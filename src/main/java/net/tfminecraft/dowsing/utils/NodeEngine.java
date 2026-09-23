package net.tfminecraft.dowsing.utils;

import java.util.HashMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.dowsing.objects.Node;
import net.tfminecraft.dowsing.objects.NodeSlot;
import net.tfminecraft.dowsing.objects.ProductionMethod;
import net.tfminecraft.tlibs.TLibs;

public class NodeEngine {
	public Boolean hasBarrel(Node n) {
		Location l = new Location(n.getLoc().getWorld(), n.getLoc().getX(), n.getLoc().getY(), n.getLoc().getZ());
		l.add(0,-1,0);
		Block b = l.getBlock();
		if(b.getType().equals(Material.BARREL)) return true;
		return false;
	}
	public Boolean hasHopper(Node n) {
		Location l = new Location(n.getLoc().getWorld(), n.getLoc().getX(), n.getLoc().getY(), n.getLoc().getZ());
		l.add(0,1,0);
		Block b = l.getBlock();
		if(b.getType().equals(Material.HOPPER)) return true;
		return false;
	}
	public Boolean hasInputs(Node n) {
		Location l = new Location(n.getLoc().getWorld(), n.getLoc().getX(), n.getLoc().getY(), n.getLoc().getZ());
		l.add(0,-1,0);
		Block block = l.getBlock();
		Barrel b = (Barrel) block.getState();
		Inventory i = b.getInventory();
		HashMap<String, Integer> inputs = new HashMap<>();
		for(NodeSlot slot : n.getCurrentType().getSlots()) {
			for(String s : slot.getActivePm().getInputs()) {
				String[] cost = DropPaths.parseStored(s);
				if(cost == null) {
					continue;
				}
				String key = cost[0];
				Integer amount = Integer.parseInt(cost[1]);
				amount = amount*n.getMultiplier();
				if(inputs.containsKey(key)) {
					amount = amount +inputs.get(key);
				}
				inputs.put(key, amount);
			}
		}
		for(String key : inputs.keySet()) {
			if(!check(n, key, inputs.get(key), i)) return false;
		}
		return true;
	}
	Boolean check(Node n, String path, Integer amount, Inventory i) {
		for(ItemStack item : i.getContents()) {
			if(item != null && compareItem(path, item)) {
				if(item.getAmount() < amount) {
					amount = amount-item.getAmount();
				} else {
					return true;
				}
			}
		}
		return false;
	}
	Boolean compareItem(String path, ItemStack item) {
		return TLibs.getItemAPI().getChecker().checkItemWithPath(item, path);
	}
	public void refund(Node n) {
		Location l = new Location(n.getLoc().getWorld(), n.getLoc().getX(), n.getLoc().getY(), n.getLoc().getZ());
		l.add(0,-1,0);
		Block block = l.getBlock();
		Barrel b = (Barrel) block.getState();
		Inventory i = b.getInventory();
		HashMap<String, Integer> inputs = new HashMap<>();
		for(NodeSlot slot : n.getCurrentType().getSlots()) {
			for(String s : slot.getActivePm().getInputs()) {
				String[] cost = DropPaths.parseStored(s);
				if(cost == null) {
					continue;
				}
				String key = cost[0];
				Integer amount = Integer.parseInt(cost[1]);
				amount = amount*n.getMultiplier();
				if(inputs.containsKey(key)) {
					amount = amount +inputs.get(key);
				}
				inputs.put(key, amount);
			}
		}
		if(n.getGuild() != null && n.getGuild().getBank() != null) {
			n.getGuild().getBank().deposit(n.getUpkeep());
		}
		n.setInputCounter(n.getInputCounter()-1);
		for(String key : inputs.keySet()) {
			addItem(key, inputs.get(key), i);
		}
	}
	public void addItem(String path, Integer amount, Inventory i) {
		ItemCreator ic = new ItemCreator();
		ItemStack item = ic.getItemFromPath(path);
		if (item == null) {
			return;
		}
		item.setAmount(amount);
		i.addItem(item);
	}
	public void takeInputs(Node n) {
		Location l = new Location(n.getLoc().getWorld(), n.getLoc().getX(), n.getLoc().getY(), n.getLoc().getZ());
		l.add(0,-1,0);
		Block block = l.getBlock();
		Barrel b = (Barrel) block.getState();
		Inventory i = b.getInventory();
		HashMap<String, Integer> inputs = new HashMap<>();
		for(NodeSlot slot : n.getCurrentType().getSlots()) {
			for(String s : slot.getActivePm().getInputs()) {
				String[] cost = DropPaths.parseStored(s);
				if(cost == null) {
					continue;
				}
				String key = cost[0];
				Integer amount = Integer.parseInt(cost[1]);
				amount = amount*n.getMultiplier();
				if(inputs.containsKey(key)) {
					amount = amount +inputs.get(key);
				}
				inputs.put(key, amount);
			}
		}
		n.setInputCounter(n.getInputCounter()+1);
		for(String key : inputs.keySet()) {
			take(n, key, inputs.get(key), i);
		}
	}
	public void take(Node n, String path, Integer amount, Inventory i) {
		for(ItemStack item : i.getContents()) {
			if(item != null && compareItem(path, item)) {
				if(item.getAmount() < amount) {
					amount = amount-item.getAmount();
					item.setAmount(0);
				} else {
					item.setAmount(item.getAmount()-amount);
					return;
				}
			}
		}
	}
	public boolean checkPrerequisite(ProductionMethod pm, Node n) {
		if(pm.getPrerequisite().equalsIgnoreCase("none")) return true;
		for(NodeSlot slot : n.getCurrentType().getSlots()) {
			for(ProductionMethod slotpm : slot.getPms()) {
				if(slotpm.getId().equalsIgnoreCase(pm.getPrerequisite())) {
					int needed = slotpm.getWeight();
					int current = slot.getActivePm().getWeight();
					if(needed <= current) return true;
				}
			}
		}
		return false;
	}
}
