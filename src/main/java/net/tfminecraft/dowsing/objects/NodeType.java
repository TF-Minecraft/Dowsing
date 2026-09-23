package net.tfminecraft.dowsing.objects;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.dowsing.loaders.SlotLoader;
import net.tfminecraft.dowsing.utils.ItemCreator;

public class NodeType {
	String id;
	String name;
	Integer timer;
	ItemStack menuItem;
	Integer maxLevel;
	String resource;
	Integer yieldNaturalYield;
	Double timeNaturalYield;
	List<NodeSlot> slots = new ArrayList<NodeSlot>();
	List<Level> levels = new ArrayList<Level>();
	List<String> drops = new ArrayList<String>();
	List<String> biomes = new ArrayList<String>();
	public List<String> getBiomes() {
		return biomes;
	}
	public void setBiomes(List<String> biomes) {
		this.biomes = biomes;
	}
	public Integer getYieldNaturalYield() {
		return yieldNaturalYield;
	}
	public void setYieldNaturalYield(Integer yieldNaturalYield) {
		this.yieldNaturalYield = yieldNaturalYield;
	}
	public Double getTimeNaturalYield() {
		return timeNaturalYield;
	}
	public void setTimeNaturalYield(Double timeNaturalYield) {
		this.timeNaturalYield = timeNaturalYield;
	}
	public String getResource() {
		return resource;
	}
	public void setResource(String resource) {
		this.resource = resource;
	}
	public Integer getMaxLevel() {
		return maxLevel;
	}
	public void setMaxLevel(Integer maxLevel) {
		this.maxLevel = maxLevel;
	}
	public List<String> getDrops() {
		return drops;
	}
	public void setDrops(List<String> drops) {
		this.drops = drops;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public ItemStack getMenuItem() {
		return menuItem;
	}
	public void setMenuItem(ItemStack menuItem) {
		this.menuItem = menuItem;
	}
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public Integer getTimer() {
		return timer;
	}
	public void setTimer(Integer timer) {
		this.timer = timer;
	}
	public List<NodeSlot> getSlots() {
		return slots;
	}
	public void setSlots(List<NodeSlot> slots) {
		this.slots = slots;
	}
	public List<Level> getLevels() {
		return levels;
	}
	public void setLevels(List<Level> levels) {
		this.levels = levels;
	}
	public NodeType(String key, ConfigurationSection config) {
		this.id = key;
		this.name = config.getString("name");
		this.timer = config.getInt("timer");
		this.resource = config.getString("resource");
		this.timeNaturalYield = config.getDouble("time-reduction-per-natural-yield");
		this.yieldNaturalYield = config.getInt("natural-yield-per-yield");
		ItemCreator ic = new ItemCreator();
		this.menuItem = ic.createTypeItemConfig(config.getConfigurationSection("item"));
		List<NodeSlot> slotList = new ArrayList<NodeSlot>();
		for(String s : config.getStringList("slots")) {
			slotList.add(SlotLoader.getByString(s));
		}
		this.slots = slotList;
		List<Level> lvl = new ArrayList<Level>();
		
		Set<String> set = config.getConfigurationSection("levels").getKeys(false);

		List<String> list = new ArrayList<String>(set);
		
		for(String lvlkey : list) {
			Level l = new Level(lvlkey, config.getConfigurationSection("levels."+lvlkey));
			lvl.add(l);
		}
		this.levels = lvl;
		this.maxLevel = this.levels.size();
		if(config.contains("drops")) {
			this.drops = config.getStringList("drops");
		}
		if(config.contains("biomes")) {
			this.biomes = config.getStringList("biomes");
			System.out.println(this.biomes);
		}
	}
	public NodeType(NodeType another) {
		this.id = another.id;
		this.name = another.name;
		this.timer = another.timer;
		this.menuItem = another.menuItem;
		this.resource = another.resource;
		this.yieldNaturalYield = another.yieldNaturalYield;
		this.timeNaturalYield = another.timeNaturalYield;
		this.biomes = another.biomes;
		for(NodeSlot s : another.slots) {
			this.slots.add(new NodeSlot(s));
		}
		for(Level l : another.levels) {
			this.levels.add(new Level(l));
		}
		this.levels = another.levels;
		this.maxLevel = another.maxLevel;
		this.drops = another.drops;
	}
}
