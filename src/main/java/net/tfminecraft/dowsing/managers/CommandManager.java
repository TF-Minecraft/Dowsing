package net.tfminecraft.dowsing.managers;

import java.io.IOException;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import net.tfminecraft.dowsing.DowsingMain;
import net.tfminecraft.dowsing.utils.Database;
import net.tfminecraft.dowsing.utils.Permissions;


public class CommandManager implements Listener, CommandExecutor{
	public String cmd1 = "dowsing";
	Database db = new Database();
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if(cmd.getName().equalsIgnoreCase(cmd1)) {
			if(Permissions.isAdmin(sender) == false) {
				sender.sendMessage("§cYou do not have access to this command!");
				return false;
			}
            if (args.length == 0) {
                sender.sendMessage("§eUsage: /dowsing <reload|createresource|deleteresource>");
                return false;
            }
			if(args[0].equalsIgnoreCase("reload")) {
				if(sender instanceof Player) {
					Player p = (Player) sender;
					JavaPlugin.getPlugin(DowsingMain.class).reloadConfigPCommand(p);
				} else {
					JavaPlugin.getPlugin(DowsingMain.class).reloadConfigCommand();
				}
			}
			if(sender instanceof Player) {
				Player p = (Player) sender;
				if(args[0].equalsIgnoreCase("createresource")) {
                    if (args.length < 4) {
                        sender.sendMessage("§eUsage: /dowsing createresource <id> <material> <yield>");
                        return false;
                    }
					String id = args[1];
					String line = args[2]+"."+args[3];
					try {
						db.saveResource(id, p.getLocation().getChunk().toString(), line);
						p.sendMessage("§aResource saved");
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				} else if(args[0].equalsIgnoreCase("deleteresource")) {
                    if (args.length < 2) {
                        sender.sendMessage("§eUsage: /dowsing deleteresource <id>");
                        return false;
                    }
					String id = args[1];
					db.removeResource(id);
					p.sendMessage("§eResource deleted");
				}
			}
		}
		return false;
	}

}
