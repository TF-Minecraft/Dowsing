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

import net.tfminecraft.dowsing.objects.ProductionMethod;

@SuppressWarnings("deprecation") // Menu identifiers are stored as legacy ItemMeta display-name strings.
public class PMLoader {
	static List<ProductionMethod> pms = new ArrayList<ProductionMethod>();
	public static void clear() {
		pms.clear();
	}
	public static List<ProductionMethod> getPMs(){
		return pms;
	}
	public static ProductionMethod getByString(String id) {
		for(ProductionMethod pm : pms) {
			if(pm.getId().equalsIgnoreCase(id)) return new ProductionMethod(pm);
		}
		return null;
	}
	public static ProductionMethod getByItemName(String id) {
		for(ProductionMethod pm : pms) {
			if(pm.getMenuItem() == null) {
				Bukkit.getLogger().info("[Dowsing] "+pm.getId()+" has no menu item");
				continue;
			}
			if(pm.getMenuItem().getItemMeta().getDisplayName().equalsIgnoreCase(id)) return new ProductionMethod(pm);
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
				ProductionMethod pm = new ProductionMethod(key, config.getConfigurationSection(key));
				pms.add(pm);
			} catch (Exception e) {
				Bukkit.getLogger().warning("[Dowsing] Failed to load production method '" + key + "': " + e.getMessage());
			}
		}
	}
}
