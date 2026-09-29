package net.tfminecraft.dowsing.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.tfminecraft.dowsing.objects.Node;

class ItemDropperTest {
	@Test
	void unavailableRewardKeepsCyclePendingAndLastResultIntact() {
		Node node = mock(Node.class);
		when(node.getId()).thenReturn(UUID.randomUUID());
		when(node.getCompleteDrop()).thenReturn(Map.of("missing.item", 100.0));
		Map<String, Integer> previous = new HashMap<>(Map.of("old.item", 2));
		when(node.getLastResult()).thenReturn(previous);

		assertFalse(new ItemDropper(path -> null, message -> {}).dropItems(node));
		assertEquals(Map.of("old.item", 2), previous);
		verify(node, never()).update();
		verify(node, never()).setLastResult(any());
	}
}
