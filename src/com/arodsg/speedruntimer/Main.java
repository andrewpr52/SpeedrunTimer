package com.arodsg.speedruntimer;

import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import com.mysql.jdbc.Connection;
import com.mysql.jdbc.PreparedStatement;

public class Main extends JavaPlugin implements Listener, TabCompleter {
	// databse vars
	String username; // Enter in your db username
	String password; // Enter your password for the db
	String url; // Enter URL w/db name

	// connection vars
	static Connection connection; // This is the variable we will use to connect to database
	
	String runUUID;
	
    Boolean isRunning = false;
	Boolean enterNether = false;
	Boolean exitNether = false;
	Boolean foundBlazeRods = false;
	Boolean foundFortress = false;
	Boolean enteredEnd = false;
	
	String enterNetherTime;
	String exitNetherTime;
	String foundBlazeRodsTime;
	String foundFortressTime;
	String enteredEndTime;
	
	Map<String, String> leaderboardPositions = new HashMap<>();
	
	Set<String> currentRunners = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
	
	Timer timer;
	long lastStartTimeMS = 0;
	long totalDurationMS = 0;
	long runningSeconds = 0;
	
    @Override
    public void onEnable() {
    	getServer().getPluginManager().registerEvents(this, this);
//    	getConfig().options().copyDefaults(false);
//    	saveConfig();
    	saveDefaultConfig();
    	
    	FileConfiguration config = this.getConfig();
    	username = config.getString("dbInfo.username");
    	password = config.getString("dbInfo.password");
    	String dbName = config.getString("dbInfo.dbName");
    	url = "jdbc:mysql://localhost:3306/" + dbName + "?autoReconnect=true&useSSL=false";
    	
    	try { //We use a try catch to avoid errors, hopefully we don't get any.
    	    Class.forName("com.mysql.jdbc.Driver"); //this accesses Driver in jdbc.
    	}
    	catch (ClassNotFoundException e) {
    	    e.printStackTrace();
    	    System.err.println("jdbc driver unavailable!");
    	    return;
    	}
    	
    	try { //Another try catch to get any SQL errors (for example connections errors)
    	    connection = (Connection) DriverManager.getConnection(url, username, password);
    	    //with the method getConnection() from DriverManager, we're trying to set
    	    //the connection's url, username, password to the variables we made earlier and
    	    //trying to get a connection at the same time. JDBC allows us to do this.
    	    
    	    List<String> leaderboardNames = Arrays.asList("overall", "enternether", "blazerods", "exitnether", "end");
    	    String sql;
    	    PreparedStatement stmt;
    	    
    	    for(String leaderboardName : leaderboardNames) {
	    	    sql = "CREATE TABLE IF NOT EXISTS `" + leaderboardName + "` (\r\n" + 
	    				"`id` INT NOT NULL AUTO_INCREMENT,\r\n" + 
	    				"`time` TEXT(120) NOT NULL,\r\n" + 
	    				"`players` TEXT(120) NOT NULL,\r\n" + 
	    				"`date` DATE NOT NULL,\r\n" + 
	    				"`uuid` TEXT(36) NOT NULL,\r\n" + 
	    				"PRIMARY KEY (`id`)\r\n" + 
    				  ");";
	    	    stmt = (PreparedStatement) connection.prepareStatement(sql);
	    	    stmt.executeUpdate();
    	    }
    	}
    	catch (SQLException e) { //catching errors)
    	    e.printStackTrace(); //prints out SQLException errors to the console (if any)
    	}
    }
    
    @Override
    public void onDisable() {
    	try { //using a try catch to catch connection errors (like wrong sql password...)
            if (connection != null && !connection.isClosed()){ //checking if connection isn't null to
                //avoid receiving a nullpointer
                connection.close(); //closing the connection field variable.
            }
        }
    	catch(Exception e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
		List<String> commandNames = Arrays.asList("speedruntimer", "srt", "timer");
		List<String> options = new ArrayList<String>();
		List<String> tabCompletionList = new ArrayList<String>();
		int argNum;
		
		if(sender instanceof Player) {
			if(commandNames.contains(command.getName())) {
				if(args.length == 1) { // /timer arg[0]
					options = Arrays.asList("start", "stop", "reset", "time", "add", "subtract", "leaderboard", "setvar");
					
					argNum = 0;
				}
				else if (args.length == 2) { // /timer arg[0] arg[1]
					switch(args[0]) {
						case "add":
							options = Arrays.asList("hours", "minutes", "seconds");
							break;
						case "subtract":
							options = Arrays.asList("hours", "minutes", "seconds");
							break;
						case "leaderboard":
							options = Arrays.asList("overall", "enternether", "blazerods", "exitnether", "end");
							break;
						case "setvar":
							options = Arrays.asList("enterNether", "exitNether", "foundBlazeRods", "foundFortress", "enteredEnd");
							break;
						default:
							break;
					}
					
					argNum = 1;
				}
				else if (args.length == 3) {
					if(args[0].equalsIgnoreCase("setvar")) {
						options = Arrays.asList("true", "false");
					}
					
					argNum = 2;
				}
				else {
					return tabCompletionList;
				}
				
				for(String option : options) {
					if(option.toLowerCase().startsWith(args[argNum].toLowerCase())) {
						tabCompletionList.add(option);
					}
				}
			}
		}
		
		return tabCompletionList;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    	List<String> commandNames = Arrays.asList("speedruntimer", "srt", "timer");
    	String commandName = command.getName();
    	
    	if (commandNames.contains(commandName) && args.length > 0) {
    		if(args.length > 0) {
	    		switch(args[0]) {
	    			case "start":
	    				if(!isRunning) {
		    				List<String> worlds = Arrays.asList("world", "world_nether", "world_the_end");
	    					
	    					for(String worldName : worlds) {
		    					List<Player> players = Bukkit.getServer().getWorld(worldName).getPlayers();
		    					for(Player player : players) {
		    						if(player.getGameMode() == GameMode.SURVIVAL) {
		    							String playerName = player.getName();
			    						currentRunners.add(playerName); // currentRunners is a Set, so if the start command is run more than once, runners will not be added multiple times
		    						}
		    					}
	    					}
		    				
		    				if(!currentRunners.isEmpty()) {
		    					runUUID = UUID.randomUUID().toString();
		    					
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
			    			    
			    			    for(World world : Bukkit.getServer().getWorlds()) { // resets each world time to 0 when the run is started
			    			    	world.setTime(0);
			    			    }
			    			    Bukkit.getServer().broadcastMessage("World time set to 0.");
			    			    Bukkit.getServer().broadcastMessage(ChatColor.GREEN + "Timer started!" + ChatColor.RESET);
		    				}
		    				else {
		    					sender.sendMessage("Error: no players are in Survival mode.");
		    				}
	    				}
	    				else { // if already running
	    					sender.sendMessage("Error: timer is already running.");
	    				}
	    				return true;
	    			case "stop":
	    				stopTimer(sender);
	    				return true;
	    			case "reset":
	    				resetTimer();
	    				
	    				Bukkit.getServer().broadcastMessage("Timer has been reset!");
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
		    						break;
		    					case "minutes":
		    						addSeconds(numToAdd * 60);
		    						break;
		    					case "seconds":
		    						addSeconds(numToAdd);
		    						break;
		    					default:
		    						sender.sendMessage("Error: invalid argument.");
		    						break;
		    				}
	    				}
	    				else {
	    					sender.sendMessage("Error: invalid number of arguments.");
	    				}
	    				
	    				return true;
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
		    						break;
		    					case "minutes":
		    						subtractSeconds(numToSubtract * 60);
		    						break;
		    					case "seconds":
		    						subtractSeconds(numToSubtract);
		    						break;
		    					default:
		    						sender.sendMessage("Error: invalid argument.");
		    						break;
		    				}
	    				}
	    				else {
	    					sender.sendMessage("Error: invalid number of arguments.");
	    				}
	    				
	    				return true;
	    			case "leaderboard":
	    				if(args.length == 2) {
	    					String leaderboardName = args[1].toLowerCase();
	    					List<String> leaderboardNames = Arrays.asList("overall", "blazerods", "enternether", "exitnether", "end");
	    					
	    					if(leaderboardNames.contains(leaderboardName)) {
	    						printLeaderboard(sender, leaderboardName, false);
	    					}
	    					else {
	    						sender.sendMessage("Error: invalid leaderboard name.");
	    					}
	    				}
	    				else {
	    					sender.sendMessage("Error: invalid number of arguments.");
	    				}
	    				return true;
	    			case "setvar":
	    				if(args.length == 3) {
	    					String varName = args[1].toLowerCase();
	    					String varValue = args[2].toLowerCase();
	    					
	    					switch(varName) {
	    						case "enternether":
	    							if(varValue.equalsIgnoreCase("true")) {
	    								enterNether = true;
	    							}
	    							else if(varValue.equalsIgnoreCase("false")) {
	    								enterNether = false;
	    							}
	    							break;
	    						case "exitnether":
	    							if(varValue.equalsIgnoreCase("true")) {
	    								exitNether = true;
	    							}
	    							else if(varValue.equalsIgnoreCase("false")) {
	    								exitNether = false;
	    							}
	    							break;
	    						case "foundblazerods":
	    							if(varValue.equalsIgnoreCase("true")) {
	    								foundBlazeRods = true;
	    							}
	    							else if(varValue.equalsIgnoreCase("false")) {
	    								foundBlazeRods = false;
	    							}
	    							break;
	    						case "foundfortress":
	    							if(varValue.equalsIgnoreCase("true")) {
	    								foundFortress = true;
	    							}
	    							else if(varValue.equalsIgnoreCase("false")) {
	    								foundFortress = false;
	    							}
	    							break;
	    						case "enteredend":
	    							if(varValue.equalsIgnoreCase("true")) {
	    								enteredEnd = true;
	    							}
	    							else if(varValue.equalsIgnoreCase("false")) {
	    								enteredEnd = false;
	    							}
	    							break;
	    					}
	    				}
	    				break;
	    			case "test_killdragon":
	    				endRun(true);
	    				
	    				return true;
	    			default:
	    				sender.sendMessage("Error: invalid argument.");
	    				break;
	    		}
    		}
    		else {
    			sender.sendMessage("Error: no arguments provided.");
    		}
    	}
    	
        return false;
    }
    
    @EventHandler
    public void onServerListPing(ServerListPingEvent event) {
    	String motdText;
        if(isRunning) {
        	motdText = "&0[&cIn Progress&0]";
        }
        else if(runningSeconds > 0) {
        	motdText = "&0[&ePaused&0]";
        }
        else {
        	motdText = "&0[&aReady&0]";
        }
        
        event.setMotd(motdText);
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
    	Player player = event.getPlayer();
    	if(!currentRunners.isEmpty() && !currentRunners.contains(player.getName())) { // if a player joins and is not a current runner, set their gamemode to spectator
    		player.setGameMode(GameMode.SPECTATOR);
        }
    }
    
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event){
    	String defaultDeathMessage = event.getDeathMessage();
    	String playerName = event.getEntity().getName();
    	
        if(currentRunners.contains(playerName)) {
        	event.setDeathMessage(""); // sets the default death message to empty
        	Bukkit.getServer().broadcastMessage(ChatColor.DARK_RED + defaultDeathMessage + ChatColor.RESET + "\n"); // broadcast the death message myself, so that it can be displayed before other messages
        	Bukkit.getServer().broadcastMessage(playerName + " has died! The overall time will not be saved.");
        	endRun(false);
        }
    }
    
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
    	Environment newWorldEnvironment = event.getPlayer().getWorld().getEnvironment();
    	
    	if(isRunning) {
    		if(!enterNether && newWorldEnvironment == World.Environment.NETHER) { // if entering the Nether for the first time
    			enterNether = true;
    			enterNetherTime = convertSecondsToString(runningSeconds);
    			Bukkit.getServer().broadcastMessage("Entered the Nether at " + enterNetherTime);
    			addToLeaderboard("enternether", enterNetherTime, currentRunners);
    		}
    		else if(!exitNether && enterNether && foundBlazeRods && newWorldEnvironment == World.Environment.NORMAL) { // if leaving the Nether after Blaze Rods have been found
    			exitNether = true;
    			exitNetherTime = convertSecondsToString(runningSeconds);
    			Bukkit.getServer().broadcastMessage("Exited the Nether at " + exitNetherTime);
    			addToLeaderboard("exitnether", exitNetherTime, currentRunners);
    		}
    		else if(!enteredEnd && newWorldEnvironment == World.Environment.THE_END) { // if entering the End for the first time
    			enteredEnd = true;
    			enteredEndTime = convertSecondsToString(runningSeconds);
    			Bukkit.getServer().broadcastMessage("Entered the End at " + enteredEndTime);
    			addToLeaderboard("end", enteredEndTime, currentRunners);
    		}
    	}
    }
    
    @EventHandler
    public void onItemPickup(EntityPickupItemEvent event) {
        LivingEntity entityType = event.getEntity();
        Item pickupItem = event.getItem();
    	
        if(isRunning && !foundBlazeRods && entityType instanceof Player && pickupItem.getItemStack().getType() == Material.BLAZE_ROD) {
        	foundBlazeRods = true;
        	foundBlazeRodsTime = convertSecondsToString(runningSeconds);
        	Bukkit.getServer().broadcastMessage("Blaze Rod acquired at " + foundBlazeRodsTime);
        	addToLeaderboard("blazerods", foundBlazeRodsTime, currentRunners);
        }
    }
    
    @EventHandler
    public void onEnderDragonDeath(EntityDeathEvent e) { // stop timer and broadcast time when the dragon is killed
         if(e.getEntity() instanceof EnderDragon){
        	 endRun(true);
        }
    }
    
    // This will be called to print the leaderboards when a player dies or the dragon is killed.
    //	The appropriate leaderboard will be printed for each section that was completed during the run.
    private void endRun(Boolean dragonKilled) {
    	Map<String, String> leaderboardTimes = new LinkedHashMap<>();
    	stopTimer(null);
        
        if(dragonKilled) {
        	String overallTimeString = convertSecondsToString(runningSeconds);
        	
        	addToLeaderboard("overall", overallTimeString, currentRunners);
        	leaderboardTimes.put("overall", overallTimeString);
        }
        if(enterNetherTime != null) {
        	leaderboardTimes.put("enternether", enterNetherTime);
        }
        if(foundBlazeRodsTime != null) {
        	leaderboardTimes.put("blazerods", foundBlazeRodsTime);
        }
        if(exitNetherTime != null) {
        	leaderboardTimes.put("exitnether", exitNetherTime);
        }
        if(enteredEndTime != null) {
        	leaderboardTimes.put("end", enteredEndTime);
        }
        
        new BukkitRunnable() { //prints the leaderboard after 3 seconds (60L)
	   	 	public void run() {
	   	 		if(leaderboardTimes.size() > 0) {
					Map.Entry<String,String> entry = leaderboardTimes.entrySet().iterator().next();
	       	 		String leaderboardName = entry.getKey();
	       	 		
	       	 	printLeaderboard(null, leaderboardName, true);
	       	 		leaderboardTimes.remove(leaderboardName);
    			}
	    			
    			if(leaderboardTimes.size() == 0) {
    				this.cancel();
    			}
    		}
    	}.runTaskTimer(this, 60L, 60L);
    	
    	resetTimer();
    }
    
    private void printLeaderboard(CommandSender sender, String leaderboardName, Boolean highlightPositions) {
    	Boolean calledFromCommand = (sender != null);
    	String fullLeaderboardString;
    	
    	switch(leaderboardName) {
    		case "overall":
    			fullLeaderboardString = ChatColor.WHITE + "\n---OVERALL TIMES---\n";
    			break;
    		case "blazerods":
    			fullLeaderboardString = ChatColor.WHITE + "\n---BLAZE ROD TIMES---\n";
    			break;
    		case "enternether":
    			fullLeaderboardString = ChatColor.WHITE + "\n---ENTER NETHER TIMES---\n";
    			break;
    		case "exitnether":
    			fullLeaderboardString = ChatColor.WHITE + "\n---EXIT NETHER TIMES---\n";
    			break;
    		case "end":
    			fullLeaderboardString = ChatColor.WHITE + "\n---ENTER END TIMES---\n";
    			break;
    		default:
    			fullLeaderboardString = ChatColor.WHITE + "\n---LEADERBOARD---\n";
    			break;
    	}
    	
    	String sql = "SELECT * FROM " + leaderboardName + " ORDER BY time LIMIT 10";
    	ResultSet results;
		try {
			PreparedStatement stmt = (PreparedStatement) connection.prepareStatement(sql);
			results = stmt.executeQuery();
			int placementPosition = 1;
			
			while (results.next()) {
				SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/YY");
				
				String uuid = results.getString("uuid");
				String time = results.getString("time");
				String players = results.getString("players");
				String date = formatter.format(results.getDate("date"));
				
				if(highlightPositions && uuid.equals(runUUID)) { //if the run's positions should be highlighted, that line will be highlighted green
	    			fullLeaderboardString += ChatColor.GREEN + Integer.toString(placementPosition) + " - " + time + " - " + players + " - " + date + ChatColor.WHITE + "\n";
	    		}
	    		else { //otherwise, print the line as normal
	    			fullLeaderboardString += placementPosition + " - " + time + " - " + players + " - " + date + "\n";
	    		}
				
				placementPosition++;
			}
			
			fullLeaderboardString += "\n\n";
	    	
	    	if(calledFromCommand) {
	    		sender.sendMessage(fullLeaderboardString);
	    	}
	    	else {
	    		Bukkit.getServer().broadcastMessage(fullLeaderboardString);
	    	}
		}
		catch (SQLException e) {
			e.printStackTrace();
		}
    }
    
    private void addToLeaderboard(String leaderboardName, String time, Set<String> runnerNames) {
		java.util.Date date = new java.util.Date();
		java.sql.Date sqlDate = new java.sql.Date(date.getTime());
		
		String sql = "INSERT INTO " + leaderboardName + " (uuid, time, players, date) VALUES ('" + runUUID + "', '" + time + "', '" + String.join(", ", runnerNames) + "', ?)";
		try {
			PreparedStatement stmt = (PreparedStatement) connection.prepareStatement(sql);
			stmt.setDate(1, sqlDate);
			stmt.executeUpdate();
		}
		catch (SQLException e) {
			e.printStackTrace();
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
			
			Bukkit.getServer().broadcastMessage(ChatColor.DARK_RED + "Timer stopped!" + ChatColor.RESET);
			broadcastRunningSeconds("Duration:");
		}
		else if(calledFromCommand) { // if not running, and function was called from a command
			sender.sendMessage("Error: no active timer running.");
		}
    }
    
    private void resetTimer() {
    	isRunning = false;
    	enterNether = false;
    	exitNether = false;
    	foundBlazeRods = false;
    	foundFortress = false;
    	enteredEnd = false;
    	
    	enterNetherTime = null;
    	exitNetherTime = null;
    	foundBlazeRodsTime = null;
    	foundFortressTime = null;
    	enteredEndTime = null;
    	
    	timer.cancel();
    	runningSeconds = 0;
    	currentRunners = new TreeSet<String>();
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