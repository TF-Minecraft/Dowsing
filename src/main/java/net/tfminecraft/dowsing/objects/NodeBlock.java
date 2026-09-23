package net.tfminecraft.dowsing.objects;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;

import net.tfminecraft.dowsing.loaders.TypeLoader;

public class NodeBlock {
	String id;
	String resource;
	String block;
	List<NodeType> types = new ArrayList<NodeType>();

	private boolean breakable = true;
	private boolean transferable = true;
	private boolean special = false;
	private String title;

	private int tier;

	public String getResource() {
		return resource;
	}
	public void setResource(String resource) {
		this.resource = resource;
	}
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getBlock() {
		return block;
	}
	public void setBlock(String block) {
		this.block = block;
	}
	public List<NodeType> getTypes() {
		return types;
	}
	public void setTypes(List<NodeType> types) {
		this.types = types;
	}
	public boolean isBreakable() {
		return breakable;
	}
	public boolean isTransferable(){
		return transferable;
	}
	public boolean isSpecial() {
		return special;
	}
	public int getTier(){
		return tier;
	}
	public boolean hasTitle() {
		return title != null;
	}
	public String getTitle() {
		return title;
	}
	public NodeBlock(String key, ConfigurationSection config) {
		this.id = key;
		this.block = config.getString("block");
		this.resource = config.getString("resource");
		List<NodeType> l = new ArrayList<NodeType>();
		for(String s : config.getStringList("main_types")) {
			l.add(TypeLoader.getByString(s));
		}
		this.types = l;
		breakable = config.getBoolean("breakable", true);
		transferable = config.getBoolean("transferable", true);
		special = config.getBoolean("special", false);
		tier = config.getInt("tier", 0);
		title = config.getString("title", null);
	}
	public NodeBlock(NodeBlock another) {
		this.id = another.id;
		this.block = another.block;
		this.resource = another.resource;
		for(NodeType t : another.types) {
			this.types.add(new NodeType(t));
		}
		this.breakable = another.isBreakable();
		this.transferable = another.isTransferable();
		this.special = another.isSpecial();
		this.tier = another.getTier();
		if(another.hasTitle()) this.title = another.getTitle();
	}
}
