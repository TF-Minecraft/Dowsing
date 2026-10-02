package net.tfminecraft.dowsing.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.tfminecraft.dowsing.DowsingMain;
import net.tfminecraft.dowsing.loaders.BlockLoader;
import net.tfminecraft.dowsing.loaders.PMLoader;
import net.tfminecraft.dowsing.loaders.TypeLoader;
import net.tfminecraft.dowsing.managers.NodeManager;
import net.tfminecraft.dowsing.objects.Node;
import net.tfminecraft.dowsing.objects.NodeBlock;
import net.tfminecraft.dowsing.objects.NodeSlot;
import net.tfminecraft.dowsing.objects.NodeType;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;

@SuppressWarnings("deprecation") // Stored configuration text uses Bukkit legacy color codes.
public class Database {
    private JSONObject json = new JSONObject(); // org.json.simple
    JSONParser parser = new JSONParser();
    private File[] listFiles(File directory) {
        File[] files = directory.listFiles();
        return files == null ? new File[0] : files;
    }
	public String getResource(Chunk c) throws IOException {
        File folder = new File(DowsingMain.plugin.getDataFolder(), "Resources");
        for (File file : listFiles(folder)) {
            if (file.isDirectory()) continue;
            try (BufferedReader reader = java.nio.file.Files.newBufferedReader(file.toPath())) {
                if (c.toString().equalsIgnoreCase(reader.readLine())) {
                    String resource = reader.readLine();
                    if (resource != null && !resource.isBlank()) return resource;
	                	}
            } catch (FileNotFoundException | java.nio.file.NoSuchFileException e) {
                  DowsingMain.plugin.getLogger().log(Level.WARNING, "Could not read resource file " + file, e);
            }
    	}
		return null;
	}
	public Boolean hasResource(Chunk c) throws IOException {
        return getResource(c) != null;
	}
	public Boolean removeResource(String id){
        File folder = new File(DowsingMain.plugin.getDataFolder(), "Resources");
        for (final File file : listFiles(folder)) {
            if (!file.isDirectory()) {
            	if(file.getName().equalsIgnoreCase(id+".txt")) {
                    return file.delete();
            	}
            }
    	}
		return false;
	}
	public void saveResource(String id, String c, String r) throws IOException {
        File folder = new File(DowsingMain.plugin.getDataFolder(), "Resources");
        java.nio.file.Files.createDirectories(folder.toPath());
        File file = new File(folder, id + ".txt");
        try (BufferedWriter writer = java.nio.file.Files.newBufferedWriter(file.toPath())) {
            writer.write(c); writer.newLine(); writer.write(r); writer.newLine();
		}
	}
	public void loadNodes() {
        File folder = new File(DowsingMain.plugin.getDataFolder(), "Nodes");
        for (final File file : listFiles(folder)) {
            if (!file.isDirectory()) {
                try (var reader = java.nio.file.Files.newBufferedReader(file.toPath())) {
                    json = (JSONObject) parser.parse(reader);
                    Location loc = new Location(Bukkit.getServer().getWorld((String) json.get("world")), ((Number) json.get("xPos")).doubleValue(),((Number) json.get("yPos")).doubleValue(),((Number) json.get("zPos")).doubleValue());
    				UUID id = UUID.fromString((String) json.get("id"));
    				NodeBlock b = BlockLoader.getByString((String) json.get("block"));
    				if(b == null) continue;
    				Guild g = resolveOwner(json);
    				Boolean isActive = Boolean.parseBoolean((String) json.get("active"));
                    int level = (int) Math.round(((Number) json.get("level")).doubleValue());
                    int cycleTime = (int) Math.round(((Number) json.get("cycle time")).doubleValue());
                    int timeLeft = (int) Math.round(((Number) json.get("time remaining")).doubleValue());
                    int inputCounter = (int) Math.round(((Number) json.get("input counter")).doubleValue());
    				NodeType currentType = TypeLoader.getByString((String) json.get("current type"));
                    double efficiency = json.containsKey("efficiency") ? ((Number) json.get("efficiency")).doubleValue() : 50;
    				if(currentType == null) continue;
    				List<String> activePMs = new ArrayList<String>();
    				int i = 0;
    				JSONArray pmArray = (JSONArray) json.get("active pms");
    				while(i < pmArray.size()) {
    					activePMs.add(pmArray.get(i).toString());
    					i++;
    				}
    				setSlots(currentType, activePMs);
    				Node n = new Node(id, b, loc, g, isActive, level, cycleTime, currentType, timeLeft, inputCounter, efficiency);
    				if(json.containsKey(LastCycleResult.KEY)) {
    					n.setLastResult(LastCycleResult.fromJson(json.get(LastCycleResult.KEY)));
    				}
    				NodeManager.nodes.add(n);
    			} catch (Exception ex) {
    				ex.printStackTrace();
    			}
            }
        }
	}
	private void setSlots(NodeType t, List<String> pms) {
		for(String pm : pms) {
			String type = pm.split("\\.")[0];
			for(NodeSlot slot : t.getSlots()) {
				if(slot.getId().equalsIgnoreCase(type)) {
					slot.setActivePm(PMLoader.getByString(pm.split("\\.")[1]));
					DowsingMain.plugin.getLogger().info(slot.getActivePm().getId());
				}
			}
		}
	}

	Guild resolveOwner(JSONObject json) {
		if(json.containsKey("guild")) {
			String gid = String.valueOf(json.get("guild"));
			if(!gid.equalsIgnoreCase("none")) {
				return FactionManager.getGuildByString(gid);
			}
			return null;
		}
		if(json.containsKey("faction")) {
			String fid = String.valueOf(json.get("faction"));
			if(!fid.equalsIgnoreCase("none")) {
				Faction f = FactionManager.getByString(fid);
				if(f != null) {
					return f.getOrCreateMainGuild();
				}
			}
		}
		return null;
	}

	public void loadGuildCapacity() {
        File file = new File(DowsingMain.plugin.getDataFolder(), "guild_capacity.json");
		if(!file.exists()) {
			return;
		}
        try (var reader = java.nio.file.Files.newBufferedReader(file.toPath())) {
            JSONObject data = (JSONObject) parser.parse(reader);
			for(Object key : data.keySet()) {
				Object value = data.get(key);
				int extra = 0;
				if(value instanceof Number) {
					extra = ((Number) value).intValue();
				} else if(value != null) {
					extra = Integer.parseInt(value.toString());
				}
				NodeManager.extraCapacityByGuild.put(key.toString(), extra);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public void saveGuildCapacity() {
		try {
            File file = new File(DowsingMain.plugin.getDataFolder(), "guild_capacity.json");
            java.nio.file.Files.createDirectories(file.getParentFile().toPath());
			file.createNewFile();
			HashMap<String, Object> defaults = new HashMap<String, Object>();
			for(String key : NodeManager.extraCapacityByGuild.keySet()) {
				defaults.put(key, NodeManager.extraCapacityByGuild.get(key));
			}
			json = new JSONObject();
			save(file, defaults);
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
	}

	public void deleteDatabase() {
        File folder = new File(DowsingMain.plugin.getDataFolder(), "Nodes");
        for (final File file : listFiles(folder)) {
            if (!file.isDirectory()) {
            	file.delete();
            }
    	}
    }
	@SuppressWarnings("unchecked")
	public void saveNode(Node n) {
		try {
            File file = new File(new File(DowsingMain.plugin.getDataFolder(), "Nodes"),n.getId()+".json");
            java.nio.file.Files.createDirectories(file.getParentFile().toPath());
            HashMap<String, Object> defaults = new HashMap<String, Object>();
            json = new JSONObject();
        	defaults.put("id", n.getId().toString());
            defaults.put("world", n.getLoc().getWorld().getName());
        	defaults.put("xPos", n.getLoc().getX());
        	defaults.put("yPos", n.getLoc().getY());
        	defaults.put("zPos", n.getLoc().getZ());
        	defaults.put("block", n.getBlock().getId());
        	if(n.hasGuild()) {
        		defaults.put("guild", n.getGuild().getId());
        	} else {
        		defaults.put("guild", "none");
        	}
        	defaults.put("active", n.getIsActive().toString());
        	defaults.put("level", n.getLevel());
        	defaults.put("cycle time", n.getCycleTime());
        	defaults.put("time remaining", n.getTimeLeft());
        	defaults.put("input counter", n.getInputCounter());
        	defaults.put("current type", n.getCurrentType().getId());
        	int i = 0;
        	JSONArray pmArray = new JSONArray();
        	while(i < n.getCurrentType().getSlots().size()) {
        		pmArray.add(n.getCurrentType().getSlots().get(i).getId()+"."+n.getCurrentType().getSlots().get(i).getActivePm().getId());
        		i++;
        	}
        	defaults.put("active pms", pmArray);
			defaults.put("efficiency", n.getEfficiency());
			defaults.put(LastCycleResult.KEY, LastCycleResult.toJson(n.getLastResult()));
        	save(file, defaults);
        } catch (Throwable ex) {
			ex.printStackTrace();
        }
	}
	@SuppressWarnings("unchecked")
	public boolean save(File file, HashMap<String, Object> defaults) {
	  try {
		  JSONObject toSave = new JSONObject();
	  
	    for (String s : defaults.keySet()) {
	      Object o = defaults.get(s);
	      if (o instanceof String) {
	        toSave.put(s, getString(s, defaults));
	      } else if (o instanceof Double) {
	        toSave.put(s, getDouble(s, defaults));
	      } else if (o instanceof Integer) {
	        toSave.put(s, getInteger(s, defaults));
	      } else if (o instanceof JSONObject) {
	        toSave.put(s, getObject(s, defaults));
	      } else if (o instanceof JSONArray) {
	        toSave.put(s, getArray(s, defaults));
	      }
	    }
	  
	    TreeMap<String, Object> treeMap = new TreeMap<String, Object>(String.CASE_INSENSITIVE_ORDER);
	    treeMap.putAll(toSave);
	  
	   Gson g = new GsonBuilder().setPrettyPrinting().create();
	   String prettyJsonString = g.toJson(treeMap);
	  
	    FileWriter fw = new FileWriter(file);
	    fw.write(prettyJsonString);
	    fw.flush();
	    fw.close();
	  
	    return true;
	  } catch (Exception ex) {
	    ex.printStackTrace();
	    return false;
	  }
	}
	
	public String getRawData(String key, HashMap<String, Object> defaults) {
	    return json.containsKey(key) ? json.get(key).toString()
	       : (defaults.containsKey(key) ? defaults.get(key).toString() : key);
	  }
	
	  public String getString(String key, HashMap<String, Object> defaults) {
	    return ChatColor.translateAlternateColorCodes('&', getRawData(key, defaults));
	  }
	
	  public boolean getBoolean(String key, HashMap<String, Object> defaults) {
	    return Boolean.valueOf(getRawData(key, defaults));
	  }
	
	  public double getDouble(String key, HashMap<String, Object> defaults) {
	    try {
	      return Double.parseDouble(getRawData(key, defaults));
	    } catch (Exception ex) { }
	    return -1;
	  }
	
	  public double getInteger(String key, HashMap<String, Object> defaults) {
	    try {
	      return Integer.parseInt(getRawData(key, defaults));
	    } catch (Exception ex) { }
	    return -1;
	  }
	 
	  public JSONObject getObject(String key, HashMap<String, Object> defaults) {
	     return json.containsKey(key) ? (JSONObject) json.get(key)
	       : (defaults.containsKey(key) ? (JSONObject) defaults.get(key) : new JSONObject());
	  }
	 
	  public JSONArray getArray(String key, HashMap<String, Object> defaults) {
		     return json.containsKey(key) ? (JSONArray) json.get(key)
		       : (defaults.containsKey(key) ? (JSONArray) defaults.get(key) : new JSONArray());
	  }
}
