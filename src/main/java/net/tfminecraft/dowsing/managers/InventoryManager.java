package net.tfminecraft.dowsing.managers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.WordUtils;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import dev.lone.itemsadder.api.CustomStack;
import net.tfminecraft.dowsing.Cache;
import net.tfminecraft.dowsing.DowsingMain;
import net.tfminecraft.dowsing.objects.Node;
import net.tfminecraft.dowsing.objects.NodeSlot;
import net.tfminecraft.dowsing.objects.NodeType;
import net.tfminecraft.dowsing.objects.ProductionMethod;
import net.tfminecraft.dowsing.utils.Database;
import net.tfminecraft.dowsing.utils.ItemCreator;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.utils.Permissions;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

@SuppressWarnings("deprecation") // Existing ItemsAdder menus use Bukkit's legacy string metadata contract.
public class InventoryManager {
	ItemCreator ic = new ItemCreator();
	public void nodeView(Player player, Node n) {
		Inventory i = DowsingMain.plugin.getServer().createInventory(null, 27, "§7"+n.getBlock().getResource()+" Node");
		i.setItem(9, createMainItem(n, n.getCurrentType(), false));
		for(NodeSlot ns : n.getCurrentType().getSlots()) {
			ns.getActivePm().setMenuItem(ic.updateMenuItem(n, ns.getActivePm()));
			i.setItem(ns.getSlot(), ns.getActivePm().getMenuItem());
		}
		if(n.getNaturalYield() > 0) {
			i.setItem(0, ic.createNaturalYieldItem(n));
		}
		if(n.getBlock().isTransferable()) i.setItem(6, createTransferItem());
		i.setItem(8, createUpgrade(n));
		i.setItem(26, createDowngrade(n));
		if(NodeManager.canPurchaseCapacity(n.getGuild())) {
			i.setItem(24, createCapacityButton(n));
		}
		i.setItem(15, createGlobe(n));
		i.setItem(16, createCycle(n));
		i.setItem(17, createStatus(n));
		if(n.getBlock().isBreakable() || Permissions.isAdmin(player)) i.setItem(18, createDeleteButton());
		Integer slot = 0;
		while(slot < i.getSize()) {
			if(i.getItem(slot) == null) {
				ItemStack fill = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
				ItemMeta fm = fill.getItemMeta();
				fm.setDisplayName("§8 ");
				fill.setItemMeta(fm);
				i.setItem(slot, fill);
			}
			slot++;
		}
		player.openInventory(i);
	}
	public void slotView(Player player, Node n, NodeSlot slot) {
		Inventory i = DowsingMain.plugin.getServer().createInventory(null, 27, "§7"+n.getBlock().getResource()+" Node: "+WordUtils.capitalize(slot.getId().replace("_", " ")));
		for(int y = 0; y<slot.getPms().size(); y++) {
			ProductionMethod pm = slot.getPms().get(y);
			pm.setMenuItem(ic.updateMenuItem(n, pm));
			ItemStack item = pm.getMenuItem();
			if(pm.getId().equalsIgnoreCase(slot.getActivePm().getId())) {
				item = new ItemStack(item);
				ItemMeta m = item.getItemMeta();
				m.addEnchant(Enchantment.UNBREAKING, 1, false);
				m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
				List<String> lore = m.getLore();
				lore.add(" ");
				lore.add("§aCURRENT");
				m.setLore(lore);
				item.setItemMeta(m);
			}
			i.setItem(y, item);
		}
		i.setItem(26, createBackButton());
		Integer slotn = 0;
		while(slotn < i.getSize()) {
			if(i.getItem(slotn) == null) {
				ItemStack fill = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
				ItemMeta fm = fill.getItemMeta();
				fm.setDisplayName("§8 ");
				fill.setItemMeta(fm);
				i.setItem(slotn, fill);
			}
			slotn++;
		}
		player.openInventory(i);
	}
	public void typeView(Player player, Node n) {
		Inventory i = DowsingMain.plugin.getServer().createInventory(null, 27, "§7"+n.getBlock().getResource()+" Node: Type");
		for(int y = 0; y<n.getBlock().getTypes().size(); y++) {
			NodeType t = n.getBlock().getTypes().get(y);
			ItemStack item = createMainItem(n, t, true);
			if(Cache.naturalYieldEnabled) {
				Database db = new Database();
				try {
					if(db.hasResource(n.getLoc().getChunk())) {
						String resource = db.getResource(n.getLoc().getChunk()).split("\\.")[0];
						if(t.getResource().equalsIgnoreCase(resource)) {
							item = new ItemStack(item);
							ItemMeta m = item.getItemMeta();
							List<String> lore = m.getLore();
							lore.add(" ");
							lore.add("§2Natural Yield Detected!");
							m.setLore(lore);
							item.setItemMeta(m);
						}
					}
				} catch (IllegalArgumentException | IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			if(t.getId().equalsIgnoreCase(n.getCurrentType().getId())) {
				item = new ItemStack(item);
				ItemMeta m = item.getItemMeta();
				m.addEnchant(Enchantment.UNBREAKING, 1, false);
				m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
				List<String> lore = m.getLore();
				lore.add(" ");
				lore.add("§aCURRENT");
				m.setLore(lore);
				item.setItemMeta(m);
			}
			if(t.getBiomes().size() > 0) {
				item = new ItemStack(item);
				ItemMeta m = item.getItemMeta();
				List<String> lore = m.getLore();
				lore.add(" ");
				lore.add("§eOnly useable in:");
				for(String biome : t.getBiomes()) {
					lore.add("§7- "+WordUtils.capitalize(biome.toLowerCase().replace("_", " ")));
				}
				m.setLore(lore);
				item.setItemMeta(m);
			}
			item = new ItemStack(item);
			ItemMeta m = item.getItemMeta();
			List<String> lore = m.getLore();
			lore.add(" ");
			//lore.add("§4Warning! §cChanging type will reset the node to level 1!");
			m.setLore(lore);
			item.setItemMeta(m);
			i.setItem(y, item);
		}
		i.setItem(26, createBackButton());
		Integer slotn = 0;
		while(slotn < i.getSize()) {
			if(i.getItem(slotn) == null) {
				ItemStack fill = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
				ItemMeta fm = fill.getItemMeta();
				fm.setDisplayName("§8 ");
				fill.setItemMeta(fm);
				i.setItem(slotn, fill);
			}
			slotn++;
		}
		player.openInventory(i);
	}
	public void confirmView(Player player) {
		Inventory i = DowsingMain.plugin.getServer().createInventory(null, 27, "§7Confirm Action");
		i.setItem(11, createItemStack(Material.GREEN_CONCRETE, "§aConfirm"));
		i.setItem(15, createItemStack(Material.RED_CONCRETE, "§cCancel"));
		Integer slot = 0;
		while(slot < i.getSize()) {
			if(i.getItem(slot) == null) {
				ItemStack fill = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
				ItemMeta fm = fill.getItemMeta();
				fm.setDisplayName("§8 ");
				fill.setItemMeta(fm);
				i.setItem(slot, fill);
			}
			slot++;
		}
		player.openInventory(i);
	}
	public void updateNodeView(Player p, Node n, Inventory i) {
		if(i.getSize() < 27) return;
		i.setItem(9, createMainItem(n, n.getCurrentType(), false));
		for(NodeSlot ns : n.getCurrentType().getSlots()) {
			ns.getActivePm().setMenuItem(ic.updateMenuItem(n, ns.getActivePm()));
			i.setItem(ns.getSlot(), ns.getActivePm().getMenuItem());
		}
		if(n.getNaturalYield() > 0) {
			i.setItem(0, ic.createNaturalYieldItem(n));
		} else {
			i.setItem(0, new ItemStack(Material.AIR, 1));
		}
		i.setItem(8, createUpgrade(n));
		i.setItem(26, createDowngrade(n));
		i.setItem(15, createGlobe(n));
		if(NodeManager.canPurchaseCapacity(n.getGuild())) {
			i.setItem(24, createCapacityButton(n));
		}
		i.setItem(16, createCycle(n));
		i.setItem(17, createStatus(n));
		i.setItem(18, createDeleteButton());
		Integer slot = 0;
		while(slot < i.getSize()) {
			if(i.getItem(slot) == null) {
				ItemStack fill = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
				ItemMeta fm = fill.getItemMeta();
				fm.setDisplayName("§8 ");
				fill.setItemMeta(fm);
				i.setItem(slot, fill);
			}
			slot++;
		}
	}
	public ItemStack createItemStack(Material m, String name) {
		ItemStack i = new ItemStack(m, 1);
		ItemMeta meta = i.getItemMeta();
		meta.setDisplayName(name);
		i.setItemMeta(meta);
		return i;
	}
	public ItemStack createMainItem(Node n, NodeType t, boolean gui) {
		ItemStack i = ic.createTypeItemNode(n, t, gui);
		return i;
	}
	ItemStack createTransferItem() {
		ItemStack i = new ItemStack(Material.PAPER, 1);
		ItemMeta m = i.getItemMeta();
		m.setDisplayName(StringFormatter.formatHex("#4da866Transfer Node"));
		List<String> lore = new ArrayList<String>();
		lore.add("§7Click to mark the node as §eClaimable");
		lore.add("§7Any guild leader that interacts with");
		lore.add("§7this node will claim it.");
		m.setLore(lore);
		i.setItemMeta(m);
		return i;
	}

	ItemStack createGlobe(Node n) {
		Guild g = n.getGuild();
		ItemStack i = getItemsAdderItem("mcicons:icon_web");
		ItemMeta m = i.getItemMeta();
		m.setDisplayName("§eBelongs to: "+g.getName());
		List<String> lore = new ArrayList<String>();
		lore.add("§7Leader: §f"+g.getLeader());
		if(NodeManager.getNodeAmount(g) < n.getCapacity()) {
			lore.add("§eNodes: §a"+NodeManager.getNodeAmount(g)+"/"+n.getCapacity());
		} else if(NodeManager.getNodeAmount(g) == n.getCapacity()){
			lore.add("§eNodes: §e"+NodeManager.getNodeAmount(g)+"/"+n.getCapacity());
		} else {
			lore.add("§eNodes: §c"+NodeManager.getNodeAmount(g)+"/"+n.getCapacity());
		}
		lore.add("§eCost Multiplier: §a"+n.getMultiplier());
		if(n.getCostIncrease() > 1.0) {
			lore.add("§eUpgrade/Upkeep Cost Multiplier: §c"+((n.getCostIncrease()-1.0)*100)+"%");
		}
		m.setLore(lore);
		i.setItemMeta(m);
		return i;
	}
	ItemStack createCapacityButton(Node n) {
		ItemStack i = createItemStack(Material.NETHER_STAR, "§aPurchase Extra Capacity");
		ItemMeta m = i.getItemMeta();
		List<String> lore = new ArrayList<String>();
		lore.add("§a+1 Node Capacity");
		lore.add(" ");
		lore.add("§7Cost: §6"+n.getNodeCapacityCost()+"d");
		m.setLore(lore);
		i.setItemMeta(m);
		return i;
	}
	ItemStack createBackButton() {
		ItemStack i = getItemsAdderItem("mcicons:icon_cancel");
		ItemMeta m = i.getItemMeta();
		m.setDisplayName("§fBack");
		i.setItemMeta(m);
		return i;
	}
	ItemStack createDeleteButton() {
		ItemStack i = getItemsAdderItem("mcicons:icon_cancel");
		ItemMeta m = i.getItemMeta();
		m.setDisplayName("§cDelete Node");
		List<String> lore = new ArrayList<String>();
		lore.add("§7Cannot be undone");
		lore.add(" ");
		lore.add("§cOnly the node block is refunded");
		lore.add("§cAll other items/upgrades are lost!");
		m.setLore(lore);
		i.setItemMeta(m);
		return i;
	}
	ItemStack createCycle(Node n) {
		ItemStack i = getItemsAdderItem("mcicons:icon_refresh");
		ItemMeta m = i.getItemMeta();
		m.setDisplayName("§9Last Cycle Result: ");
		List<String> lore = new ArrayList<String>();
		lore.add("§7Extracted:");
		if(n.getLastResult().size() == 0) {
			lore.add("§fNothing");
		} else {
			for(String s : n.getLastResult().keySet()) {
				s = s+"("+n.getLastResult().get(s)+")";
				lore.add(ic.getFormattedCost(s, 1));
			}
		}
		m.setLore(lore);
		i.setItemMeta(m);
		return i;
	}
	private String getEfficiencyString(double efficiency) {
		String color;

		if (efficiency < 20) {
			color = "§4"; // dark red
		} else if (efficiency < 40) {
			color = "§c"; // red
		} else if (efficiency < 60) {
			color = "§e"; // yellow
		} else if (efficiency < 80) {
			color = "§a"; // light green
		} else {
			color = "§2"; // dark green
		}

		return String.format("§7Efficiency: %s%.2f%%", color, efficiency);
	}
	ItemStack createStatus(Node n) {
		ItemStack i = new ItemStack(Material.LIME_STAINED_GLASS_PANE, 1);
		if(!n.getIsActive()) {
			i.setType(Material.RED_STAINED_GLASS_PANE);
			ItemMeta m = i.getItemMeta();
			m.setDisplayName("§cINACTIVE");
			if(n.hasPendingRefund()) {
				m.setDisplayName("§eRetry Refund");
				List<String> lore = new ArrayList<>(n.getErrors());
				lore.add("§7Restore the barrel and hopper, then click to retry.");
				lore.add("§7Refunds must finish before changing or activating this node.");
				m.setLore(lore);
			} else if(n.getErrors().size() > 0) {
				m.setLore(n.getErrors());
			}
			i.setItemMeta(m);
		} else {
			ItemMeta m = i.getItemMeta();
			m.setDisplayName("§aACTIVE");
			List<String> lore = new ArrayList<String>();
			lore.add("§7Time until next output: §f"+ic.formatTime(n.getTimeLeft()));
			lore.add("§7Time until next input: §f"+ic.formatTime(Cache.cycleLength-n.getCycleTime()));
			lore.add("");
			lore.add(getEfficiencyString(n.getEfficiency()));
			lore.add(" ");
			lore.add("§eClick to Deactivate");
			lore.add(" ");
			lore.add("§4WARNING!");
			lore.add("§cDeactivating resets the current cycle!");
			lore.add("§cThe cost of the current cycle will be refunded.");
			m.setLore(lore);
			i.setItemMeta(m);
		}
		return i;
	}
	ItemStack createUpgrade(Node n) {
		ItemStack i = getItemsAdderItem("mcicons:icon_up_gray");
		ItemMeta m = i.getItemMeta();
		m.setDisplayName("§eCurrent Level: §a"+n.getLevel());
		List<String> lore = new ArrayList<String>();
		if(n.getLevel() < n.getCurrentType().getMaxLevel()) {
			Double cost = n.getCurrentType().getLevels().get(n.getLevel()).getCost();
			cost = cost*n.getCostIncrease();
			lore.add("§7Upgrade to level "+(n.getLevel()+1)+": §f"+cost+"d");
		} else {
			lore.add("§7Max level");
		}
		for(String s : ic.getUpgradeDownGradeFormatted(n, "upgrade")) {
			lore.add(s);
		}
		if(n.getLevel() < n.getCurrentType().getMaxLevel()) {
			lore.add("§bClick to Upgrade!");
		}
		m.setLore(lore);
		i.setItemMeta(m);
		return i;
	}
	ItemStack createDowngrade(Node n) {
		ItemStack i = getItemsAdderItem("mcicons:icon_down_gray");
		ItemMeta m = i.getItemMeta();
		m.setDisplayName("§eCurrent Level: §a"+n.getLevel());
		List<String> lore = new ArrayList<String>();
		if(n.getLevel() > 1) {
			lore.add("§7Downgrade to level "+(n.getLevel()-1));
			lore.add("§7Refunds: §f"+(n.getCurrentType().getLevels().get(n.getLevel()-1).getCost()*Cache.refundPercentage)+"d §7("+Cache.refundPercentage*100+"%)");
		} else {
			lore.add("§7Lowest level");
		}
		for(String s : ic.getUpgradeDownGradeFormatted(n, "downgrade")) {
			lore.add(s);
		}
		if(n.getLevel() > 1) {
			lore.add("§bClick to Downgrade!");
		}
		m.setLore(lore);
		i.setItemMeta(m);
		return i;
	}
	public ItemStack getItemsAdderItem(String path) {
		CustomStack stack = CustomStack.getInstance(path);
		if(stack != null) {
			ItemStack i = stack.getItemStack();
			return i;
		}
		return null;
	}
}
