package net.tfminecraft.dowsing.loaders;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.dowsing.objects.NodeBlock;

public class BlockLoader {
	static List<NodeBlock> oList = new ArrayList<NodeBlock>();
	
	public static List<NodeBlock> getNodeBlocks() {
		return oList;
	}
	public static void clear() {
		oList.clear();
	}
	public static NodeBlock getByString(String id) {
		for(NodeBlock o : oList) {
			if(o.getId().equalsIgnoreCase(id)) return new NodeBlock(o);
		}
		return null;
	}
	public static NodeBlock getByBlock(Material m) {
		for(NodeBlock b : oList) {
			String plugin = b.getBlock().split("\\.")[0];
			if(!plugin.equalsIgnoreCase("v")) continue;
			String type = b.getBlock().split("\\.")[1];
			if(m.equals(Material.valueOf(type.toUpperCase()))) return new NodeBlock(b);
		}
		return null;
	}
	public static NodeBlock getByPath(String path) {
		for(NodeBlock b : oList) {
			String plugin = b.getBlock().split("\\.")[0];
			if(!plugin.equalsIgnoreCase("ia")) continue;
			String blockpath = b.getBlock().split("\\.")[1];
			if(blockpath.equalsIgnoreCase(path)) return new NodeBlock(b);
		}
		return null;
	}
	public void loadConfig(File configFile) {
		FileConfiguration config = new YamlConfiguration();
        try {
        	config.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
        Set<String> set = config.getKeys(false);

		List<String> list = new ArrayList<String>(set);
		
		for(String key : list) {
			NodeBlock o = new NodeBlock(key, config.getConfigurationSection(key));
			oList.add(o);
		}
	}
}
