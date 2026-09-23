package net.tfminecraft.dowsing.loaders;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.dowsing.objects.NodeType;

@SuppressWarnings("deprecation") // Menu identifiers are stored as legacy ItemMeta display-name strings.
public class TypeLoader {
	static List<NodeType> oList = new ArrayList<NodeType>();
	public static void clear() {
		oList.clear();
	}
	public static List<NodeType> getNodeTypes() {
		return oList;
	}
	public static NodeType getByString(String id) {
		for(NodeType o : oList) {
			if(o.getId().equalsIgnoreCase(id)) return new NodeType(o);
		}
		return null;
	}
	public static NodeType getByItemName(String id) {
		for(NodeType t : oList) {
			if(t.getMenuItem().getItemMeta().getDisplayName().equalsIgnoreCase(id)) return new NodeType(t);
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
			try {
				NodeType o = new NodeType(key, config.getConfigurationSection(key));
				oList.add(o);
			} catch (Exception e) {
				Bukkit.getLogger().warning("[Dowsing] Failed to load node type '" + key + "': " + e.getMessage());
			}
		}
	}
}
