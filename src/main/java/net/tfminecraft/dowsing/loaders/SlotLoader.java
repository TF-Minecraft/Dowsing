package net.tfminecraft.dowsing.loaders;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.dowsing.objects.NodeSlot;

public class SlotLoader {
	static List<NodeSlot> oList = new ArrayList<NodeSlot>();
	public static void clear() {
		oList.clear();
	}
	public static List<NodeSlot> getNodeSlots() {
		return oList;
	}
	public static NodeSlot getByString(String id) {
		for(NodeSlot o : oList) {
			if(o.getId().equalsIgnoreCase(id)) return new NodeSlot(o);
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
			NodeSlot o = new NodeSlot(key, config.getConfigurationSection(key));
			oList.add(o);
		}
	}
}
