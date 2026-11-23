package net.tfminecraft.dowsing.managers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.lang.WordUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
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
import net.tfminecraft.dowsing.utils.NodePayments;
import net.tfminecraft.simplefactions.api.SimpleFactionsApi;
import net.tfminecraft.dowsing.utils.NodeEngine;
import net.tfminecraft.dowsing.utils.NodeReloader;
import net.tfminecraft.dowsing.utils.NodeToggleLog;
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

	/** Each active node's guild and that guild's realm at the last sync, to spot realm changes. */
	private static List<Object> lastOwners = List.of();
	/** Whether the last sync finished; a failed one is retried even if no owner changed. */
	private static boolean synced = false;

	/**
	 * Node benefits sit on each realm's own guild and only node events resync them. A dissolve,
	 * elevation or relocation moves a node to another realm without one, so {@link #realmCycle}
	 * resyncs when any active node's guild or realm changed since the last sync, or that sync failed.
	 */
	public static void syncIfRealmsChanged() {
		if (!SimpleFactionsApi.ready()) return;
		if (!synced || !sameOwners(owners(), lastOwners)) syncFactionNodeBenefits();
	}

	private static List<Object> owners() {
		List<Object> owners = new ArrayList<>();
		for(Node n : nodes) {
			if(!n.hasGuild() || !Boolean.TRUE.equals(n.getIsActive())) continue;
			Guild g = n.getGuild();
			owners.add(n);
			owners.add(g);
			owners.add(g == null ? null : g.getFaction());
		}
		return owners;
	}

	private static boolean sameOwners(List<Object> now, List<Object> before) {
		if (now.size() != before.size()) return false;
		for (int i = 0; i < now.size(); i++) {
			if (now.get(i) != before.get(i)) return false;
		}
		return true;
	}

	public static void syncFactionNodeBenefits() {
		if (!SimpleFactionsApi.ready()) return;
		synced = false;
		List<Object> owners = owners();
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
		Set<Guild> realmGuilds = Collections.newSetFromMap(new IdentityHashMap<>());
		for(Faction f : FactionManager.getCopy()) {
			double prestige = prestigeByFaction.getOrDefault(f, 0.0);
			double wealth = wealthByFaction.getOrDefault(f, 0.0);
			f.setPersistentPrestigeModifier("Nodes", prestige);
			f.updatePrestige();
			Guild mainGuild = f.getOrCreateMainGuild();
			mainGuild.addWealthModifier(new Modifier("Nodes", wealth, true));
			mainGuild.updateWealth();
			realmGuilds.add(mainGuild);
		}
		// A realm's own guild that became an ordinary guild (its realm dissolved into an overlord)
		// kept its last node value; only realm guilds carry one.
		for(Guild g : FactionManager.getAllGuilds()) {
			if(g == null || realmGuilds.contains(g)) continue;
			if(g.getWealthModifiers().removeIf(m -> "Nodes".equalsIgnoreCase(m.getType()))) g.updateWealth();
		}
		// Only a finished sync counts, so the realm check retries one that failed part way.
		lastOwners = owners;
		synced = true;
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
		if (g == null || Node.held(g)) return 0;
		double total = 0;
		// Every node of g pays the same cost increase, so count g's nodes at most once, and only
		// when a node first needs the count.
		int[] amount = {-1};
		java.util.function.IntSupplier count = () -> amount[0] >= 0 ? amount[0] : (amount[0] = getNodeAmount(g));
		for (Node n : nodes) {
			if (!n.getIsActive()) continue;
			// One lookup of the node's guild answers both whether it has one and which it is.
			Guild owner = n.getGuild();
			if (owner == null || !owner.getId().equalsIgnoreCase(g.getId())) continue;
			total += n.getDailyUpkeep(count);
		}
		return total;
	}

	public static Integer getNodeAmount(Guild g) {
		Integer i = 0;
		if(g == null) return i;
		if(passCounts != null) return g.getId() == null ? i : passCounts.getOrDefault(g.getId(), i);
		for(Node n : nodes) {
			Guild owner = n.getGuild();
			if(owner == null) continue;
			if(n.getBlock().isSpecial()) continue;
			if(owner.getId().equalsIgnoreCase(g.getId())) i++;
		}
		return i;
	}
	/**
	 * Claimed-node counts per guild for one particle pass. The pass checks every claimed node four
	 * times a second, and each check counted its guild's nodes by walking every node, so a pass cost
	 * nodes x nodes guild lookups. Counted once per pass instead, and kept right by {@link #released}.
	 */
	private static Map<String, Integer> passCounts;
	/**
	 * Whether each guild checked so far in one particle pass can hold its nodes. {@link Node#canHold}
	 * asks only about the guild, so all its nodes get the same answer until one is let go; that
	 * changes the count and resyncs realms, so {@link #released} forgets the answers.
	 */
	private static Map<Guild, Boolean> passHolds;

	static void beginCountPass() {
		Map<String, Integer> counts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for(Node n : nodes) {
			Guild owner = n.getGuild();
			if(owner == null) continue;
			if(n.getBlock().isSpecial()) continue;
			String id = owner.getId();
			if(id != null) counts.merge(id, 1, Integer::sum);
		}
		passCounts = counts;
		passHolds = new IdentityHashMap<>();
	}

	static void endCountPass() {
		passCounts = null;
		passHolds = null;
	}

	/** A node of {@code g} was just let go during a particle pass, so its guild holds one fewer. */
	public static void released(Guild g, Node n) {
		if(passHolds != null) passHolds.clear();
		if(passCounts == null || g == null || g.getId() == null || n.getBlock().isSpecial()) return;
		passCounts.computeIfPresent(g.getId(), (id, count) -> count - 1);
	}

	/** {@link Node#canHold} for a node's check: worked out once per guild during a particle pass. */
	public static boolean canHold(Node n, Guild g) {
		if(passHolds == null) return n.canHold(g);
		return passHolds.computeIfAbsent(g, n::canHold);
	}

	/** Room for new nodes, from the guild's active members. */
	public static Integer getNodeCapacity(Guild g) {
		return capacityFor(g, net.tfminecraft.dowsing.utils.ActiveMembers.count(g));
	}

	/** Room to keep the nodes a guild already holds, from all its members, so going inactive does not cost a node. */
	public static Integer getHeldNodeCapacity(Guild g) {
		return capacityFor(g, g == null || g.getMembers() == null ? 0 : g.getMembers().size());
	}

	private static Integer capacityFor(Guild g, int members) {
		int capacity = 1;
		if(g == null) return capacity;
		if(Cache.extraCapacity) {
			int added = (int) Math.floorDiv(members, Cache.membersPerCapacity);
			if(added > Cache.maxMemberCapacity) {
				added = Cache.maxMemberCapacity;
			}
			capacity = capacity+added;
		}
		return capacity+getExtraCapacity(g);
	}
	public Node getByLocation(Location loc) {
		for(Node n : nodes) {
			if(n.getLoc().equals(loc)) return n;
		}
		return null;
	}
	public String getClickedFurniture(Block b) {
		Collection<Entity> nearbyEntities = b.getWorld().getNearbyEntities(b.getLocation(), 0.2, 0.2, 0.2);
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
		realmCycle();
		new BukkitRunnable()
		{
			public void run()
			   {
					if (!DowsingMain.plugin.nodesReady()) return;
					validate();
					// Failures are handled per node and per chunk inside, so production below always runs.
					holdNodeChunks();
					for(Node n : nodes) {
						try {
						// Switched-off and unowned nodes wait, and a held realm's nodes pause until it is taken on.
						if(!producing(n)) continue;
						if(n.getTimeLeft() <= 0) {
							if(!new ItemDropper().dropItems(n)) continue;
							n.setTimeLeft(n.getModifiedTime());
							n.setInputCounter(0);
							startDrain(n);
						}
						n.tickCycle();
						if(n.getCycleTime().equals(Cache.cycleLength)) {
							n.setCycleTime(0);
							n.input();
							startDrain(n);
						}
						if(!n.getIsActive()) continue;
						n.tick();
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
						} catch(RuntimeException ex) {
							DowsingMain.plugin.getLogger().log(java.util.logging.Level.SEVERE,
									"Failed to process node " + n.getId() + " at " + n.getLoc(), ex);
						}
					}	
			   }
		}.runTaskTimer(DowsingMain.plugin, 0L, 1200L);
	}
	/** Whether a node produces this pass: switched on, owned, and not in a held realm. */
	static boolean producing(Node n) {
		if(!n.getIsActive() || !n.hasGuild()) return false;
		return !Node.held(n.getGuild());
	}

	/** A world chunk that holds a node. */
	record NodeChunk(World world, int x, int z) {
		static NodeChunk of(Node n) {
			return at(n.getLoc());
		}
		static NodeChunk at(Location loc) {
			if(loc == null || loc.getWorld() == null) return null;
			return new NodeChunk(loc.getWorld(), loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
		}
		boolean loaded() { return world.isChunkLoaded(x, z); }
		boolean forced() { return world.isChunkForceLoaded(x, z); }
		void force(boolean on) { world.setChunkForceLoaded(x, z, on); }
	}

	/** The chunks the passes of this start force-loaded for producing nodes. */
	private static final Set<NodeChunk> heldChunks = new HashSet<>();
	/** Whether this start has let go of the idle node chunks an earlier start left force-loaded. */
	private static boolean startCleared = false;

	/**
	 * Keeps a producing node's chunk force-loaded while it works, so it produces and its hopper runs with
	 * nobody near. With timed chunks (the default) that is only around its drops and inputs: the chunk loads
	 * in the background a little before, and stays for the drain time after (longer while the node's hopper
	 * still holds items); "always" keeps it the whole time. Configured keep-active chunks stay forced so their
	 * world keeps ticking while nobody is online. Lets go of the chunk once no node in it needs it: switched
	 * off, unowned, in a held realm, or between a timed node's events. A
	 * removed node's chunk is let go when it breaks ({@link #releaseChunkOf}). A forced chunk never unloads
	 * and keeps every chunk a player loads within 13 chunks of it in memory, so an idle node's chunk must
	 * not stay forced. The force flag (not a plugin ticket) is kept on purpose: like before, it keeps the
	 * world ticking hoppers while nobody is online. It is saved with the world, so the first pass of each
	 * start lets go of the chunk of every idle node Dowsing loaded, including those Dowsing 3.4 and older
	 * never let go; spots of nodes removed before this version are not known and need a manual check.
	 * Later passes only touch chunks this start forced. Waits for SimpleFactions, which says who owns a node.
	 */
	static void holdNodeChunks() {
		if(!SimpleFactionsApi.ready()) return;
		advanceDrains();
		Set<NodeChunk> wanted = new HashSet<>();
		Set<NodeChunk> soon = new HashSet<>();
		Set<NodeChunk> unknown = new HashSet<>();
		Set<NodeChunk> idle = new HashSet<>(heldChunks);
		wanted.addAll(keepActiveChunks());
		boolean lookupFailed = false;
		for(Node n : nodes) {
			NodeChunk chunk;
			try {
				chunk = NodeChunk.of(n);
			} catch(RuntimeException ex) {
				logChunkFailure("Failed to find node " + n.getId() + "'s chunk", ex);
				lookupFailed = true;
				continue;
			}
			if(chunk == null) continue;
			try {
				// With timed chunks a producing node needs its chunk only around a drop or an input.
				if(producing(n) && needsChunkNow(n)) wanted.add(chunk);
				else if(producing(n) && needsChunkSoon(n)) soon.add(chunk);
				else if(!startCleared) idle.add(chunk);
			} catch(RuntimeException ex) {
				// Not known whether it produces: its chunk keeps what it had and is not let go.
				logChunkFailure("Failed to check node " + n.getId() + " for chunk loading", ex);
				unknown.add(chunk);
			}
		}
		soon.removeAll(wanted);
		idle.removeAll(wanted);
		idle.removeAll(soon);
		idle.removeAll(unknown);
		upcoming.clear();
		upcoming.addAll(wanted);
		upcoming.addAll(soon);
		Set<NodeChunk> next = new HashSet<>();
		if(lookupFailed) {
			// A node whose chunk is unknown may be in any of them: nothing is let go this pass. Held
			// chunks stay tracked, and the first pass's clean-up waits for a pass that knows every chunk.
			for(NodeChunk chunk : idle) {
				if(heldChunks.contains(chunk)) next.add(chunk);
			}
			idle.clear();
		}
		// On the first pass a chunk that could not be handled is tracked anyway, so a later pass can still
		// let go of a flag an earlier start left on it.
		boolean keepAll = !startCleared;
		for(NodeChunk chunk : wanted) {
			// A held chunk that could not be checked stays held, so it is still let go once idle.
			if(setForced(chunk, true) || keepAll || heldChunks.contains(chunk)) next.add(chunk);
		}
		for(NodeChunk chunk : soon) {
			// Loaded in the background ahead of a drop or input, so the pass never waits for the disk.
			if(chunk.loaded()) setForced(chunk, true);
			else preload(chunk);
			next.add(chunk);
		}
		for(NodeChunk chunk : idle) {
			// Kept when it could not be let go, so the next pass tries again.
			if(!setForced(chunk, false)) next.add(chunk);
		}
		for(NodeChunk chunk : unknown) {
			if(keepAll || heldChunks.contains(chunk)) next.add(chunk);
		}
		heldChunks.clear();
		heldChunks.addAll(next);
		if(!lookupFailed) startCleared = true;
	}

	private static void logChunkFailure(String message, Throwable ex) {
		DowsingMain.plugin.getLogger().log(java.util.logging.Level.SEVERE, message, ex);
	}

	/** Chunks wanted or about to be wanted this pass; a background load finishing later only holds these. */
	private static final Set<NodeChunk> upcoming = new HashSet<>();
	/** Chunks with a background load still running. */
	private static final Set<NodeChunk> loading = new HashSet<>();
	/** Per node: minutes its chunk stays held after a drop or input, and the extensions used. */
	private static final Map<java.util.UUID, int[]> drains = new HashMap<>();

	/** True when the node drops, takes inputs or is draining this pass, or chunks are not timed. */
	static boolean needsChunkNow(Node n) {
		if(!Cache.timedNodeChunks) return true;
		if(n.getTimeLeft() <= 0 || drains.containsKey(n.getId())) return true;
		return Cache.cycleLength != null && n.getCycleTime() + 1 >= Cache.cycleLength;
	}

	/** True when a drop or an input comes within its lead time, so the chunk loads in the background. */
	static boolean needsChunkSoon(Node n) {
		if(n.getTimeLeft() <= Cache.nodeChunkLeadMinutes) return true;
		return Cache.cycleLength != null && Cache.cycleLength - n.getCycleTime() <= Cache.nodeChunkInputLeadMinutes + 1;
	}

	/** Keeps a node's chunk held for the drain time after it dropped items or took inputs. */
	static void startDrain(Node n) {
		if(Cache.timedNodeChunks) drains.put(n.getId(), new int[] {Cache.nodeChunkDrainMinutes, 0});
	}

	/**
	 * Counts down each node's drain time, once per pass. When it runs out while the node's hopper still
	 * holds items it is extended, up to the configured number of times; removed nodes are forgotten.
	 */
	static void advanceDrains() {
		Set<java.util.UUID> present = new HashSet<>();
		Map<java.util.UUID, Node> byId = new HashMap<>();
		for(Node n : nodes) {
			present.add(n.getId());
			byId.put(n.getId(), n);
		}
		drains.keySet().retainAll(present);
		java.util.Iterator<Map.Entry<java.util.UUID, int[]>> it = drains.entrySet().iterator();
		while(it.hasNext()) {
			Map.Entry<java.util.UUID, int[]> e = it.next();
			int[] d = e.getValue();
			if(--d[0] > 0) continue;
			if(d[1] < Cache.nodeChunkMaxExtensions && hopperHoldsItems(byId.get(e.getKey()))) {
				d[0] = Cache.nodeChunkDrainMinutes;
				d[1]++;
			} else {
				it.remove();
			}
		}
	}

	/** Whether the hopper on top of the node still holds items; false when its chunk is not loaded. */
	static boolean hopperHoldsItems(Node n) {
		try {
			NodeChunk chunk = NodeChunk.of(n);
			if(chunk == null || !chunk.loaded()) return false;
			Block block = n.getLoc().clone().add(0, 1, 0).getBlock();
			if(block.getType() != Material.HOPPER) return false;
			return !((org.bukkit.block.Hopper) block.getState(false)).getInventory().isEmpty();
		} catch(RuntimeException ex) {
			logChunkFailure("Failed to check node " + n.getId() + "'s hopper", ex);
			return false;
		}
	}

	/** Loads a chunk without blocking the server thread and holds it once loaded, if still needed then. */
	static void preload(NodeChunk chunk) {
		if(!loading.add(chunk)) return;
		try {
			chunk.world().getChunkAtAsync(chunk.x(), chunk.z()).whenComplete((loaded, ex) ->
					Bukkit.getScheduler().runTask(DowsingMain.plugin, () -> {
						loading.remove(chunk);
						if(ex != null) logChunkFailure("Failed to load node chunk " + chunk.x() + ", " + chunk.z(), ex);
						else if(upcoming.contains(chunk)) setForced(chunk, true);
					}));
		} catch(RuntimeException ex) {
			loading.remove(chunk);
			logChunkFailure("Failed to load node chunk " + chunk.x() + ", " + chunk.z(), ex);
		}
	}

	/** The configured always-loaded chunks whose world exists; a malformed entry is skipped. */
	static Set<NodeChunk> keepActiveChunks() {
		Set<NodeChunk> chunks = new HashSet<>();
		for(String entry : Cache.keepActiveChunks) {
			String[] p = entry.trim().split("\\s+");
			World w = p.length == 3 ? Bukkit.getWorld(p[0]) : null;
			if(w == null) continue;
			try {
				chunks.add(new NodeChunk(w, Integer.parseInt(p[1]) >> 4, Integer.parseInt(p[2]) >> 4));
			} catch(NumberFormatException ex) {
				// Skipped like an unknown world.
			}
		}
		return chunks;
	}

	/** Sets one chunk's force flag; a failing chunk is logged and does not stop the others. */
	private static boolean setForced(NodeChunk chunk, boolean on) {
		try {
			if(chunk.forced() != on) chunk.force(on);
			return true;
		} catch(RuntimeException ex) {
			DowsingMain.plugin.getLogger().log(java.util.logging.Level.SEVERE,
					"Failed to " + (on ? "hold" : "let go of") + " node chunk " + chunk.x() + ", " + chunk.z(), ex);
			return false;
		}
	}

	/**
	 * Lets go of a removed node's chunk right away, unless another producing node is in it, so the saved
	 * force flag cannot outlive the node when the server stops before the next pass.
	 */
	public static void releaseChunkOf(Node removed) {
		NodeChunk chunk = NodeChunk.of(removed);
		if(chunk == null) return;
		for(Node n : nodes) {
			if(n != removed && chunk.equals(NodeChunk.of(n)) && producing(n)) return;
		}
		// Only a chunk Dowsing holds, or any node chunk before the first pass has cleared them; a staff
		// force-load made later on an idle node's chunk is left alone.
		if(!heldChunks.contains(chunk) && startCleared) return;
		// Tracked until it is let go, so a failed release is tried again by the next pass.
		if(setForced(chunk, false)) heldChunks.remove(chunk);
		else heldChunks.add(chunk);
	}

	/** Forgets what this start held and cleared, for tests. */
	static void forgetHeldChunks() {
		heldChunks.clear();
		startCleared = false;
		upcoming.clear();
		loading.clear();
		drains.clear();
	}

	/** Every 30 s: resync node benefits when a node's guild or realm changed without a node event. */
	public void realmCycle(){
		new BukkitRunnable()
		{
			public void run()
			{
				if (!DowsingMain.plugin.nodesReady()) return;
				try {
					syncIfRealmsChanged();
				} catch(RuntimeException ex) {
					DowsingMain.plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to check node realms", ex);
				}
			}
		}.runTaskTimer(DowsingMain.plugin, 0L, 600L);
	}
	public void particleCycle(){
		new BukkitRunnable()
		{
			public void run()
			{
				if (!DowsingMain.plugin.nodesReady()) return;
				beginCountPass();
				try {
					for(Node n : nodes){
						if(!SimpleFactionsApi.ready()) continue;
						// Looked up once here and handed to the check, not once to see if the
						// node is claimable and twice more inside the check.
						Guild g = n.getGuild();
						if(g == null){
							Location loc = n.getLoc().clone().add(0.5, 1, 0.5);
							loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 10);
						} else {
							n.check(g);
						}
					}
				} finally {
					endCountPass();
				}
			}
		}.runTaskTimer(DowsingMain.plugin, 0L, 5L);
	}
	public void hourCycle(){
		new BukkitRunnable()
		{
			public void run()
			{
				if (!DowsingMain.plugin.nodesReady()) return;
				for(Node n : nodes){
					n.growEfficiency();
				}	
			}
		}.runTaskTimer(DowsingMain.plugin, 0L, 72000L);
	}
	public void validate() {
		if (!SimpleFactionsApi.ready()) return;
		for(int i = 0; i<nodes.size();i++) {
			Node n = nodes.get(i);
			try {
				// Reading a block in an unloaded chunk loads it on the main thread. Nobody can break a node
				// whose chunk is not loaded, so it is checked when its chunk is next loaded.
				NodeChunk chunk = NodeChunk.of(n);
				if(chunk == null || !chunk.loaded()) continue;
				if(!n.getLoc().getBlock().getType().equals(Material.BARRIER)) {
					n.breakNode();
					nodes.remove(i--);
				}
			} catch(RuntimeException ex) {
				// One node that cannot be checked does not stop the others or the pass.
				logChunkFailure("Failed to validate node " + n.getId(), ex);
			}
		}
	}
	public void confirmClick(Player p, Node n, ConfirmType t) {
		if (!DowsingMain.plugin.nodesReady()) return;
		if(t != ConfirmType.DEACTIVATE && blockPendingRefund(p, n)) return;
		if(t != ConfirmType.DEACTIVATE && blockActiveChange(p, n,
				t == ConfirmType.DELETE_NODE ? "delete node" : "change type")) return;
		if(t.equals(ConfirmType.DEACTIVATE)) {
			boolean wasActive = n.getIsActive();
			n.deActivate();
			if(wasActive) NodeToggleLog.deactivated(p, n);
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
		confirm.remove(p);
	}
	private boolean blockPendingRefund(Player p, Node n) {
		if(!n.hasPendingRefund()) return false;
		p.sendMessage("§cRestore the barrel and hopper, then click Retry Refund before changing this node.");
		return true;
	}
	private boolean canManageNode(Player p, Node n) {
		if(p.hasPermission("dowsing.admin")) return true;
		Guild memberGuild = FactionManager.getGuildByMember(p);
		Guild owner = n.getGuild();
		if(memberGuild != null && owner != null && owner.getId().equalsIgnoreCase(memberGuild.getId())) return true;
		p.sendMessage("§cCannot change another guild's node");
		p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
		return false;
	}
	private boolean blockActiveChange(Player p, Node n, String action) {
		if(!n.getIsActive()) return false;
		p.sendMessage("§cCannot "+action+" while node is active");
		p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
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
		if (!DowsingMain.plugin.nodesReady()) { e.setCancelled(true); return; }
		if(p == null || loc == null || b == null) {
			e.setCancelled(true);
			return;
		}
		if(getByLocation(loc) != null) {
			e.setCancelled(true);
			return;
		}
		for(Node node : nodes) {
			// Compared by position, so placing a node does not load every other node's chunk.
			if(java.util.Objects.equals(NodeChunk.of(node), NodeChunk.at(loc))) {
				p.sendMessage("§cThere is already a node on this land!");
				e.setCancelled(true);
				return;
			}
		}
		Guild g = FactionManager.getGuildByMember(p);
		if(g == null) {
			p.sendMessage("§cYou need to have a guild to use nodes!");
			e.setCancelled(true);
			return;
		}
		if(net.tfminecraft.dowsing.utils.ActiveMembers.count(g) < Cache.minMembersForNode && !b.isSpecial()){
			p.sendMessage("§cYou need at least "+Cache.minMembersForNode+" active members in your guild to have a node!");
			e.setCancelled(true);
			return;
		}
		if(getNodeAmount(g)-getNodeCapacity(g) >= 0 && !b.isSpecial()) {
			p.sendMessage("§cYour guild has already filled its node capacity!");
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
		if (!DowsingMain.plugin.nodesReady()) return;
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
			Guild g = FactionManager.getGuildByLeader(p);
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
		if (!DowsingMain.plugin.nodesReady()) {
			if (currentNode.containsKey(e.getWhoClicked())) e.setCancelled(true);
			return;
		}
		Player p = (Player) e.getWhoClicked();
		if(!currentNode.containsKey(p)) return;
		Node n = currentNode.get(p);
		if(!nodes.contains(n)) {
			// The node was already removed; stale menus must not act on it.
			currentNode.remove(p);
			p.closeInventory();
			return;
		}
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
			Guild g = FactionManager.getGuildByMember(p);
			if(!canManageNode(p, n)) return;
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
						NodeToggleLog.activated(p, n);
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
			if(!canManageNode(p, n)) return;
			if(blockActiveChange(p, n, "change production methods")) return;
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
			if(!canManageNode(p, n)) return;
			if(blockActiveChange(p, n, "change type")) return;
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
			if(e.getSlot() == 11 && !canManageNode(p, n)) return;
			p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
			if(e.getSlot() == 11) {
				confirmClick(p, n, confirm.get(p));
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
		if (!payments().change(n, g, "capacity", getExtraCapacity(g), getExtraCapacity(g)+1, cost)) {
			p.sendMessage("§cNode payment is pending or unavailable. Try again shortly.");
			return;
		}
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
		if (!payments().change(n, g, "level", n.getLevel(), n.getLevel()+1, cost)) {
			p.sendMessage("§cNode payment is pending or unavailable. Try again shortly.");
			return;
		}
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
		if(n.getGuild() == null || n.getGuild().getBank() == null ||
				!payments().change(n, n.getGuild(), "level", n.getLevel(), n.getLevel()-1, -refund)) {
			p.sendMessage("§cNode payment is pending or unavailable. Try again shortly.");
			return;
		}
		p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
		InventoryManager inv = new InventoryManager();
		n.update();
		requestNodeBenefitSync();
		inv.updateNodeView(p, n, i);
	}
	private NodePayments payments() {
		return DowsingMain.plugin.payments();
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
