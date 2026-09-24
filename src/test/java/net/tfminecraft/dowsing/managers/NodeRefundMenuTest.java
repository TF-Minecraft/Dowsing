package net.tfminecraft.dowsing.managers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import net.tfminecraft.dowsing.enums.ConfirmType;
import net.tfminecraft.dowsing.objects.Node;
import net.tfminecraft.dowsing.objects.NodeBlock;
import net.tfminecraft.dowsing.objects.NodeSlot;

class NodeRefundMenuTest {
	private NodeManager manager;
	private Node node;
	private Player player;
	private InventoryClickEvent click;
	private InventoryView view;

	@BeforeEach
	void setUp() {
		manager = new NodeManager();
		node = mock(Node.class);
		player = mock(Player.class);
		click = mock(InventoryClickEvent.class);
		view = mock(InventoryView.class);
		NodeBlock block = mock(NodeBlock.class);
		when(node.getBlock()).thenReturn(block);
		when(block.getResource()).thenReturn("Iron");
		when(node.hasGuild()).thenReturn(true);
		when(node.hasPendingRefund()).thenReturn(true);
		when(node.getIsActive()).thenReturn(false);
		when(player.hasPermission("dowsing.admin")).thenReturn(true);
		when(click.getWhoClicked()).thenReturn(player);
		when(click.getView()).thenReturn(view);
		when(view.getTitle()).thenReturn("§7Iron Node");
		when(click.getClickedInventory()).thenReturn(mock(Inventory.class));
		manager.currentNode.put(player, node);
	}

	@ParameterizedTest
	@ValueSource(ints = {6, 8, 9, 18, 24, 26, 10})
	void pendingRefundBlocksMainMenuChanges(int slot) {
		when(click.getSlot()).thenReturn(slot);

		manager.invenClick(click);

		verify(click).setCancelled(true);
		verify(player).sendMessage(contains("Retry Refund"));
		verify(node, never()).setGuild(any());
		verify(node, never()).breakNode();
		verify(node, never()).activate();
	}

	@ParameterizedTest
	@EnumSource(value = ConfirmType.class, names = {"DELETE_NODE", "CHANGE_TYPE"})
	void pendingRefundBlocksStaleConfirmation(ConfirmType action) {
		manager.confirmClick(player, node, action);

		verify(player).sendMessage(contains("Retry Refund"));
		verify(node, never()).breakNode();
		verify(node, never()).setCurrentType(any());
	}

	@ParameterizedTest
	@ValueSource(strings = {"§7Iron Node: Type", "§7Iron Node: Fuel"})
	void pendingRefundBlocksAlreadyOpenSubmenus(String title) {
		NodeSlot slot = mock(NodeSlot.class);
		when(slot.getId()).thenReturn("fuel");
		manager.currentSlot.put(player, slot);
		when(view.getTitle()).thenReturn(title);

		manager.invenClick(click);

		verify(click).setCancelled(true);
		verify(player).sendMessage(contains("Retry Refund"));
		verify(slot, never()).setActivePm(any());
	}

	@Test
	void statusButtonRetriesRefundWithoutActivating() {
		when(click.getSlot()).thenReturn(17);

		manager.invenClick(click);

		verify(node).deActivate();
		verify(node, never()).activate();
		verify(player).sendMessage(contains("Refund still pending"));
	}

	@Test
	void successfulRetryLeavesNodeInactive() {
		when(click.getSlot()).thenReturn(17);
		when(node.hasPendingRefund()).thenReturn(true, false);

		manager.invenClick(click);

		verify(node).deActivate();
		verify(node, never()).activate();
		verify(player).sendMessage(contains("Refund complete"));
	}

	@Test
	void statusButtonStillActivatesWhenNoRefundIsPending() {
		when(click.getSlot()).thenReturn(17);
		when(node.hasPendingRefund()).thenReturn(false);
		// Stop at the production boundary before Bukkit's server-backed sound registry is needed.
		RuntimeException activationReached = new RuntimeException("Activation reached");
		doThrow(activationReached).when(node).activate();

		assertSame(activationReached, assertThrows(RuntimeException.class, () -> manager.invenClick(click)));

		verify(node).activate();
		verify(node, never()).deActivate();
		verify(player, never()).sendMessage(anyString());
	}
}
