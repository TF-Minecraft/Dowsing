package net.tfminecraft.dowsing.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.function.Function;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
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

	/** Returns false when a configured reward cannot be created, so the cycle can retry. */
	public boolean dropItems(Node n) {
		Map<String, ItemStack> items = new HashMap<>();
		for(String key : n.getCompleteDrop().keySet()) {
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
		n.update();
		Location loc = n.getLoc().clone().add(0.5, 2, 0.5);
		for(Map.Entry<String, Integer> entry : result.entrySet()) {
			for(int count = 0; count < entry.getValue(); count++) {
				Item e = loc.getWorld().dropItem(loc, items.get(entry.getKey()).clone());
				e.setVelocity(new Vector());
				loc.getWorld().playSound(loc, Sound.ENTITY_ITEM_PICKUP, 0.5f, 1f);
			}
		}
		n.setLastResult(result);
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
