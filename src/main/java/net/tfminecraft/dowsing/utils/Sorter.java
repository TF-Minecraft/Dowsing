package net.tfminecraft.dowsing.utils;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.bukkit.Bukkit;

import net.tfminecraft.dowsing.objects.ProductionMethod;

public class Sorter {
	public List<ProductionMethod> sortPMsByWeight(List<ProductionMethod> list){
		Collections.sort(list, new Comparator<ProductionMethod>() {
		    @Override
		    public int compare(ProductionMethod f1, ProductionMethod f2) {
		    	if(f1.getWeight() == null) {
		    		Bukkit.getLogger().info("[Dowsing] "+f1.getId()+" has no weight");
		    		return 0;
		    	}
		    	if(f2.getWeight() == null) {
		    		Bukkit.getLogger().info("[Dowsing] "+f1.getId()+" has no weight");
		    		return 0;
		    	}
		        return Double.compare(f1.getWeight(), f2.getWeight());
		    }
		});
		return list;
	}
}
