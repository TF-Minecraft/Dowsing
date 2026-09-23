package net.tfminecraft.dowsing.objects;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang.WordUtils;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import dev.lone.itemsadder.api.CustomFurniture;
import net.tfminecraft.dowsing.Cache;
import net.tfminecraft.dowsing.managers.NodeManager;
import net.tfminecraft.dowsing.utils.Database;
import net.tfminecraft.dowsing.utils.DropPaths;
import net.tfminecraft.dowsing.utils.ItemDropper;
import net.tfminecraft.dowsing.utils.NodeEngine;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.utils.Formatter;
import net.tfminecraft.simplefactions.utils.Permissions;

public class Node {
	UUID id;
	Guild guild;
	Boolean isActive;
	Location loc;
	Integer naturalYield;
	Integer level;
	NodeBlock block;
	Integer timeLeft;
	Integer cycleTime;
	Integer modifiedTime;
	Integer yield;
	Double yieldPercent;
	Double wealthModifier;
	Double prestigeGain;
	NodeType currentType;
	Integer multiplier;
	Double costIncrease;
	Map<String, Integer> lastResult = new HashMap<>();
	List<String> addedDrops = new ArrayList<String>();
	List<String> errors = new ArrayList<String>();
	Map<String, Double> completeDrop = new HashMap<>();
	Double timeModifier;
	Integer inputCounter;
	Integer extraction;
	Double upkeep;

	//Production Efficiency
	double efficiency = 0;

	public Double getUpkeep() {
		return upkeep;
	}
	public void setUpkeep(Double upkeep) {
		this.upkeep = upkeep;
	}
	public UUID getId() {
		return id;
	}
	public void setId(UUID id) {
		this.id = id;
	}
	public Boolean hasGuild() {
		return resolveGuild() != null;
	}
	public Integer getExtraction() {
		return extraction;
	}
	public void setExtraction(Integer extraction) {
		this.extraction = extraction;
	}
	public Integer getInputCounter() {
		return inputCounter;
	}
	public void setInputCounter(Integer inputCounter) {
		this.inputCounter = inputCounter;
	}
	public Integer getCycleTime() {
		return cycleTime;
	}
	public void setCycleTime(Integer cycleTime) {
		this.cycleTime = cycleTime;
	}
	public Map<String, Double> getCompleteDrop() {
		return completeDrop;
	}
	public Double getWealthModifier() {
		return wealthModifier;
	}
	public void setWealthModifier(Double wealthModifier) {
		this.wealthModifier = wealthModifier;
	}
	public Double getPrestigeGain() {
		return prestigeGain;
	}
	public void setPrestigeGain(Double prestigeGain) {
		this.prestigeGain = prestigeGain;
	}
	public List<String> getAddedDrops() {
		return addedDrops;
	}
	public void setAddedDrops(List<String> addedDrops) {
		this.addedDrops = addedDrops;
	}
	public Double getTimeModifier() {
		return timeModifier;
	}
	public void setTimeModifier(Double timeModifier) {
		this.timeModifier = timeModifier;
	}
	public Map<String, Integer> getLastResult() {
		return lastResult;
	}
	public void setLastResult(Map<String, Integer> lastResult) {
		this.lastResult = lastResult;
	}
	public NodeBlock getBlock() {
		return block;
	}
	public void setBlock(NodeBlock block) {
		this.block = block;
	}
	public NodeType getCurrentType() {
		return currentType;
	}
	public void setCurrentType(NodeType currentType) {
		this.currentType = currentType;
	}
	public Integer getYield() {
		double base = yield == null ? 0 : yield;
		double percent = yieldPercent == null ? 0.0 : yieldPercent;
		return Math.max(0, (int) Math.round(base * (1.0 + percent / 100.0)));
	}
	public Integer getBaseYield() {
		return yield == null ? 0 : yield;
	}
	public Double getYieldPercent() {
		return yieldPercent == null ? 0.0 : yieldPercent;
	}
	public void setYield(Integer yield) {
		this.yield = yield;
	}
	public Integer getModifiedTime() {
		return modifiedTime;
	}
	public void setModifiedTime(Integer modifiedTime) {
		this.modifiedTime = modifiedTime;
	}
	public Integer getTimeLeft() {
		return timeLeft;
	}
	public void setTimeLeft(Integer timeLeft) {
		this.timeLeft = timeLeft;
	}
	public Guild getGuild() {
		return resolveGuild();
	}
	public void setGuild(Guild g) {
		this.guild = g;
	}
	public Boolean getIsActive() {
		return isActive;
	}
	public void setIsActive(Boolean isActive) {
		this.isActive = isActive;
	}
	public Location getLoc() {
		return loc;
	}
	public void setLoc(Location loc) {
		this.loc = loc;
	}
	public Integer getLevel() {
		return level;
	}
	public void setLevel(Integer level) {
		this.level = level;
	}
	public Integer getNaturalYield() {
		return naturalYield;
	}
	public void setNaturalYield(Integer naturalYield) {
		this.naturalYield = naturalYield;
	}
	public List<String> getErrors() {
		return errors;
	}
	public void setErrors(List<String> errors) {
		this.errors = errors;
	}
	public Integer getMultiplier() {
		return multiplier;
	}
	public void setMultiplier(Integer multiplier) {
		this.multiplier = multiplier;
	}
	public double getNodeCapacityCost() {
		Formatter format = new Formatter();
		int extra = NodeManager.getExtraCapacity(resolveGuild());
		return format.formatDouble(Cache.extraCapacityCost+(Cache.extraCapacityCost*extra));
	}
	public Double getCostIncrease() {
		return costIncrease;
	}
	public void setCostIncrease(Double costIncrease) {
		this.costIncrease = costIncrease;
	}
	public boolean isClaimable() {
		return resolveGuild() == null;
	}

	//Efficiency
	public double getEfficiency() {
		return efficiency;
	}

	Guild resolveGuild() {
		if(guild == null) {
			return null;
		}
		Guild live = FactionManager.getGuildByString(guild.getId());
		if(live == null) {
			guild = null;
			return null;
		}
		guild = live;
		return guild;
	}

	public boolean canHold(Guild g){
		if(g == null) return false;
		Player p = Bukkit.getPlayerExact(g.getLeader());
		if(p != null && p.isOnline() && Permissions.isAdmin(p)) return true;
		if(g.getMembers().size() < Cache.minMembersForNode) return false;
		if(NodeManager.getNodeAmount(g)-NodeManager.getNodeCapacity(g) > 0) return false;
		return true;
	}

	public boolean canClaim(Player p, Guild g){
		if(Permissions.isAdmin(p)) return true;
		if(!isClaimable()) return false;
		if(g.getMembers().size() < Cache.minMembersForNode){
			p.sendMessage("§cYou need at least "+Cache.minMembersForNode+" members in your guild to claim this node!");
			return false;
		}
		if(NodeManager.getNodeAmount(g)-NodeManager.getNodeCapacity(g) >= 0 && !block.isSpecial()) {
			p.sendMessage("§cYou are already filled your node capacity!");
			return false;
		}
		return true;
	}

	public Node(Location l, Guild g, NodeBlock b) {
		this.id = UUID.randomUUID();
		this.block = b;
		this.loc = l;
		this.guild = g;
		if(b.isSpecial()){
			this.guild = null;
		}
		this.efficiency = 50;
		this.isActive = false;
		this.level = 1;
		this.yield = 0;
		this.yieldPercent = 0.0;
		this.timeModifier = 0.0;
		this.prestigeGain = 0.0;
		this.wealthModifier = 0.0;
		this.cycleTime = 0;
		this.currentType = b.getTypes().get(0);
		this.upkeep = 0.0;
		try {
			this.naturalYield = getNaturalYieldFromChunk(this.currentType.getResource(), this.loc);
		} catch (IOException e) {
			e.printStackTrace();
		}
		this.timeLeft = currentType.getTimer();
		this.modifiedTime = timeLeft;
		this.inputCounter = 0;
		this.multiplier = 1;
		this.costIncrease = 1.0;
		if(!isClaimable()) update();
		
	}
	public Node(UUID id, NodeBlock b, Location l, Guild g, Boolean active, int lvl, int cycleTime, NodeType currentType, int timeLeft, int inputCounter, double efficiency) {
		this.id = id;
		this.block = b;
		this.loc = l;
		this.guild = g;
		this.efficiency = efficiency;
		this.isActive = active;
		this.level = lvl;
		this.yield = 0;
		this.yieldPercent = 0.0;
		this.timeModifier = 0.0;
		this.addedDrops = new ArrayList<String>();
		this.extraction = 0;
		this.naturalYield = 0;
		this.upkeep = 0.0;
		this.prestigeGain = 0.0;
		this.wealthModifier = 0.0;
		this.cycleTime = cycleTime;
		this.timeLeft = timeLeft;
		this.modifiedTime = timeLeft;
		this.inputCounter = inputCounter;
		this.multiplier = 1;
		this.costIncrease = 1.0;
		this.currentType = currentType;
		if(!isClaimable()) update();
	}
	Integer getNaturalYieldFromChunk(NodeBlock b, Location l) throws NumberFormatException, IOException {
		Database db = new Database();
		if(db.hasResource(l.getChunk())) {
			String resource = db.getResource(l.getChunk()).split("\\.")[0];
			Integer yield = Integer.parseInt(db.getResource(l.getChunk()).split("\\.")[1]);
			if(b.getResource().equalsIgnoreCase(resource)) {
				return yield;
			}
		}
		return 0;
	}

	public void check() {
		if(isClaimable()) return;
		Guild g = resolveGuild();
		if(g == null) return;
		if(canHold(g)) return;
		Player p = Bukkit.getPlayer(g.getLeader());
		if(p != null && p.isOnline()) {
			p.sendMessage("§cYou lost control of the "+block.getResource()+" Node §c!");
			p.closeInventory();
		}
		guild = null;
		NodeManager.requestNodeBenefitSync();
	}

	public void tick() {
		if(!isClaimable()) this.timeLeft = this.timeLeft-1;
	}
	public void tickCycle() {
		if(!isClaimable()) this.cycleTime = this.cycleTime+1;
	}
	public void input() {
		NodeEngine ng = new NodeEngine();
		Boolean failed = false;
		this.errors.clear();
		if(!ng.hasBarrel(this)) {
			failed = true;
			this.errors.add("§7No barrel");
		}
		if(!ng.hasHopper(this)) {
			failed = true;
			this.errors.add("§7No hopper");
			this.errors.add("§7Lacking resources");
		}
		if(ng.hasBarrel(this)) {
			if(!ng.hasInputs(this)) {
				failed = true;
				this.errors.add("§7Lacking resources");
			}
		}
		if(failed) {
			if(this.isActive) {
				this.deActivate();
			}
			this.update();
			return;
		}
		ng.takeInputs(this);
	}
	public void refund() {
		NodeEngine ng = new NodeEngine();
		Boolean failed = false;
		this.errors.clear();
		if(!ng.hasBarrel(this)) {
			failed = true;
			this.errors.add("§7No barrel");
		}
		if(!ng.hasHopper(this)) {
			failed = true;
			this.errors.add("§7No hopper");
			this.errors.add("§7Lacking resources");
		}
		if(failed) {
			if(this.isActive) {
				this.deActivate();
			}
			this.update();
			return;
		}
		ng.refund(this);;
	}
	public void activate() {
		if(isClaimable()) return;
		NodeEngine ng = new NodeEngine();
		Boolean failed = false;
		this.errors.clear();
		if(!ng.hasBarrel(this)) {
			failed = true;
			this.errors.add("§7No barrel");
		}
		if(!ng.hasHopper(this)) {
			failed = true;
			this.errors.add("§7No hopper");
			this.errors.add("§7Lacking resources");
		}
		if(ng.hasBarrel(this)) {
			if(!ng.hasInputs(this)) {
				failed = true;
				this.errors.add("§7Lacking resources");
			}
		}
		if(this.guild == null || this.guild.getBank() == null || this.guild.isBankrupt()) {
			failed = true;
			this.errors.add("§7No bank");
		}
		if(this.guild != null && this.guild.getBank() != null) {
			if(this.guild.isBankrupt() || this.guild.getBank().getWealth() < this.upkeep) {
				failed = true;
				this.errors.add("§7Lacking upkeep");
			}
		}
		if(this.currentType.getBiomes().size() > 0) {
			String biome = this.loc.getBlock().getBiome().toString();
			if(!this.currentType.getBiomes().contains(biome)) {
				failed = true;
				this.errors.add("§7Wrong biome, change type");
			}
		}
		for(NodeSlot slot : this.currentType.getSlots()) {
			if(!ng.checkPrerequisite(slot.getActivePm(), this)) {
				failed = true;
				this.errors.add("§7"+WordUtils.capitalize(slot.getActivePm().getId().replace("_", " ") +" requires at least "+ WordUtils.capitalize(slot.getActivePm().getPrerequisite().replace("_", " "))));
			}
		}
		if(failed) {
			return;
		}
		this.guild.getBank().withdraw(this.upkeep);
		this.isActive = true;
		this.timeLeft = this.modifiedTime;
		this.cycleTime = 0;
		ng.takeInputs(this);
		NodeManager.requestNodeBenefitSync();
	}
	public void deActivate() {
		this.isActive = false;
		if(isClaimable()) return;
		if(this.cycleTime > 0 && this.cycleTime < Cache.cycleLength) {
			while(this.getInputCounter() > 0) {
				refund();
			}
		}
		NodeManager.requestNodeBenefitSync();
	}

	public void growEfficiency() {
		if(resolveGuild() == null) return;
		int members = guild.getMembers().size();
		double growth = Math.min(1.0, members*Cache.efficiencyGrowthPerMember);
		updateEfficiency(growth);
	}

	public double getMaxEfficiency() {
		if(resolveGuild() == null) return 0;
		return Math.min(100.0, guild.getMembers().size()*Cache.maxEfficiencyPerMember);
	}

	public void updateEfficiency(double eff) {
		efficiency += eff;
		if(efficiency < 0) efficiency = 0;
		if(efficiency > getMaxEfficiency()) efficiency = getMaxEfficiency();
	}

	public void updateEfficiencyTime() {
		int old = modifiedTime;

		// Scale goes from 4x (eff=0) down to 1x (eff=100)
		double scale = 4.0 - (efficiency / 100.0) * 3.0;

		modifiedTime = (int) Math.round(modifiedTime * scale);
	}


	public void update() {
		if(isClaimable()) return;
		this.yield = 0;
		this.yieldPercent = 0.0;
		this.timeModifier = 0.0;
		this.addedDrops = new ArrayList<String>();
		this.extraction = 0;
		this.naturalYield = 0;
		this.upkeep = 0.0;
		this.prestigeGain = 0.0;
		try {
			this.naturalYield = getNaturalYieldFromChunk(this.currentType.getResource(), this.loc);
		} catch (IOException e) {
			e.printStackTrace();
		}
		for(NodeSlot slot : this.getCurrentType().getSlots()) {
			ProductionMethod pm = slot.getActivePm();
			for(String s : pm.getEffects()) {
				if(s.contains("extraction") && this.getNaturalYield() < 1) {
					continue;
				}
				runEffect(s);
			}
		}
		Level lvl = this.getCurrentType().getLevels().get(this.getLevel()-1);
		for(String s : lvl.getEffects()) {
			runEffect(s);
		}
		Double newWealthModifier = 0.0;
		for(Level l : this.getCurrentType().getLevels()) {
			if(l.getLevel() <= this.getLevel()) {
				newWealthModifier = newWealthModifier+l.getCost();
			}
		}
		this.wealthModifier = newWealthModifier;
		if(this.extraction > this.naturalYield) {
			this.extraction = this.naturalYield;
		}
		if(this.naturalYield > 0) {
			Integer extractionY = this.currentType.getYieldNaturalYield();
			Double timeY = this.currentType.getTimeNaturalYield();
			Integer e = this.extraction;
			if(e > 0) {
				timeModifier = timeModifier+(timeY*e);
			}
			while(e >= extractionY) {
				this.yield++;
				e = e-extractionY;
			}
		}
		if(this.timeModifier < -99) {
			modifiedTime = 1;
		} else {
			modifiedTime = (int) Math.round(this.getCurrentType().getTimer()*(1+(this.timeModifier/100)));
		}
		updateEfficiencyTime();
		if(timeLeft > modifiedTime) timeLeft = modifiedTime;
		setCompleteDrops();
		int newMultipler = 1+NodeManager.getNodeAmount(this.guild)-getCapacity();
		if(newMultipler < 1) {
			newMultipler = 1;
		}
		this.multiplier = newMultipler;
		Formatter format = new Formatter();
		this.costIncrease = format.formatDouble(1.0+((multiplier-1.0)*0.5));
		this.upkeep = format.formatDouble(this.upkeep);
	}
	Double getAddedPrestige(String e) {
		String type = e.split("\\(")[0];
		if(type.equalsIgnoreCase("prestige")) {
			return Double.parseDouble(e.split("\\(")[1].replace(")", ""));
		}
		return 0.0;
	}
	public int getCapacity() {
		int capacity = 1;
		if(Cache.extraCapacity && resolveGuild() != null) {
			int members = this.guild.getMembers().size();
			int added = (int) Math.floorDiv(members, Cache.membersPerCapacity);
			capacity = capacity+added+NodeManager.getExtraCapacity(this.guild);
		}
		return capacity;
	}
	void setCompleteDrops() {
		this.completeDrop.clear();
		for(String s : this.getCurrentType().getDrops()) {
			addCompleteDrop(s);
		}
		for(String s : this.addedDrops) {
			addCompleteDrop(s);
		}
	}
	void addCompleteDrop(String token) {
		String[] drop = DropPaths.parseStored(token);
		if(drop == null) {
			return;
		}
		String key = drop[0];
		Double amount = Double.parseDouble(drop[1]);
		if(this.completeDrop.containsKey(key)) {
			amount = amount+this.completeDrop.get(key);
		}
		this.completeDrop.put(key, amount);
	}
	void runEffect(String e) {
		String type = e.split("\\(")[0];
		if(type.equalsIgnoreCase("time_modifier")) {
			timeModifier = timeModifier+Double.parseDouble(e.split("\\(")[1].replace(")", ""));
		} else if(type.equalsIgnoreCase("yield")) {
			yield = yield+Integer.parseInt(e.split("\\(")[1].replace(")", ""));
		} else if(type.equalsIgnoreCase("yield_percent")) {
			yieldPercent = yieldPercent+Double.parseDouble(e.split("\\(")[1].replace(")", ""));
		} else if(type.equalsIgnoreCase("add_drop")) {
			String[] drop = DropPaths.parseAddDropEffect(e);
			if(drop != null) {
				addedDrops.add(DropPaths.formatStored(drop[0], Double.parseDouble(drop[1])));
			}
		} else if(type.equalsIgnoreCase("extraction")) {
			extraction = extraction+Integer.parseInt(e.split("\\(")[1].replace(")", ""));
		} else if(type.equalsIgnoreCase("upkeep")) {
			upkeep = upkeep+Double.parseDouble(e.split("\\(")[1].replace(")", ""));
		} else if(type.equalsIgnoreCase("prestige")) {
			prestigeGain = prestigeGain+getAddedPrestige(e);
		}
	}
	public Integer getNaturalYieldFromChunk(String resource, Location l) throws IOException {
		if(!Cache.naturalYieldEnabled) {
			return 0;
		}
		Chunk c = l.getChunk();
		Database db = new Database();
		if(db.hasResource(c)) {
			String s = db.getResource(c);
			String dbResource = s.split("\\.")[0];
			Integer yield = Integer.parseInt(s.split("\\.")[1]);
			if(resource.equalsIgnoreCase(dbResource)) {
				return yield;
			}
		}
		return 0;
	}
	public void breakNode() {
		String path = this.getBlock().getBlock();
		String type = path.split("\\.")[0];
		if(type.equalsIgnoreCase("v")) {
			this.loc.getBlock().setType(Material.AIR);
		} else if(type.equalsIgnoreCase("ia")) {
			Location center = this.loc.clone().add(0.5, 0.5, 0.5);
			for(Entity a : this.loc.getWorld().getNearbyEntities(center, 1.5, 1.5, 1.5)) {
				CustomFurniture f = CustomFurniture.byAlreadySpawned(a);
				if(f != null) {
					f.remove(false);
					break;
				}
			}
			if(this.loc.getBlock().getType() != Material.AIR) {
				this.loc.getBlock().setType(Material.AIR);
			}
		}
		Location loc = new Location(this.getLoc().getWorld(), this.getLoc().getX(), this.getLoc().getY(), this.getLoc().getZ());
		loc.add(0.5,0,0.5);
		ItemDropper dropper = new ItemDropper();
		dropper.dropItem(loc, path, false);
		loc.getWorld().playSound(loc, Sound.ENTITY_GLOW_ITEM_FRAME_REMOVE_ITEM, 0.5f, 1f);
		NodeManager.requestNodeBenefitSync();
	}
}
