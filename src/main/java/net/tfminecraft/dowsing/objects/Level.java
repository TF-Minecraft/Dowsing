package net.tfminecraft.dowsing.objects;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;

public class Level {
	Integer level;
	List<String> effects = new ArrayList<String>();
	Double cost;
	public Integer getLevel() {
		return level;
	}
	public void setLevel(Integer level) {
		this.level = level;
	}
	public List<String> getEffects() {
		return effects;
	}
	public void setEffects(List<String> effects) {
		this.effects = effects;
	}
	public Double getCost() {
		return cost;
	}
	public void setCost(Double cost) {
		this.cost = cost;
	}
	public Level(String key, ConfigurationSection config) {
		this.level = Integer.parseInt(key);
		this.effects = config.getStringList("effects");
		this.cost = config.getDouble("cost");
	}
	public Level(Level another) {
		this.level = another.level;
		this.effects = another.effects;
		this.cost = another.cost;
	}
}
