package net.tfminecraft.dowsing.utils;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import net.tfminecraft.dowsing.objects.Node;

public class ItemDropper {
	public void dropItems(Node n) {
		n.getLastResult().clear();
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
						Integer a = 1;
						if(n.getLastResult().containsKey(key)) {
							a = 1+n.getLastResult().get(key);
						}
						n.getLastResult().put(key, a);
						Location loc = new Location(n.getLoc().getWorld(), n.getLoc().getX(), n.getLoc().getY(), n.getLoc().getZ());
						loc.add(0.5,2,0.5);
						dropItem(loc, key, true);
						loc.getWorld().playSound(loc, Sound.ENTITY_ITEM_PICKUP, 0.5f, 1f);
					}
					break;
				}
				previous = max;
			}
		}
		n.update();
	}
	public void dropItem(Location loc, String path, Boolean noV) {
		ItemCreator ic = new ItemCreator();
		ItemStack item = ic.getItemFromPath(path);
		if(item == null) return;
		Item e = loc.getWorld().dropItem(loc, item);
		if(noV) e.setVelocity(new Vector());
	}
}
