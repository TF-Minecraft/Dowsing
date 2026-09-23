package net.tfminecraft.dowsing.objects;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.dowsing.utils.ItemCreator;

public class ProductionMethod {
	String id;
	ItemStack menuItem;
	Integer weight;
	List<String> effects = new ArrayList<String>();
	List<String> inputs = new ArrayList<String>();
	String prerequisite;
	public Integer getWeight() {
		return weight;
	}
	public void setWeight(Integer weight) {
		this.weight = weight;
	}
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public ItemStack getMenuItem() {
		return menuItem;
	}
	public void setMenuItem(ItemStack menuItem) {
		this.menuItem = menuItem;
	}
	public List<String> getEffects() {
		return effects;
	}
	public void setEffects(List<String> effects) {
		this.effects = effects;
	}
	public List<String> getInputs() {
		return inputs;
	}
	public void setInputs(List<String> inputs) {
		this.inputs = inputs;
	}
	public String getPrerequisite() {
		return prerequisite;
	}
	public void setPrerequisite(String prerequisite) {
		this.prerequisite = prerequisite;
	}
	public ProductionMethod(String key, ConfigurationSection config) {
		this.id = key;
		this.weight = config.getInt("weight");
		if(config.contains("effects")) {
			this.effects = config.getStringList("effects");
		}
		if(config.contains("cost")) {
			this.inputs = config.getStringList("cost");
		}
		if(config.contains("prerequisite")) {
			this.prerequisite = config.getString("prerequisite");
		} else {
			this.prerequisite = "none";
		}
		ItemCreator ic = new ItemCreator();
		this.menuItem = ic.createMenuItem(config.getConfigurationSection("item"), this.effects, this.inputs, this.prerequisite);
	}
	public ProductionMethod(ProductionMethod another) {
		this.id = another.id;
		this.weight = another.weight;
		this.effects = another.effects;
		this.inputs = another.inputs;
		this.prerequisite = another.prerequisite;
		this.menuItem = another.menuItem;
	}
}
