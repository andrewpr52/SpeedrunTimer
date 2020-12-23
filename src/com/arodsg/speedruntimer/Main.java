package com.arodsg.speedruntimer;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.Listener;
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
	    			            		Bukkit.getServer().broadcastMessage("Current duration: " + convertSecondsToString(runningSeconds));
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
	    				if(isRunning) { // if running, then stop
	    					isRunning = false;
	    					timer.cancel();
	    					
	    					sender.sendMessage("Timer stopped!");
	    					return true;
	    				}
	    				else { // if not running
	    					sender.sendMessage("Error: no active timer running.");
	    				}
	    				break;
	    			case "reset":
	    				resetTimer();
	    				
	    				sender.sendMessage("Timer has been reset!");
	    				return true;
	    			case "time":
	    				sender.sendMessage("Current duration: " + convertSecondsToString(runningSeconds));
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
		    						runningSeconds += (numToAdd * 3600);
		    						sender.sendMessage("Time set to " + convertSecondsToString(runningSeconds));
		    						return true;
		    					case "minutes":
		    						runningSeconds += (numToAdd * 60);
		    						sender.sendMessage("Time set to " + convertSecondsToString(runningSeconds));
		    						return true;
		    					case "seconds":
		    						runningSeconds += numToAdd;
		    						sender.sendMessage("Time set to " + convertSecondsToString(runningSeconds));
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
		    						runningSeconds -= (numToSubtract * 3600);
		    						runningSeconds = runningSeconds >= 0 ? runningSeconds : 0;
		    						sender.sendMessage("Time set to " + convertSecondsToString(runningSeconds));
		    						return true;
		    					case "minutes":
		    						runningSeconds -= (numToSubtract * 60);
		    						runningSeconds = runningSeconds >= 0 ? runningSeconds : 0;
		    						sender.sendMessage("Time set to " + convertSecondsToString(runningSeconds));
		    						return true;
		    					case "seconds":
		    						runningSeconds -= numToSubtract;
		    						runningSeconds = runningSeconds >= 0 ? runningSeconds : 0;
		    						sender.sendMessage("Time set to " + convertSecondsToString(runningSeconds));
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
    
    private String convertSecondsToString(long seconds) {
    	return String.format("%02d:%02d:%02d",
			TimeUnit.SECONDS.toHours(seconds),
			TimeUnit.SECONDS.toMinutes(seconds) -
			TimeUnit.HOURS.toMinutes(TimeUnit.SECONDS.toHours(seconds)),
			TimeUnit.SECONDS.toSeconds(seconds) -
			TimeUnit.MINUTES.toSeconds(TimeUnit.SECONDS.toMinutes(seconds)));
    }
    
    private void resetTimer() {
    	isRunning = false;
    	timer.cancel();
    	runningSeconds = 0;
    }
}