package net.tfminecraft.dowsing.utils;

import java.util.HashMap;

import net.tfminecraft.dowsing.loaders.PMLoader;
import net.tfminecraft.dowsing.objects.Node;
import net.tfminecraft.dowsing.objects.NodeSlot;

public class NodeReloader {
	private HashMap<String, String> pmMap = new HashMap<>();
	
	public void cache(Node n) {
		for(NodeSlot slot : n.getCurrentType().getSlots()) {
			pmMap.put(slot.getId(), slot.getActivePm().getId());
		}
	}
	public void reload(Node n) {
		for(NodeSlot slot : n.getCurrentType().getSlots()) {
			if(pmMap.containsKey(slot.getId())){
				slot.setActivePm(PMLoader.getByString(pmMap.get(slot.getId())));
			}
		}
	}
}
