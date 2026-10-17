package net.tfminecraft.dowsing.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Item;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.dowsing.objects.Node;
import net.tfminecraft.dowsing.objects.NodeBlock;

class ItemDropperTest {
	Set<String> logged;
	Set<String> previousLogged;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void isolateWarningSuppression() throws Exception {
		Field field = ItemDropper.class.getDeclaredField("unavailableRewardsLogged");
		field.setAccessible(true);
		logged = (Set<String>) field.get(null);
		previousLogged = new HashSet<>(logged);
		logged.clear();
	}

	@AfterEach
	void restoreWarningSuppression() {
		logged.clear();
		logged.addAll(previousLogged);
	}

	@Test
	void unavailableRewardKeepsCyclePendingAndLastResultIntact() {
		Node node = mock(Node.class);
		when(node.getId()).thenReturn(UUID.randomUUID());
		Map<String, Double> rewards = new LinkedHashMap<>();
		rewards.put("first.item", 100.0);
		rewards.put("missing.item", 100.0);
		when(node.getCompleteDrop()).thenReturn(rewards);
		Map<String, Integer> previous = new HashMap<>(Map.of("old.item", 2));
		when(node.getLastResult()).thenReturn(previous);

		ItemStack first = mock(ItemStack.class);
		assertFalse(new ItemDropper(path -> path.equals("first.item") ? first : null,
				message -> {}).dropItems(node));
		assertEquals(Map.of("old.item", 2), previous);
		verify(node, never()).getLoc();
		verify(node, never()).update();
		verify(node, never()).setLastResult(any());
	}

	@Test
	void successfulCyclesDropIndividualCopiesAtTheNodeAndRecordTheResult() {
		World world = mock(World.class);
		Location origin = new Location(world, 4, 60, 7);
		Node node = nodeAt(origin, Map.of("v.STONE", 1.0), 3);
		ItemStack template = mock(ItemStack.class);
		ItemStack first = mock(ItemStack.class), second = mock(ItemStack.class), third = mock(ItemStack.class);
		when(template.clone()).thenReturn(first, second, third);
		Item entity = mock(Item.class);
		when(world.dropItem(any(Location.class), any(ItemStack.class))).thenReturn(entity);
		assertTrue(new ItemDropper(path -> template, message -> fail(message)).dropItems(node));
		Location drop = new Location(world, 4.5, 62, 7.5);
		verify(world).dropItem(drop, first); verify(world).dropItem(drop, second); verify(world).dropItem(drop, third);
		verify(entity, times(3)).setVelocity(new Vector());
		verify(world, times(3)).playSound(drop, Sound.ENTITY_ITEM_PICKUP, 0.5f, 1f);
		verify(node).setLastResult(Map.of("v.STONE", 3)); verify(node).update();
		assertEquals(new Location(world, 4, 60, 7), origin);
	}

	@Test
	void nothingRewardsAndZeroWeightTablesFinishWithoutSpawningItems() {
		World world = mock(World.class);
		Location location = new Location(world, 0, 60, 0);
		Node nothing = nodeAt(location, Map.of("NoThInG", 1.0), 3);
		assertTrue(new ItemDropper(path -> { fail("Nothing must not be resolved"); return null; }, message -> fail(message)).dropItems(nothing));
		verify(nothing).setLastResult(Map.of());
		Map<String, Double> zeroWeights = new LinkedHashMap<>();
		zeroWeights.put("nothing", 0.0); zeroWeights.put("v.STONE", 0.0);
		Node zero = nodeAt(location, zeroWeights, 3);
		assertTimeout(Duration.ofSeconds(1), () -> assertTrue(new ItemDropper(path -> mock(ItemStack.class), message -> fail(message)).dropItems(zero)));
		verify(zero).setLastResult(Map.of()); verify(zero).update();
		Node empty = nodeAt(location, Map.of(), 0);
		assertTrue(new ItemDropper(path -> null, message -> fail(message)).dropItems(empty));
		verify(empty).setLastResult(Map.of());
		verify(world, never()).dropItem(any(Location.class), any(ItemStack.class));
	}

	@Test
	void unavailableWarningsAreSuppressedUntilTheRewardRecovers() {
		Node node = nodeAt(new Location(mock(World.class), 0, 0, 0), Map.of("missing.item", 1.0), 0);
		List<String> warnings = new ArrayList<>();
		Map<String, ItemStack> available = new HashMap<>();
		ItemDropper dropper = new ItemDropper(available::get, warnings::add);
		assertFalse(dropper.dropItems(node)); assertFalse(dropper.dropItems(node));
		assertEquals(1, warnings.size()); assertTrue(warnings.getFirst().contains("missing.item"));
		available.put("missing.item", mock(ItemStack.class)); assertTrue(dropper.dropItems(node));
		available.clear(); assertFalse(dropper.dropItems(node)); assertEquals(2, warnings.size());
		verify(node, times(1)).setLastResult(Map.of());
	}

	@Test
	void defaultDropperResolvesConfiguredItemsAndLogsMissingRewards() {
		Logger logger = mock(Logger.class);
		Node node = nodeAt(new Location(mock(World.class), 0, 0, 0), Map.of("missing.item", 1.0), 0);
		try (var bukkit = mockStatic(Bukkit.class); var creators = mockConstruction(ItemCreator.class)) {
			bukkit.when(Bukkit::getLogger).thenReturn(logger);
			assertFalse(new ItemDropper().dropItems(node));
			verify(creators.constructed().getFirst()).getItemFromPath("missing.item");
			verify(logger).warning(contains("reward item is unavailable: missing.item"));
		}
	}

	@Test
	void directDropsSkipMissingItemsAndOptionallyClearVelocity() {
		World world = mock(World.class); Location location = new Location(world, 1, 2, 3);
		ItemStack stack = mock(ItemStack.class); Item entity = mock(Item.class);
		when(world.dropItem(location, stack)).thenReturn(entity);
		try (var creators = mockConstruction(ItemCreator.class, (creator, context) -> when(creator.getItemFromPath("present")).thenReturn(stack))) {
			ItemDropper dropper = new ItemDropper(path -> null, message -> fail(message));
			dropper.dropItem(location, "missing", true); verifyNoInteractions(world);
			dropper.dropItem(location, "present", false); verify(entity, never()).setVelocity(any());
			dropper.dropItem(location, "present", true); verify(entity).setVelocity(new Vector());
			verify(world, times(2)).dropItem(location, stack);
		}
	}

	@Test
	void outputGoesToTheHopperAboveTheNodeInsteadOfTheGround() {
		World world = mock(World.class);
		Location origin = new Location(world, 4, 60, 7);
		Node node = nodeAt(origin, Map.of("v.STONE", 1.0), 3);
		Inventory inventory = hopperAbove(world);
		when(inventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());
		ItemStack template = mock(ItemStack.class);
		ItemStack delivered = mock(ItemStack.class);
		when(template.clone()).thenReturn(delivered);
		assertTrue(new ItemDropper(path -> template, message -> fail(message)).dropItems(node));
		verify(delivered).setAmount(3);
		verify(inventory).addItem(delivered);
		verify(world, never()).dropItem(any(Location.class), any(ItemStack.class));
		verify(world).playSound(new Location(world, 4.5, 62, 7.5), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1f);
		verify(node).setLastResult(Map.of("v.STONE", 3)); verify(node).update();
		verify(node, never()).notifyGuild(anyString());
		assertEquals(new Location(world, 4, 60, 7), origin);
	}

	@Test
	void fullHopperSpillsTheOverflowOnTopAndWarnsTheGuild() {
		World world = mock(World.class);
		Node node = nodeAt(new Location(world, 4, 60, 7), Map.of("v.STONE", 1.0), 3);
		Inventory inventory = hopperAbove(world);
		ItemStack template = mock(ItemStack.class);
		ItemStack delivered = mock(ItemStack.class), spilled = mock(ItemStack.class);
		when(template.clone()).thenReturn(delivered, spilled);
		ItemStack leftover = mock(ItemStack.class);
		when(leftover.getAmount()).thenReturn(2);
		when(inventory.addItem(delivered)).thenReturn(new HashMap<>(Map.of(0, leftover)));
		Item entity = mock(Item.class);
		when(world.dropItem(any(Location.class), any(ItemStack.class))).thenReturn(entity);
		List<String> warnings = new ArrayList<>();
		assertTrue(new ItemDropper(path -> template, warnings::add).dropItems(node));
		Location top = new Location(world, 4.5, 62, 7.5);
		verify(delivered).setAmount(3);
		verify(world, times(2)).dropItem(top, spilled);
		verify(entity, times(2)).setVelocity(new Vector());
		// One sound for the item that reached the hopper, one per spilled item.
		verify(world, times(3)).playSound(top, Sound.ENTITY_ITEM_PICKUP, 0.5f, 1f);
		verify(node).setPendingResult(null); verify(node).setLastResult(Map.of("v.STONE", 3));
		assertEquals(List.of("[Dowsing] Node " + node.getId() + " spilled 2 items past its full hopper at 4, 61, 7"), warnings);
		verify(node).notifyGuild("§cThe hopper on your Iron node at 4, 61, 7 is full. 2 items spilled on top of it and may be lost.");
		verify(node).notifyGuild("§7Have the hopper feed into a chest or barrel so the output has room.");
	}

	@Test
	void heldOutputWiderThanTheHopperIsDeliveredAsRolledInsteadOfBlocking() {
		World world = mock(World.class);
		Node node = nodeAt(new Location(world, 4, 60, 7), Map.of("v.STONE", 1.0), 3);
		// Six item kinds can never fit a five-slot hopper at once.
		Map<String, Integer> held = new LinkedHashMap<>();
		for(String kind : List.of("v.A", "v.B", "v.C", "v.D", "v.E", "v.F")) held.put(kind, 2);
		when(node.getPendingResult()).thenReturn(held);
		// A reroll at the new yield would deliver 99 instead of the held output.
		when(node.getYield()).thenReturn(99);
		Inventory inventory = hopperAbove(world);
		ItemStack template = mock(ItemStack.class);
		ItemStack copy = mock(ItemStack.class);
		when(template.clone()).thenReturn(copy);
		ItemStack leftover = mock(ItemStack.class);
		when(leftover.getAmount()).thenReturn(2);
		when(inventory.addItem(copy)).thenReturn(new HashMap<>(), new HashMap<>(), new HashMap<>(),
				new HashMap<>(), new HashMap<>(), new HashMap<>(Map.of(0, leftover)));
		when(world.dropItem(any(Location.class), any(ItemStack.class))).thenReturn(mock(Item.class));
		assertTrue(new ItemDropper(path -> template, message -> {}).dropItems(node));
		verify(copy, times(6)).setAmount(2); verify(copy, never()).setAmount(99);
		verify(world, times(2)).dropItem(new Location(world, 4.5, 62, 7.5), copy);
		verify(node).setPendingResult(null); verify(node).setLastResult(held);
		verify(node).notifyGuild(contains("2 items spilled"));
	}

	@Test
	void outputThatAllSpillsMakesNoHopperSound() {
		World world = mock(World.class);
		Node node = nodeAt(new Location(world, 4, 60, 7), Map.of("v.STONE", 1.0), 1);
		Inventory inventory = hopperAbove(world);
		ItemStack template = mock(ItemStack.class);
		ItemStack copy = mock(ItemStack.class);
		when(template.clone()).thenReturn(copy);
		ItemStack leftover = mock(ItemStack.class);
		when(leftover.getAmount()).thenReturn(1);
		when(inventory.addItem(copy)).thenReturn(new HashMap<>(Map.of(0, leftover)));
		when(world.dropItem(any(Location.class), any(ItemStack.class))).thenReturn(mock(Item.class));
		assertTrue(new ItemDropper(path -> template, message -> {}).dropItems(node));
		// Only the spilled item's own drop sound plays.
		verify(world, times(1)).playSound(any(Location.class), any(Sound.class), anyFloat(), anyFloat());
		verify(node).notifyGuild(contains("1 item spilled"));
	}

	@Test
	void nothingOnlyOutputCompletesWithoutTouchingTheHopper() {
		World world = mock(World.class);
		Node node = nodeAt(new Location(world, 4, 60, 7), Map.of("nothing", 1.0), 3);
		Inventory inventory = hopperAbove(world);
		assertTrue(new ItemDropper(path -> { fail("Nothing must not be resolved"); return null; }, message -> fail(message)).dropItems(node));
		verify(inventory, never()).addItem(any(ItemStack.class));
		verify(world, never()).playSound(any(Location.class), any(Sound.class), anyFloat(), anyFloat());
		verify(node).setPendingResult(null); verify(node).setLastResult(Map.of());
	}

	@Test
	void pendingAmountsAboveOneStackAreDeliveredAsOneTransaction() {
		World world = mock(World.class);
		Node node = nodeAt(new Location(world, 4, 60, 7), Map.of("v.STONE", 1.0), 3);
		when(node.getPendingResult()).thenReturn(Map.of("v.STONE", 130));
		Inventory inventory = hopperAbove(world);
		when(inventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());
		ItemStack template = mock(ItemStack.class);
		ItemStack delivered = mock(ItemStack.class);
		when(template.clone()).thenReturn(delivered);
		assertTrue(new ItemDropper(path -> template, message -> fail(message)).dropItems(node));
		verify(delivered).setAmount(130); verify(inventory).addItem(delivered);
		verify(node).setPendingResult(null); verify(node).setLastResult(Map.of("v.STONE", 130));
	}

	private Inventory hopperAbove(World world) {
		Block block = mock(Block.class);
		Hopper hopper = mock(Hopper.class);
		Inventory inventory = mock(Inventory.class);
		when(world.getBlockAt(any(Location.class))).thenReturn(block);
		when(block.getType()).thenReturn(Material.HOPPER);
		when(block.getState()).thenReturn(hopper);
		when(block.getX()).thenReturn(4); when(block.getY()).thenReturn(61); when(block.getZ()).thenReturn(7);
		when(hopper.getInventory()).thenReturn(inventory);
		return inventory;
	}

	private Node nodeAt(Location location, Map<String, Double> drops, int yield) {
		Node node = mock(Node.class); when(node.getId()).thenReturn(UUID.randomUUID());
		NodeBlock block = mock(NodeBlock.class); when(block.getResource()).thenReturn("Iron");
		when(node.getBlock()).thenReturn(block);
		when(node.getLoc()).thenReturn(location); when(node.getCompleteDrop()).thenReturn(drops); when(node.getYield()).thenReturn(yield);
		return node;
	}
}
