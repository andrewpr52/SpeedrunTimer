package com.arodsg.speedruntimer;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EnderDragon;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin implements Listener, TabCompleter {
	
	Boolean isRunning = false;
	Timer timer;
	
	long lastStartTimeMS = 0;
	long totalDurationMS = 0;
	
	long runningSeconds = 0;
	
    @Override
    public void onEnable() {
//    	saveDefaultConfig();
    	getServer().getPluginManager().registerEvents(this, this);
    }
    
    @Override
    public void onDisable() {
    	
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
		return null;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    	String commandName = command.getName();
    	
    	if ((commandName.equalsIgnoreCase("speedruntimer") || commandName.equalsIgnoreCase("srt") || commandName.equalsIgnoreCase("timer")) && args.length > 0) {
    		if(args.length > 0) {
	    		switch(args[0]) {
	    			case "start":
	    				if(!isRunning) {
		    				isRunning = true;
		    				
		    				timer = new Timer();
		    			    timer.scheduleAtFixedRate(new TimerTask() {
	    			            public void run() {
	    			            	runningSeconds++;
	    			            	
	    			            	if(runningSeconds % 900 == 0) { // every 15 minutes
	    			            		broadcastRunningSeconds("Duration:");
	    			            	}
	    			            }
	    			        }, 1000, 1000);
		    			    
		    			    sender.sendMessage("Timer started!");
		    			    return true;
	    				}
	    				else { // if already running
	    					sender.sendMessage("Error: timer is already running.");
	    				}
	    				break;
	    			case "stop":
	    				stopTimer(sender);
	    				return true;
	    			case "reset":
	    				resetTimer();
	    				
	    				sender.sendMessage("Timer has been reset!");
	    				return true;
	    			case "time":
	    				sender.sendMessage("Duration: " + convertSecondsToString(runningSeconds));
	    				return true;
	    			case "add":
	    				if(args.length == 3) {
	    					int numToAdd;
	    					
	    					try {
	    						numToAdd = Integer.parseInt(args[2]);
	    					}
	    					catch(NumberFormatException e) {
	    						sender.sendMessage("Error: invalid argument \"" + args[2] + "\".");
	    						break;
	    					}
	    					
		    				switch(args[1]) {
		    					case "hours":
		    						addSeconds(numToAdd * 3600);
		    						return true;
		    					case "minutes":
		    						addSeconds(numToAdd * 60);
		    						return true;
		    					case "seconds":
		    						addSeconds(numToAdd);
		    						return true;
		    					default:
		    						sender.sendMessage("Error: invalid argument.");
		    						break;
		    				}
	    				}
	    				else {
	    					sender.sendMessage("Error: invalid number of arguments.");
	    				}
	    				
	    				break;
	    			case "subtract":
	    				if(args.length == 3) {
	    					int numToSubtract;
	    					
	    					try {
	    						numToSubtract = Integer.parseInt(args[2]);
	    					}
	    					catch(NumberFormatException e) {
	    						sender.sendMessage("Error: invalid argument \"" + args[2] + "\".");
	    						break;
	    					}
	    					
		    				switch(args[1]) {
		    					case "hours":
		    						subtractSeconds(numToSubtract * 3600);
		    						return true;
		    					case "minutes":
		    						subtractSeconds(numToSubtract * 60);
		    						return true;
		    					case "seconds":
		    						subtractSeconds(numToSubtract);
		    						return true;
		    					default:
		    						sender.sendMessage("Error: invalid argument.");
		    						break;
		    				}
	    				}
	    				else {
	    					sender.sendMessage("Error: invalid number of arguments.");
	    				}
	    				
	    				break;
	    			default:
	    				sender.sendMessage("Error: invalid argument.");
	    		}
    		}
    		else {
    			sender.sendMessage("Error: no arguments provided.");
    		}
    	}
    	
        return false;
    }
    
    @EventHandler
    public void onEnderDragonDeath(EntityDeathEvent e) { // stop timer and broadcast time when the dragon is killed
         if(e.getEntity() instanceof EnderDragon){
             stopTimer(null);
        }
    }
    
    private void addSeconds(long seconds) {
    	runningSeconds += seconds;
    	broadcastRunningSeconds("Time set to");
    }
    
    private void subtractSeconds(long seconds) {
    	runningSeconds -= seconds;
		runningSeconds = runningSeconds >= 0 ? runningSeconds : 0;
		broadcastRunningSeconds("Time set to");
    }
    
    private void stopTimer(CommandSender sender) {
    	Boolean calledFromCommand = (sender != null);
    	
    	if(isRunning) { // if running, then stop
			isRunning = false;
			timer.cancel();
			
			Bukkit.getServer().broadcastMessage("Timer stopped!");
			broadcastRunningSeconds("Duration:");
		}
		else if(calledFromCommand) { // if not running, and function was called from a command
			sender.sendMessage("Error: no active timer running.");
		}
    }
    
    private void resetTimer() {
    	isRunning = false;
    	timer.cancel();
    	runningSeconds = 0;
    }
    
    private void broadcastRunningSeconds(String prefix) {
    	String runningSecondsString = convertSecondsToString(runningSeconds);
    	Bukkit.getServer().broadcastMessage(prefix + " " + runningSecondsString);
    }
    
    private String convertSecondsToString(long seconds) {
    	return String.format("%02d:%02d:%02d",
			TimeUnit.SECONDS.toHours(seconds),
			TimeUnit.SECONDS.toMinutes(seconds) -
			TimeUnit.HOURS.toMinutes(TimeUnit.SECONDS.toHours(seconds)),
			TimeUnit.SECONDS.toSeconds(seconds) -
			TimeUnit.MINUTES.toSeconds(TimeUnit.SECONDS.toMinutes(seconds)));
    }
}