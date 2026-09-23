package net.tfminecraft.dowsing.managers;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import dev.lone.itemsadder.api.CustomFurniture;
import io.lumine.mythic.lib.api.item.NBTItem;
import net.tfminecraft.dowsing.Cache;
import net.tfminecraft.dowsing.utils.Database;

@SuppressWarnings("deprecation") // Preserve the plugin's legacy section-color output.
public class ResourceManager implements Listener{
	Database db = new Database();
	public HashMap<Player, Long> cooldown = new HashMap<>();
	@EventHandler
	public void dowsingEvent(PlayerInteractEvent e) throws NumberFormatException, IOException {
		if(!(e.getAction().equals(Action.RIGHT_CLICK_AIR) || e.getAction().equals(Action.RIGHT_CLICK_BLOCK))) return;
		Player p = e.getPlayer();
		if(cooldown.containsKey(p)) {
			if(cooldown.get(p) > System.currentTimeMillis()) {
				return;
			}
		}
		cooldown.put(p, System.currentTimeMillis() + (100));
		ItemStack item = p.getInventory().getItemInMainHand();
		NBTItem nbt = NBTItem.get(item);
		if(nbt.hasType() == false) return;
		String dowsingType = Cache.dowsingStick.split("\\.")[0];
		String dowsingId = Cache.dowsingStick.split("\\.")[1];
		if(!(nbt.getType().equalsIgnoreCase(dowsingType) && nbt.getString("MMOITEMS_ITEM_ID").equalsIgnoreCase(dowsingId))) return;
		if(!Cache.naturalYieldEnabled) {
			p.sendMessage("§cNatural yields are disabled");
			return;
		}
		if(db.hasResource(p.getLocation().getChunk())) {
			String resource = db.getResource(p.getLocation().getChunk()).split("\\.")[0];
			Integer yield = Integer.parseInt(db.getResource(p.getLocation().getChunk()).split("\\.")[1]);
			String color = "DARK_RED";
			if(yield > 1) color = "RED";
			if(yield > 3) color = "YELLOW";
			if(yield > 5) color = "GREEN";
			if(yield > 7) color = "DARK_GREEN";
			p.sendMessage("§fThis chunk has resources of the type: §e"+resource.replace("_", " ")+" §fin it, with a yield of "+ChatColor.valueOf(color) + yield);
		} else {
			p.sendMessage("§7Nothing found here");
		}
	}
	public Boolean clickedIsFurniture( Block b, String station) {
		List<Entity> nearbyEntities = (List<Entity>) b.getWorld().getNearbyEntities(b.getLocation(), 0.2, 0.2, 0.2);
		for(Entity a : b.getWorld().getEntities()){
            if(nearbyEntities.contains(a)){
            	CustomFurniture f = CustomFurniture.byAlreadySpawned(a);
                if(f != null) {
                	if((f.getNamespace()+":"+f.getId()).equalsIgnoreCase(station)) {
                		return true;
                	}
                }
            }
        }
		return false;
	}
}
