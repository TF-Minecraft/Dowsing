package net.tfminecraft.dowsing.objects;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;

import net.tfminecraft.dowsing.loaders.PMLoader;
import net.tfminecraft.dowsing.utils.Sorter;

public class NodeSlot {
	String id;
	Integer slot;
	ProductionMethod activePm;
	List<ProductionMethod> pms = new ArrayList<ProductionMethod>();
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public Integer getSlot() {
		return slot;
	}
	public void setSlot(Integer slot) {
		this.slot = slot;
	}
	public ProductionMethod getActivePm() {
		return activePm;
	}
	public void setActivePm(ProductionMethod activePm) {
		this.activePm = activePm;
	}
	public List<ProductionMethod> getPms() {
		return pms;
	}
	public void setPms(List<ProductionMethod> pms) {
		this.pms = pms;
	}
	public NodeSlot(String key, ConfigurationSection config) {
		this.id = key;
		this.slot = config.getInt("slot");
		
		List<ProductionMethod> pmUnsorted = new ArrayList<ProductionMethod>();
		for(String s : config.getStringList("production_methods")) {
			pmUnsorted.add(PMLoader.getByString(s));
		}
		Sorter sort = new Sorter();
		this.pms = sort.sortPMsByWeight(pmUnsorted);
		this.activePm = this.pms.get(0);
	}
	public NodeSlot(NodeSlot another) {
		this.id = another.id;
		this.slot = another.slot;
		for(ProductionMethod p : another.pms) {
			this.pms.add(new ProductionMethod(p));
		}
		this.activePm = this.pms.get(0);
	}
}
