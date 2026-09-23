package net.tfminecraft.dowsing.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class TabCompletion implements TabCompleter{
    @Override
    public List<String> onTabComplete (CommandSender sender, Command cmd, String label, String[] args){
    	if(Permissions.isAdmin(sender)) {
	        if(cmd.getName().equalsIgnoreCase("dowsing") && args.length >= 0 && args.length < 2 && !(args[0].equalsIgnoreCase("reload") || args[0].equalsIgnoreCase("createresource") || args[0].equalsIgnoreCase("deleteresource"))){
	            if(sender instanceof Player){
	                List<String> completions = new ArrayList<>();
	                
	                completions.add("createresource");
	                completions.add("deleteresource");
	                completions.add("reload");
	                return completions;
	            }
	        } else if(cmd.getName().equalsIgnoreCase("dowsing") && args.length == 2 && args[0].equalsIgnoreCase("createresource")){
	            if(sender instanceof Player){
	            	List<String> completions = new ArrayList<String>();
	            	completions.add("<id>");
	                
	                return completions;
	            }
	        } else if(cmd.getName().equalsIgnoreCase("dowsing") && args.length == 3 && args[0].equalsIgnoreCase("createresource")){
	            if(sender instanceof Player){
	            	List<String> completions = new ArrayList<String>();
	            	completions.add("<material>");
	                
	                return completions;
	            }
	        } else if(cmd.getName().equalsIgnoreCase("dowsing") && args.length == 4 && args[0].equalsIgnoreCase("createresource")){
	            if(sender instanceof Player){
	            	List<String> completions = new ArrayList<String>();
	            	completions.add("1");
	                completions.add("2");
	                completions.add("3");
	                completions.add("4");
	                completions.add("5");
	                completions.add("6");
	                completions.add("7");
	                completions.add("8");
	                
	                return completions;
	            }
	        }else if(cmd.getName().equalsIgnoreCase("dowsing") && args.length == 2 && args[0].equalsIgnoreCase("deleteresource")){
	            if(sender instanceof Player){
	            	List<String> completions = new ArrayList<String>();
	            	File folder = new File("plugins/Dowsing/Resources");
	            	for (final File file : folder.listFiles()) {
	                    if (!file.isDirectory()) {
	                    	String id = new String(file.getName());
	                    	id = id.replace(".txt", "");
	                    	completions.add(id);
	                    }
	            	}
	                
	                return completions;
	            }
	        }
	    }
    	return null;
    }
}
