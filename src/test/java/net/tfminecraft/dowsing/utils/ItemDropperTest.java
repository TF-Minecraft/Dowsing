package net.tfminecraft.dowsing.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.dowsing.objects.Node;

class ItemDropperTest {
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
}
