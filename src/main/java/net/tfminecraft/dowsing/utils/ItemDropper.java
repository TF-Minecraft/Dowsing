package net.tfminecraft.dowsing.utils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.function.Function;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Item;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import net.tfminecraft.dowsing.objects.Node;

public class ItemDropper {
	private static final Set<String> unavailableRewardsLogged = new HashSet<>();
	private final Function<String, ItemStack> itemFactory;
	private final Consumer<String> warning;

	public ItemDropper() {
		this(new ItemCreator()::getItemFromPath, message -> org.bukkit.Bukkit.getLogger().warning(message));
	}

	ItemDropper(Function<String, ItemStack> itemFactory, Consumer<String> warning) {
		this.itemFactory = itemFactory;
		this.warning = warning;
	}

	/**
	 * Delivers output into the hopper above the node when one is present, so blocks
	 * above the hopper cannot trap and despawn it. Returns false when a configured
	 * reward cannot be created or the hopper cannot hold the output, so the cycle
	 * can retry instead of losing items.
	 */
	public boolean dropItems(Node n) {
		Map<String, Integer> pending = n.getPendingResult();
		boolean retained = pending != null && !pending.isEmpty();
		Map<String, ItemStack> items = new HashMap<>();
		for(String key : retained ? pending.keySet() : n.getCompleteDrop().keySet()) {
			if(key.equalsIgnoreCase("nothing")) continue;
			ItemStack item = itemFactory.apply(key);
			if(item == null) {
				if(unavailableRewardsLogged.add(key)) {
					warning.accept("[Dowsing] Cannot complete node " + n.getId()
							+ ": reward item is unavailable: " + key);
				}
				return false;
			}
			unavailableRewardsLogged.remove(key);
			items.put(key, item);
		}
		Map<String, Integer> result = new HashMap<>();
		if(retained) {
			// A held cycle keeps its rolled output; retries must not reroll it.
			result = new LinkedHashMap<>(pending);
		} else {
			Double maxWeight = 0.0;
			for(String key : n.getCompleteDrop().keySet()) {
				maxWeight = maxWeight+n.getCompleteDrop().get(key);
			}
			int safety = 0;
			Integer dropped = 0;
			while(dropped < n.getYield() && safety < n.getYield() * 10) {
				safety++;
				Double random = Math.random();
				Double previous = 0.0;
				for(String key : n.getCompleteDrop().keySet()) {
					if(maxWeight <= 0) {
						maxWeight = 1.0;
					}
					Double chance = n.getCompleteDrop().get(key) / maxWeight;
					Double max = previous+chance;
					if(random <= max && random > previous) {
						dropped++;
						if(!key.equalsIgnoreCase("nothing")) {
							result.merge(key, 1, Integer::sum);
						}
						break;
					}
					previous = max;
				}
			}
		}
		n.update();
		Location loc = n.getLoc().clone().add(0.5, 2, 0.5);
		Block above = n.getLoc().clone().add(0, 1, 0).getBlock();
		if(above != null && above.getType() == Material.HOPPER && above.getState() instanceof Hopper hopper) {
			if(!deliverToHopper(n, hopper.getInventory(), items, result, loc)) {
				n.setPendingResult(result);
				return false;
			}
		} else {
			for(Map.Entry<String, Integer> entry : result.entrySet()) {
				for(int count = 0; count < entry.getValue(); count++) {
					Item e = loc.getWorld().dropItem(loc, items.get(entry.getKey()).clone());
					e.setVelocity(new Vector());
					loc.getWorld().playSound(loc, Sound.ENTITY_ITEM_PICKUP, 0.5f, 1f);
				}
			}
		}
		n.setPendingResult(null);
		n.setLastResult(result);
		return true;
	}
	/** One transaction: a full hopper keeps its contents and the pending output for a later retry. */
	private boolean deliverToHopper(Node n, Inventory inventory, Map<String, ItemStack> items, Map<String, Integer> result, Location loc) {
		ItemStack[] before = Arrays.stream(inventory.getContents())
				.map(item -> item == null ? null : item.clone()).toArray(ItemStack[]::new);
		for(Map.Entry<String, Integer> entry : result.entrySet()) {
			ItemStack stack = items.get(entry.getKey()).clone();
			stack.setAmount(entry.getValue());
			if(!inventory.addItem(stack).isEmpty()) {
				inventory.setContents(before);
				if(unavailableRewardsLogged.add("blocked:" + n.getId())) {
					warning.accept("[Dowsing] Cannot complete node " + n.getId() + ": output hopper is full");
				}
				return false;
			}
		}
		unavailableRewardsLogged.remove("blocked:" + n.getId());
		if(!result.isEmpty()) loc.getWorld().playSound(loc, Sound.ENTITY_ITEM_PICKUP, 0.5f, 1f);
		return true;
	}
	public void dropItem(Location loc, String path, Boolean noV) {
		ItemCreator ic = new ItemCreator();
		ItemStack item = ic.getItemFromPath(path);
		if(item == null) return;
		Item e = loc.getWorld().dropItem(loc, item);
		if(noV) e.setVelocity(new Vector());
	}
}
