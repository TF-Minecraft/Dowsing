package net.tfminecraft.dowsing.managers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.WordUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import dev.lone.itemsadder.api.CustomFurniture;
import dev.lone.itemsadder.api.Events.FurnitureBreakEvent;
import dev.lone.itemsadder.api.Events.FurniturePlaceSuccessEvent;
import net.tfminecraft.dowsing.Cache;
import net.tfminecraft.dowsing.DowsingMain;
import net.tfminecraft.dowsing.loaders.BlockLoader;
import net.tfminecraft.dowsing.loaders.PMLoader;
import net.tfminecraft.dowsing.loaders.TypeLoader;
import net.tfminecraft.dowsing.objects.Level;
import net.tfminecraft.dowsing.objects.Node;
import net.tfminecraft.dowsing.objects.NodeBlock;
import net.tfminecraft.dowsing.objects.NodeSlot;
import net.tfminecraft.dowsing.objects.NodeType;
import net.tfminecraft.dowsing.objects.ProductionMethod;
import net.tfminecraft.dowsing.utils.ItemDropper;
import net.tfminecraft.dowsing.utils.NodeEngine;
import net.tfminecraft.dowsing.utils.NodeReloader;
import net.tfminecraft.dowsing.enums.ConfirmType;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.Modifier;
import net.tfminecraft.simplefactions.utils.Permissions;

@SuppressWarnings("deprecation") // Inventory titles and menu identifiers remain legacy strings for compatibility.
public class NodeManager implements Listener{
	public static List<Node> nodes = new ArrayList<Node>();
	public HashMap<Player, Node> currentNode = new HashMap<>();
	public HashMap<Player, NodeSlot> currentSlot = new HashMap<>();
	public HashMap<Player, NodeType> currentType = new HashMap<>();
	public HashMap<Player, ConfirmType> confirm = new HashMap<>();
	public HashMap<Location, NodeReloader> cached = new HashMap<>();
	public static HashMap<String, Integer> extraCapacityByGuild = new HashMap<>();

	public static void requestNodeBenefitSync() {
		syncFactionNodeBenefits();
	}

	public static void syncFactionNodeBenefits() {
		Map<Faction, Double> prestigeByFaction = new HashMap<>();
		Map<Faction, Double> wealthByFaction = new HashMap<>();
		for(Node n : nodes) {
			if(!n.hasGuild() || !Boolean.TRUE.equals(n.getIsActive())) continue;
			Guild g = n.getGuild();
			if(g == null) continue;
			Faction f = g.getFaction();
			if(f == null) continue;
			prestigeByFaction.merge(f, n.getPrestigeGain(), Double::sum);
			wealthByFaction.merge(f, n.getWealthModifier(), Double::sum);
		}
		for(Faction f : FactionManager.getCopy()) {
			double prestige = prestigeByFaction.getOrDefault(f, 0.0);
			double wealth = wealthByFaction.getOrDefault(f, 0.0);
			f.setPersistentPrestigeModifier("Nodes", prestige);
			f.updatePrestige();
			Guild mainGuild = f.getOrCreateMainGuild();
			mainGuild.addWealthModifier(new Modifier("Nodes", wealth, true));
			mainGuild.updateWealth();
		}
	}

	public static int getExtraCapacity(Guild g) {
		if(g == null) return 0;
		return extraCapacityByGuild.getOrDefault(g.getId(), 0);
	}

	public static boolean canPurchaseCapacity(Guild g) {
		if(g == null) return false;
		return getExtraCapacity(g) < net.tfminecraft.simplefactions.Cache.maxExtraNodeCapacity;
	}

	public static double getTotalUpkeep(Guild g) {
		if (g == null) return 0;
		double total = 0;
		for (Node n : nodes) {
			if (!n.hasGuild()) continue;
			if (!n.getGuild().getId().equalsIgnoreCase(g.getId())) continue;
			Double upkeep = n.getUpkeep();
			if (upkeep != null) total += upkeep;
		}
		return total;
	}

	public static Integer getNodeAmount(Guild g) {
		Integer i = 0;
		if(g == null) return i;
		for(Node n : nodes) {
			if(!n.hasGuild()) continue;
			if(n.getBlock().isSpecial()) continue;
			if(n.getGuild().getId().equalsIgnoreCase(g.getId())) i++;
		}
		return i;
	}
	public static Integer getNodeCapacity(Guild g) {
		int capacity = 1;
		if(g == null) return capacity;
		if(Cache.extraCapacity) {
			int members = g.getMembers().size();
			int added = (int) Math.floorDiv(members, Cache.membersPerCapacity);
			if(added > Cache.maxMemberCapacity) {
				added = Cache.maxMemberCapacity;
			}
			capacity = capacity+added+getExtraCapacity(g);
		}
		return capacity;
	}
	public Node getByLocation(Location loc) {
		for(Node n : nodes) {
			if(n.getLoc().equals(loc)) return n;
		}
		return null;
	}
	public String getClickedFurniture(Block b) {
		List<Entity> nearbyEntities = (List<Entity>) b.getWorld().getNearbyEntities(b.getLocation(), 0.2, 0.2, 0.2);
		for(Entity a : b.getWorld().getEntities()){
            if(nearbyEntities.contains(a)){
            	CustomFurniture f = CustomFurniture.byAlreadySpawned(a);
                if(f != null) {
                	return f.getNamespace();
                }
            }
        }
		return "none";
}
	public void start() {
		particleCycle();
		hourCycle();
		new BukkitRunnable()
		{
			public void run()
			   {
					validate();
					for(Node n : nodes) {
						if(!n.getIsActive()) continue;
						if(!n.hasGuild()) continue;
						if(n.getLoc().getChunk().isForceLoaded() == false) {
							n.getLoc().getChunk().setForceLoaded(true);
						}
						n.tickCycle();
						if(n.getCycleTime().equals(Cache.cycleLength)) {
							n.setCycleTime(0);
							n.input();
						}
						if(n.getTimeLeft() > 0) {
							n.tick();
						} else {
							ItemDropper dropper = new ItemDropper();
							dropper.dropItems(n);
							n.setTimeLeft(n.getModifiedTime());
							n.setInputCounter(0);
						}
						for(Player p : Bukkit.getOnlinePlayers()) {
							if(currentNode.containsKey(p)) {
								if(currentNode.get(p).getId().equals(n.getId())) {
									InventoryManager inv = new InventoryManager();
									Inventory i = p.getOpenInventory().getTopInventory();
									if(i == null) continue;
									if(p.getOpenInventory() == null) continue;
									if(p.getOpenInventory().getTitle().equalsIgnoreCase("§7"+n.getBlock().getResource()+ " Node")) {
										inv.updateNodeView(p, n, i);
									}
								}
							}
						}
					}	
			   }
		}.runTaskTimer(DowsingMain.plugin, 0L, 1200L);
	}
	public void particleCycle(){
		new BukkitRunnable()
		{
			public void run()
			{
				for(Node n : nodes){
					if(n.isClaimable()){
						Location loc = n.getLoc().clone().add(0.5, 1, 0.5);
						loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 10);
					} else {
						n.check();
					}
				}	
			}
		}.runTaskTimer(DowsingMain.plugin, 0L, 5L);
	}
	public void hourCycle(){
		new BukkitRunnable()
		{
			public void run()
			{
				for(Node n : nodes){
					n.growEfficiency();
				}	
			}
		}.runTaskTimer(DowsingMain.plugin, 0L, 72000L);
	}
	public void validate() {
		for(int i = 0; i<nodes.size();i++) {
			Node n = nodes.get(i);
			if(!n.getLoc().getBlock().getType().equals(Material.BARRIER)) {
				n.breakNode();
				nodes.remove(i);
			}
		}
	}
	public void confirmClick(Player p, Node n, ConfirmType t) {
		if(t != ConfirmType.DEACTIVATE && blockPendingRefund(p, n)) return;
		if(t.equals(ConfirmType.DEACTIVATE)) {
			n.deActivate();
			InventoryManager inv = new InventoryManager();
			inv.nodeView(p, n);
			currentNode.put(p, n);
		}
		if(t.equals(ConfirmType.DELETE_NODE)) {
			n.breakNode();
			nodes.remove(n);
			p.closeInventory();
		}
		if(t.equals(ConfirmType.CHANGE_TYPE)) {
			NodeType nt = currentType.get(p);
			n.setCurrentType(nt);
			//n.setLevel(1);
			n.updateEfficiency(-Cache.efficiencyLossType);
			n.update();
			requestNodeBenefitSync();
			InventoryManager inv = new InventoryManager();
			inv.nodeView(p, n);
			currentNode.put(p, n);
		}
	}
	private boolean blockPendingRefund(Player p, Node n) {
		if(!n.hasPendingRefund()) return false;
		p.sendMessage("§cRestore the barrel and hopper, then click Retry Refund before changing this node.");
		return true;
	}
	@EventHandler(ignoreCancelled = true)
	public void placeVanillaNode(BlockPlaceEvent e) {
		NodeBlock b = BlockLoader.getByBlock(e.getBlock().getType());
		if(b == null) return;
		tryCreateNode(e.getPlayer(), e.getBlock().getLocation(), b, e);
	}
	@EventHandler(ignoreCancelled = true)
	public void placeFurnitureNode(FurniturePlaceSuccessEvent e) {
		if(e.getNamespacedID() == null) return;
		NodeBlock b = BlockLoader.getByPath(e.getNamespacedID());
		if(b == null) return;
		if(e.getBukkitEntity() == null) return;
		Location loc = e.getBukkitEntity().getLocation().getBlock().getLocation();
		Cancellable placement = new Cancellable() {
			private boolean cancelled;
			public boolean isCancelled() { return cancelled; }
			public void setCancelled(boolean cancel) { cancelled = cancel; }
		};
		tryCreateNode(e.getPlayer(), loc, b, placement);
		if(placement.isCancelled()) {
			CustomFurniture furniture = CustomFurniture.byAlreadySpawned(e.getBukkitEntity());
			if(furniture != null) {
				furniture.remove(false);
			}
		}
	}
	private void tryCreateNode(Player p, Location loc, NodeBlock b, Cancellable e) {
		if(p == null || loc == null || b == null) {
			e.setCancelled(true);
			return;
		}
		if(getByLocation(loc) != null) {
			e.setCancelled(true);
			return;
		}
		for(Node node : nodes) {
			if(node.getLoc().getChunk().equals(loc.getChunk())) {
				p.sendMessage("§cChunk already has a node!");
				e.setCancelled(true);
				return;
			}
		}
		Guild g = FactionManager.getGuildByMember(p.getName());
		if(g == null) {
			p.sendMessage("§cYou need to have a guild to use nodes!");
			e.setCancelled(true);
			return;
		}
		if(g.getMembers().size() < Cache.minMembersForNode && !b.isSpecial()){
			p.sendMessage("§cYou need at least "+Cache.minMembersForNode+" members in your guild to have a node!");
			e.setCancelled(true);
			return;
		}
		if(getNodeAmount(g)-getNodeCapacity(g) >= 0 && !b.isSpecial()) {
			p.sendMessage("§cYou are already filled your node capacity!");
			e.setCancelled(true);
			return;
		}
		Node n = new Node(loc, g, b);
		p.sendMessage("Node created");
		p.getLocation().getWorld().playSound(n.getLoc(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
		nodes.add(n);
		n.activate();
		n.update();
		requestNodeBenefitSync();
	}
	@EventHandler(ignoreCancelled = true)
	public void breakFurnitureNode(FurnitureBreakEvent e) {
		if(e.getNamespacedID() == null || BlockLoader.getByPath(e.getNamespacedID()) == null) return;
		Location loc = furnitureBlockLocation(e.getBukkitEntity());
		if(loc == null) return;
		if(getByLocation(loc) != null) {
			e.setCancelled(true);
		}
	}
	@EventHandler(ignoreCancelled = true)
	public void breakNode(BlockBreakEvent e) {
		if(getByLocation(e.getBlock().getLocation()) == null) return;
		e.setCancelled(true);
	}
	private Location furnitureBlockLocation(Entity entity) {
		if(entity == null) return null;
		return entity.getLocation().getBlock().getLocation();
	}
	@EventHandler(ignoreCancelled = true)
	public void openNode(PlayerInteractEvent e) {
		if(e.getClickedBlock() == null) return;
		if(e.getAction().equals(Action.LEFT_CLICK_BLOCK)) {
			if(getByLocation(e.getClickedBlock().getLocation()) == null) return;
			e.setCancelled(true);
			return;
		}
		if(!e.getAction().equals(Action.RIGHT_CLICK_BLOCK)) return;
		Player p = e.getPlayer();
		if(getByLocation(e.getClickedBlock().getLocation()) == null) return;
		Node n = getByLocation(e.getClickedBlock().getLocation());
		InventoryManager inv = new InventoryManager();
		e.setCancelled(true);
		if(n.isClaimable()){
			Guild g = FactionManager.getGuildByLeader(p.getName());
			if(g == null) {
				p.sendMessage("§cMust be a guild leader to claim an unclaimed node!");
				p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
				return;
			}
			if(!n.canClaim(p, g)) return;
			p.sendMessage("§aClaimed Node");
			n.setGuild(g);
			p.getLocation().getWorld().playSound(n.getLoc(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
			n.update();
			requestNodeBenefitSync();
			inv.nodeView(p, n);
			currentNode.put(p, n);
			return;
		}
		if(!n.hasGuild()) {
			p.sendMessage("§cNode had no guild and so it broke");
			n.breakNode();
			nodes.remove(n);
			return;
		}
		n.update();
		inv.nodeView(p, n);
		currentNode.put(p, n);
	}
	@EventHandler
	public void invenClick(InventoryClickEvent e) {
		Player p = (Player) e.getWhoClicked();
		if(!currentNode.containsKey(p)) return;
		Node n = currentNode.get(p);
		InventoryManager inv = new InventoryManager();
		if(e.getView().getTitle().equalsIgnoreCase("§7"+n.getBlock().getResource()+ " Node")) {
			e.setCancelled(true);
			if(e.getClickedInventory() != e.getView().getTopInventory()) return;
			if(!n.hasGuild()) {
				n.breakNode();
				nodes.remove(n);
				p.closeInventory();
				return;
			}
			Guild g = FactionManager.getGuildByMember(p.getName());
			if(!p.hasPermission("dowsing.admin") && (g == null || n.getGuild() == null || !n.getGuild().getId().equalsIgnoreCase(g.getId()))) {
				p.sendMessage("§cCannot change another guild's node");
				p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
				return;
			}
			if(e.getSlot() != 17 && blockPendingRefund(p, n)) return;
			if(e.getSlot() == 8) {
				if(n.getIsActive()) {
					p.sendMessage("§cCannot upgrade while node is active");
					p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
					return;
				}
				upgradeNode(p, n, e.getClickedInventory());
			} else if(e.getSlot() == 9) {
				if(n.getIsActive()) {
					p.sendMessage("§cCannot change type while node is active");
					p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
					return;
				}
				p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				inv.typeView(p, n);
			} else if(e.getSlot() == 24 && e.getClickedInventory() == e.getView().getTopInventory()) {
				if(canPurchaseCapacity(g)) {
					purchaseCapacity(p, g, n, e.getClickedInventory());
				} else {
					ItemStack current = e.getCurrentItem();
					if(current != null && current.getType().equals(Material.NETHER_STAR)) {
						p.sendMessage("§cAlready purchased the maximum extra capacity");
						p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
						return;
					}
				}
			} else if(e.getSlot() == 26) {
				if(n.getIsActive()) {
					p.sendMessage("§cCannot downgrade while node is active");
					p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
					return;
				}
				downgradeNode(p, n, e.getClickedInventory());
			} else if(e.getSlot() == 17) {
				if(n.getIsActive()) {
					confirm.put(p, ConfirmType.DEACTIVATE);
					inv.confirmView(p);
				} else if(n.hasPendingRefund()) {
					n.deActivate();
					p.sendMessage(n.hasPendingRefund()
							? "§cRefund still pending. Restore the barrel and hopper, then retry."
							: "§aRefund complete. The node remains inactive.");
				} else {
					n.activate();
					if(n.getIsActive()) {
						p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
					} else {
						p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
					}
				}
				n.update();
				if(n.getIsActive()) {
					requestNodeBenefitSync();
				}
				inv.updateNodeView(p, n, e.getClickedInventory());
			} else if(e.getSlot() == 18) {
				if(!(n.getBlock().isBreakable() || Permissions.isAdmin(p))) return;
				if(n.getIsActive()) {
					p.sendMessage("§cCannot delete node while active");
					p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
					return;
				}
				p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				confirm.put(p, ConfirmType.DELETE_NODE);
				inv.confirmView(p);
			} else if(e.getSlot() == 6) {
				if(!n.getBlock().isTransferable()) return;
				n.setGuild(null);
				p.sendMessage("§aNode set as claimable");
				p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				requestNodeBenefitSync();
				p.closeInventory();
			} else{
				for(NodeSlot slot : n.getCurrentType().getSlots()) {
					if(slot.getSlot().equals(e.getSlot())) {
						if(n.getIsActive()) {
							p.sendMessage("§cCannot change production methods while node is active");
							p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
							return;
						}
						p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
						inv.slotView(p, n, slot);
						currentSlot.put(p, slot);
					}
				}
			}
		} else if(currentSlot.get(p) != null && e.getView().getTitle().equalsIgnoreCase("§7"+n.getBlock().getResource()+" Node: "+WordUtils.capitalize(currentSlot.get(p).getId().replace("_", " ")))) {
			e.setCancelled(true);
			if(e.getClickedInventory() != e.getView().getTopInventory()) return;
			if(e.getSlot() != 26 && blockPendingRefund(p, n)) return;
			if(!n.hasGuild()) {
				n.breakNode();
				nodes.remove(n);
				p.closeInventory();
				return;
			}
			if(e.getSlot() == 26) {
				inv.nodeView(p, n);
				currentNode.put(p, n);
				p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				return;
			}
			NodeSlot slot = currentSlot.get(p);
			ItemStack i = e.getCurrentItem();
			if(i == null) return;
			ProductionMethod pm = PMLoader.getByItemName(i.getItemMeta().getDisplayName());
			if(pm == null) return;
			if(pm.getId().equalsIgnoreCase(slot.getActivePm().getId())) return;
			if(!pm.getPrerequisite().equalsIgnoreCase("none")){
				NodeEngine ng = new NodeEngine();
				if(!ng.checkPrerequisite(pm, n)) {
					p.sendMessage("§cThis production method requires at least "+WordUtils.capitalize(pm.getPrerequisite().replace("_", " ")));
					p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
					return;
				}
			}
			p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
			slot.setActivePm(pm);
			n.updateEfficiency(-Cache.efficiencyLossPM);
			n.update();
			requestNodeBenefitSync();
			inv.nodeView(p, n);
			currentNode.put(p, n);
		} else if(e.getView().getTitle().equalsIgnoreCase("§7"+n.getBlock().getResource()+" Node: Type")) {
			e.setCancelled(true);
			if(e.getClickedInventory() != e.getView().getTopInventory()) return;
			if(e.getSlot() != 26 && blockPendingRefund(p, n)) return;
			if(!n.hasGuild()) {
				n.breakNode();
				nodes.remove(n);
				p.closeInventory();
				return;
			}
			if(e.getSlot() == 26) {
				inv.nodeView(p, n);
				currentNode.put(p, n);
				p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				return;
			}
			ItemStack i = e.getCurrentItem();
			if(i == null) return;
			NodeType t = TypeLoader.getByItemName(i.getItemMeta().getDisplayName());
			if(t == null) return;
			if(t.getId().equalsIgnoreCase(n.getCurrentType().getId())) return;
			if(t.getBiomes().size() > 0) {
				String biome = n.getLoc().getBlock().getBiome().toString();
				if(!t.getBiomes().contains(biome)) {
					p.sendMessage("§cThis node type can only be used in these biomes:");
					for(String s : t.getBiomes()) {
						p.sendMessage("§f- "+WordUtils.capitalize(s.replace("_", " ")));
					}
					p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
					return;
				}
			}
			confirm.put(p, ConfirmType.CHANGE_TYPE);
			currentType.put(p, t);
			inv.confirmView(p);
		} else if(e.getView().getTitle().equalsIgnoreCase("§7Confirm Action")) {
			e.setCancelled(true);
			if(e.getClickedInventory() != e.getView().getTopInventory()) return;
			if(!n.hasGuild()) {
				n.breakNode();
				nodes.remove(n);
				p.closeInventory();
				return;
			}
			if(!confirm.containsKey(p)) return;
			p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
			if(e.getSlot() == 11) {
				confirmClick(p, n, confirm.get(p));
				confirm.remove(p);
			} else if(e.getSlot() == 15) {
				inv.nodeView(p, n);
				currentNode.put(p, n);
			}
		}
	}
	private void purchaseCapacity(Player p, Guild g, Node n, Inventory i) {
		double cost = n.getNodeCapacityCost();
		if(g.getBank() == null) {
			p.sendMessage("§cNo bank");
			p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
			return;
		}
		if(g.isBankrupt() || g.getBank().getWealth() < cost) {
			p.sendMessage("§cNot enough funds");
			p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
			return;
		}
		g.getBank().withdraw(cost);
		extraCapacityByGuild.put(g.getId(), getExtraCapacity(g)+1);
		p.sendMessage("§aPurchased +1 Capacity");
		p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
		InventoryManager inv = new InventoryManager();
		inv.updateNodeView(p, n, i);
		
	}
	public void upgradeNode(Player p, Node n, Inventory i) {
		if(n.getLevel() >= n.getCurrentType().getLevels().size()) {
			p.sendMessage("§cNode is already at max level");
			p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
			return;
		}
		Level newLvl = n.getCurrentType().getLevels().get(n.getLevel());
		Guild g = n.getGuild();
		Double cost = newLvl.getCost()*n.getCostIncrease();
		if(g == null || g.getBank() == null || g.isBankrupt() || g.getBank().getWealth() < cost) {
			p.sendMessage("§cGuild bank does not have enough funds");
			p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
			return;
		}
		p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
		g.getBank().withdraw(cost);
		n.setLevel(n.getLevel()+1);
		InventoryManager inv = new InventoryManager();
		n.update();
		requestNodeBenefitSync();
		inv.updateNodeView(p, n, i);
	}
	public void downgradeNode(Player p, Node n, Inventory i) {
		if(n.getLevel() == 1) {
			p.sendMessage("§cNode cannot go below level 1");
			p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
			return;
		}
		Level lvl = n.getCurrentType().getLevels().get(n.getLevel()-1);
		Double refund = lvl.getCost()*Cache.refundPercentage;
		if(n.getGuild() != null && n.getGuild().getBank() != null) {
			n.getGuild().getBank().deposit(refund);
		}
		p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
		n.setLevel(n.getLevel()-1);
		InventoryManager inv = new InventoryManager();
		n.update();
		requestNodeBenefitSync();
		inv.updateNodeView(p, n, i);
	}
	public void cacheNodes() {
		cached.clear();
		for(Node n : nodes) {
			NodeReloader reloader = new NodeReloader();
			reloader.cache(n);
			cached.put(n.getLoc(), reloader);
		}
	}
	public void loadCache() {
		for(Node n : nodes) {
			n.setBlock(BlockLoader.getByString(n.getBlock().getId()));
			n.setCurrentType(TypeLoader.getByString(n.getCurrentType().getId()));
			NodeReloader reloader = cached.get(n.getLoc());
			reloader.reload(n);
			n.update();
		}
	}
}
