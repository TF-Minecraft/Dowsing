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
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.dowsing.objects.Node;

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

	private Node nodeAt(Location location, Map<String, Double> drops, int yield) {
		Node node = mock(Node.class); when(node.getId()).thenReturn(UUID.randomUUID());
		when(node.getLoc()).thenReturn(location); when(node.getCompleteDrop()).thenReturn(drops); when(node.getYield()).thenReturn(yield);
		return node;
	}
}
