package com.arodsg.spawnsinglemob;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin implements Listener, TabCompleter {
	
	Map<String, EntityType> mobMap = new HashMap<String, EntityType>();
	{
		mobMap.put("blaze", EntityType.BLAZE);
		mobMap.put("creeper", EntityType.CREEPER);
		mobMap.put("drowned", EntityType.DROWNED);
		mobMap.put("elderguardian", EntityType.ELDER_GUARDIAN);
		mobMap.put("enderdragon", EntityType.ENDER_DRAGON);
		mobMap.put("evoker", EntityType.EVOKER);
		mobMap.put("ghast", EntityType.GHAST);
		mobMap.put("guardian", EntityType.GUARDIAN);
		mobMap.put("hoglin", EntityType.HOGLIN);
		mobMap.put("husk", EntityType.HUSK);
		mobMap.put("magmacube", EntityType.MAGMA_CUBE);
		mobMap.put("phantom", EntityType.PHANTOM);
		mobMap.put("piglin", EntityType.PIGLIN);
		mobMap.put("piglinbrute", EntityType.PIGLIN_BRUTE);
		mobMap.put("pillager", EntityType.PILLAGER);
		mobMap.put("ravager", EntityType.RAVAGER);
		mobMap.put("shulker", EntityType.SHULKER);
		mobMap.put("silverfish", EntityType.SILVERFISH);
		mobMap.put("skeleton", EntityType.SKELETON);
		mobMap.put("skeletonhorse", EntityType.SKELETON_HORSE);
		mobMap.put("slime", EntityType.SLIME);
		mobMap.put("spider", EntityType.SPIDER);
		mobMap.put("stray", EntityType.STRAY);
		mobMap.put("vindicator", EntityType.VINDICATOR);
		mobMap.put("witch", EntityType.WITCH);
		mobMap.put("wither", EntityType.WITHER);
		mobMap.put("witherskeleton", EntityType.WITHER_SKELETON);
		mobMap.put("zoglin", EntityType.ZOGLIN);
		mobMap.put("zombie", EntityType.ZOMBIE);
		mobMap.put("zombievillager", EntityType.ZOMBIE_VILLAGER);
	}
	
	EntityType selectedEntityType;
	Boolean isEnabled = false;
	
    @Override
    public void onEnable() {
//    	saveDefaultConfig();
    	getServer().getPluginManager().registerEvents(this, this);
    }
    
    @Override
    public void onDisable() {
    	
    }
    
    @EventHandler
    public void onOverworldMobSpawn(CreatureSpawnEvent event) {
    	LivingEntity eventEntity = event.getEntity();
    	
    	if(isEnabled && eventEntity != null && event.getSpawnReason() == SpawnReason.NATURAL && eventEntity instanceof Monster) { //if enabled, and the entity is not null, and the creature spawned naturally, and the creature is a monster entity
    		World mobWorld = event.getEntity().getWorld();
	    	
	    	if(mobWorld.getEnvironment() == World.Environment.NORMAL) { //if the mob's world is a normal world (not the Nether or End), spawn a Skeleton in its place
	    		spawnMob(mobWorld, eventEntity.getLocation());
	    		eventEntity.remove();
	    	}
    	}
    }
    
    public void spawnMob(World world, Location location) { //spawns the specified mob in the given world at the given location
    	world.spawnEntity(location, selectedEntityType);
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
		return null;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    	String commandName = command.getName();
    	
    	if ((commandName.equalsIgnoreCase("spawnsinglemob") || commandName.equalsIgnoreCase("ssm")) && args.length > 0) {
    		if(args.length > 0) {
	    		switch(args[0]) {
	    			case "enable":
	    				if(args.length == 2) {
	    					String mobName = args[1];
	    					
	    					if(mobMap.containsKey(mobName)) {
	    						selectedEntityType = mobMap.get(mobName);
	    						isEnabled = true;
	    					}
	    					else {
	    						//error, invalid mob name
	    					}
	    				}
	    				else {
	    					//error, invalid number of arguments
	    				}
	    				break;
	    			case "disable":
	    				selectedEntityType = null;
	    				isEnabled = false;
	    				break;
	    		}
    		}
    		else {
    			//error, invalid number of arguments, no arguments provided
    		}
    	}
    	
        return false;
    }
}