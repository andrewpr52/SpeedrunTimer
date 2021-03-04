package com.arodsg.speedruntimer;

import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
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
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import com.mysql.jdbc.Connection;
import com.mysql.jdbc.PreparedStatement;

public class Main extends JavaPlugin implements Listener, TabCompleter {
	// database vars
	String dbUsername, dbPassword, dbUrl;
	static Connection connection; // This is the variable we will use to connect to database
	
	long seed;
	Boolean isRunning = false, enterNether = false, exitNether = false, foundBlazeRods = false, foundFortress = false,
			enteredEnd = false, runCompleted = false, shouldConfirmTimerEnd = false, logExecutedSQL = false;
	String enterNetherTime, exitNetherTime, foundBlazeRodsTime, foundFortressTime, enteredEndTime, confirmTimerEndPlayerName;

	TreeSet<String> currentRunners = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);

	Timer timer;
	int runningSeconds = 0;

	@Override
	public void onEnable() {
		getServer().getPluginManager().registerEvents(this, this);
		saveDefaultConfig();
		
		FileConfiguration config = this.getConfig();
		logExecutedSQL = config.isSet("logExecutedSQL") && config.getBoolean("logExecutedSQL");

		World overworld = null;
		for (World world : Bukkit.getServer().getWorlds()) { // sets the world seed var when plugin is enabled
			if (world.getEnvironment() == World.Environment.NORMAL) {
				overworld = world;
				seed = world.getSeed();
			}
		}
		
		try {
			createRunInfoTable();
			createLeaderboardTables();
		}
		catch (SQLException e) {
			e.printStackTrace();
		}

		setDoDaylightCycle(false); // Freeze world time on enable, since 'timer start' will enable this
		if (overworld != null && runningSeconds == 0) { // If the run hasn't been previously started, set time to 0 on enable after freezing world time.
			overworld.setTime(0);
		}
	}

	@Override
	public void onDisable() {
		isRunning = false;

		if (timer != null) {
			timer.cancel();
		}

		try { // catch connection errors (like wrong sql password)
			if (connection != null && !connection.isClosed()) { // checking if connection isn't null to avoid receiving a null pointer
				closeDBConnection(); // closing the connection field variable.
			}
		} catch (Exception e) {
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
				if (args.length == 1) { // /timer arg[0]
					options = Arrays.asList("start", "pause", "end", "reset", "time", "add", "subtract", "leaderboard", "broadcastinterval", "setvar");

					argNum = 0;
				} else if (args.length == 2) { // /timer arg[0] arg[1]
					switch (args[0]) {
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
						options = Arrays.asList("enterNether", "exitNether", "foundBlazeRods", "foundFortress",
								"enteredEnd");
						break;
					default:
						break;
					}

					argNum = 1;
				} else if (args.length == 3) {
					if (args[0].equalsIgnoreCase("setvar")) {
						options = Arrays.asList("true", "false");
					}

					argNum = 2;
				} else {
					return tabCompletionList;
				}

				for (String option : options) {
					if (option.toLowerCase().startsWith(args[argNum].toLowerCase())) {
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
			if (args.length > 0) {
				switch (args[0]) {
				case "start":
					if (!isRunning) {
						List<String> worlds = Arrays.asList("world", "world_nether", "world_the_end");

						for (String worldName : worlds) {
							World world = Bukkit.getServer().getWorld(worldName);
							if(runningSeconds == 0) { // clear weather if this is the start of the run
								world.setStorm(false);
							}
							
							List<Player> players = world.getPlayers();
							for (Player player : players) {
								if (player.getGameMode() == GameMode.SURVIVAL) {
									String playerName = player.getName();
									currentRunners.add(playerName); // currentRunners is a Set, so if the start command is run more than once, runners will not be added multiple times

									if (runningSeconds == 0) { // if this is the first time the run is started, set player health and food to default
										player.setHealth(20.0);
										player.setFoodLevel(20);
										player.setSaturation(5);
										player.setExhaustion(0);
									}
								}
							}
						}

						if (!currentRunners.isEmpty()) { // if the run was previously started and there are current runners
							Boolean allRunnersInSurvival = true;
							for (String worldName: worlds) {
								World world = Bukkit.getServer().getWorld(worldName);
								List<Player> players = world.getPlayers();
								
								for(Player player: players) {
									String playerName = player.getName();
									if(currentRunners.contains(playerName) && player.getGameMode() != GameMode.SURVIVAL) { // checks that all current runners are in Survival mode before re-starting the timer
										allRunnersInSurvival = false;
										Bukkit.getServer().broadcastMessage("Error: " + playerName + " must be in Survival mode before restarting the timer.");
										break;
									}
								}
							}
							
							if(allRunnersInSurvival) {
								String playersString = String.join(", ", currentRunners);
								
								if(runningSeconds == 0) {
									checkRunExists(seed, playersString);
									insertRunInfo(currentRunners);
									Bukkit.getServer().broadcastMessage(ChatColor.GREEN + "Timer started!\n" + ChatColor.WHITE + "Runners: " + playersString + ChatColor.RESET);
								}
								else {
									Bukkit.getServer().broadcastMessage(ChatColor.GREEN + "Timer resumed!" + ChatColor.RESET);
								}
								
								isRunning = true;
								setDoDaylightCycle(true);
								
								FileConfiguration config = this.getConfig();
	
								timer = new Timer();
								timer.scheduleAtFixedRate(new TimerTask() {
									public void run() {
										runningSeconds++;
										int dbUpdateRunTimeSeconds = config.isSet("dbUpdateRunTimeSeconds") ? config.getInt("dbUpdateRunTimeSeconds") : 15;
										int broadcastTimeMinutes = config.isSet("broadcastTimeMinutes") ? config.getInt("broadcastTimeMinutes") : 15;
	
										if(runningSeconds % dbUpdateRunTimeSeconds == 0) { // updates running seconds in the database at the interval, in seconds, specified in the config
											updateRunningSeconds(seed, runningSeconds);
										}
										
										if(runningSeconds % (broadcastTimeMinutes * 60) == 0) { // broadcasts time every interval, in minutes, as specified in the config
											broadcastRunningSeconds("Duration:");
										}
									}
								}, 1000, 1000);
							}
						} else {
							sender.sendMessage("Error: no players are in Survival mode.");
						}
					} else { // if already running
						sender.sendMessage("Error: timer is already running.");
					}
					return true;
				case "pause":
					pauseTimer(sender, false);
					return true;
				case "end":
					sender.sendMessage("This will stop the timer and end the run, are you sure?\n" + ChatColor.BOLD + "(Type 'Y' to confirm, or 'N' to continue the run...)" + ChatColor.RESET);
					shouldConfirmTimerEnd = true;
					confirmTimerEndPlayerName = sender.getName();
					return true;
				case "reset":
					resetTimer();
					setDoDaylightCycle(false);

					for (World world : Bukkit.getServer().getWorlds()) { // resets each world time to 0 when the timer is reset
						world.setTime(0);
					}

					Bukkit.getServer().broadcastMessage("Timer has been reset!");
					return true;
				case "time":
					sender.sendMessage("Duration: " + convertSecondsToString(runningSeconds));
					return true;
				case "add":
					if (args.length == 3) {
						int numToAdd;

						try {
							numToAdd = Integer.parseInt(args[2]);
						} catch (NumberFormatException e) {
							sender.sendMessage("Error: invalid argument \"" + args[2] + "\".");
							break;
						}

						switch (args[1]) {
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
					} else {
						sender.sendMessage("Error: invalid number of arguments.");
					}

					return true;
				case "subtract":
					if (args.length == 3) {
						int numToSubtract;

						try {
							numToSubtract = Integer.parseInt(args[2]);
						} catch (NumberFormatException e) {
							sender.sendMessage("Error: invalid argument \"" + args[2] + "\".");
							break;
						}

						switch (args[1]) {
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
					} else {
						sender.sendMessage("Error: invalid number of arguments.");
					}

					return true;
				case "leaderboard":
					if (args.length == 2) {
						String leaderboardName = args[1].toLowerCase();
						List<String> leaderboardNames = Arrays.asList("overall", "blazerods", "enternether",
								"exitnether", "end");

						if (leaderboardNames.contains(leaderboardName)) {
							printLeaderboard(sender, leaderboardName, false);
						} else {
							sender.sendMessage("Error: invalid leaderboard name.");
						}
					} else {
						sender.sendMessage("Error: invalid number of arguments.");
					}
					return true;
				case "broadcastinterval":
					if (args.length == 2) {
						int intervalTime = Integer.valueOf(args[1]);
						if(intervalTime > 0) {
							this.getConfig().set("broadcastTimeMinutes", intervalTime);
							this.saveConfig();
							Bukkit.getServer().broadcastMessage("Time broadcast interval set to " + intervalTime + " minutes.");
						}
						else {
							sender.sendMessage("Error: invalid time interval - please enter a positive numeric value.");
						}
					}
					else {
						sender.sendMessage("Error: invalid number of arguments.");
					}
					return true;
				case "setvar":
					if (args.length == 3) {
						String varName = args[1].toLowerCase();
						String varValue = args[2].toLowerCase();

						switch (varName) {
						case "enternether":
							if (varValue.equalsIgnoreCase("true")) {
								enterNether = true;
							} else if (varValue.equalsIgnoreCase("false")) {
								enterNether = false;
							}
							break;
						case "exitnether":
							if (varValue.equalsIgnoreCase("true")) {
								exitNether = true;
							} else if (varValue.equalsIgnoreCase("false")) {
								exitNether = false;
							}
							break;
						case "foundblazerods":
							if (varValue.equalsIgnoreCase("true")) {
								foundBlazeRods = true;
							} else if (varValue.equalsIgnoreCase("false")) {
								foundBlazeRods = false;
							}
							break;
						case "foundfortress":
							if (varValue.equalsIgnoreCase("true")) {
								foundFortress = true;
							} else if (varValue.equalsIgnoreCase("false")) {
								foundFortress = false;
							}
							break;
						case "enteredend":
							if (varValue.equalsIgnoreCase("true")) {
								enteredEnd = true;
							} else if (varValue.equalsIgnoreCase("false")) {
								enteredEnd = false;
							}
							break;
						}
					}
					break;
				case "test_killdragon":
					enterNether = true;
					exitNether = true;
					foundBlazeRods = true;
					foundFortress = true;
					enteredEnd = true;
					endRun(true);

					return true;
				default:
					sender.sendMessage("Error: invalid argument.");
					break;
				}
			} else {
				sender.sendMessage("Error: no arguments provided.");
			}
		}

		return false;
	}
	
	@EventHandler
	public void onChat(AsyncPlayerChatEvent event) {
		if(shouldConfirmTimerEnd) {
			Player player = event.getPlayer();
			String chatMessage = event.getMessage();
			
			if(player.getName().equals(confirmTimerEndPlayerName)) {
				if(chatMessage.equalsIgnoreCase("Yes") || chatMessage.equalsIgnoreCase("Y")) {
					Bukkit.getServer().broadcastMessage("Run ended! The overall time will not be saved.");
					endRun(false);
				}
				else if(chatMessage.equalsIgnoreCase("No") || chatMessage.equalsIgnoreCase("N")) {
					player.sendMessage("Command aborted. Timer is still running!");
				}
				else {
					player.sendMessage("Command aborted, invalid option. Timer is still running!");
				}
				
				shouldConfirmTimerEnd = false;
				confirmTimerEndPlayerName = "";
				
				event.setCancelled(true);
			}
		}
	}

	@EventHandler
	public void onServerListPing(ServerListPingEvent event) {
		String motdText;
		if (isRunning) { // run is currently in progress
			motdText = "&0[&4In Progress&0]";
		} else if (runCompleted) { // run is completed (died, or killed dragon)
			motdText = "&0[&3Finished&0]";
		} else if (runningSeconds > 0) { // run was previously started, but currently paused
			motdText = "&0[&6Paused&0]";
		} else { // run is not in progress (and not yet completed)
			motdText = "&0[&2Ready&0]";
		}

		event.setMotd(motdText);
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		if (!currentRunners.isEmpty() && !currentRunners.contains(player.getName())) { // if a player joins and is not a current runner, set their gamemode to spectator
			player.setGameMode(GameMode.SPECTATOR);
		}
	}
	
	Boolean supressEvent = false;
	
	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerGameModeChange(PlayerGameModeChangeEvent event) {
		if(supressEvent) {
			return;
		}
		
		Player player = event.getPlayer();
		GameMode gameMode = player.getGameMode();
		
		if (isRunning && currentRunners.contains(player.getName())) { // if a current runner tries to change their gamemode while the timer is running
			player.sendMessage("Error: Run is in progress. You must pause or end the run to be able to switch gamemodes.");
		}
		else {
			gameMode = event.getNewGameMode();
		}
		
		if(player != null && gameMode != null) {
			supressEvent = true;
			player.setGameMode(gameMode);
			
			// sets appropriate player attributes based on the set gamemode (this will help to reset the values that get set by the PermissionGamemode plugin)
			player.setAllowFlight(gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR);
			player.setFlying(gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR);
			supressEvent = false;
		}
		
		event.setCancelled(true);
	}

	@EventHandler
	public void onPlayerDeath(PlayerDeathEvent event) {
		String defaultDeathMessage = event.getDeathMessage();
		String playerName = event.getEntity().getName();

		if (currentRunners.contains(playerName)) {
			event.setDeathMessage(""); // sets the default death message to empty
			Bukkit.getServer().broadcastMessage(ChatColor.DARK_RED + defaultDeathMessage + ChatColor.RESET + "\n"); // broadcast the death message myself, so that it can be displayed before other messages
			Bukkit.getServer().broadcastMessage(playerName + " has died! The overall time will not be saved.");
			endRun(false);
		}
	}

	@EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
    	Player player = event.getPlayer();
    	Environment newWorldEnvironment = player.getWorld().getEnvironment();
    	
    	if(isRunning && currentRunners.contains(player.getName())) {
    		if(!enterNether && newWorldEnvironment == World.Environment.NETHER) { // if entering the Nether for the first time
    			enterNether = true;
    			enterNetherTime = convertSecondsToString(runningSeconds);
    			Bukkit.getServer().broadcastMessage("Entered the Nether at " + enterNetherTime);
    			addToLeaderboard("enternether", enterNetherTime, currentRunners);
    		}
    		else if(!exitNether && enterNether && foundBlazeRods && newWorldEnvironment == World.Environment.NORMAL) { // if leaving the Nether for the first time after Blaze Rods have been found
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

		if (isRunning && !foundBlazeRods && entityType instanceof Player
				&& pickupItem.getItemStack().getType() == Material.BLAZE_ROD) {
			foundBlazeRods = true;
			foundBlazeRodsTime = convertSecondsToString(runningSeconds);
			Bukkit.getServer().broadcastMessage("Blaze Rod acquired at " + foundBlazeRodsTime);
			addToLeaderboard("blazerods", foundBlazeRodsTime, currentRunners);
		}
	}

	@EventHandler
	public void onEnderDragonDeath(EntityDeathEvent e) { // stop timer and broadcast time when the dragon is killed
		if (e.getEntity() instanceof EnderDragon && isRunning) {
			endRun(true);
		}
	}
	
	private Boolean dbConnect() {
		if(logExecutedSQL) {
			getLogger().info("Connecting to DB...");
		}
		
		FileConfiguration config = this.getConfig();
		dbUsername = config.getString("dbInfo.username");
		dbPassword = config.getString("dbInfo.password");
		String dbName = config.getString("dbInfo.dbName");
		dbUrl = "jdbc:mysql://localhost:3306/" + dbName + "?autoReconnect=true&useSSL=false";
		
		try { // We use a try catch to avoid errors, hopefully we don't get any.
			Class.forName("com.mysql.jdbc.Driver"); // this accesses Driver in jdbc.
		}
		catch (ClassNotFoundException e) {
			e.printStackTrace();
			System.err.println("jdbc driver unavailable!");
			return false;
		}
		
		try { // catch any SQL errors (ex: connection errors)
			connection = (Connection) DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
			if(logExecutedSQL) {
				getLogger().info("DB connection established.");
			}
			// with the method getConnection() from DriverManager, we're trying to set
			// the connection's url, username, password to the variables we made earlier and
			// trying to get a connection at the same time. JDBC allows us to do this.
		}
		catch (SQLException e) {
			e.printStackTrace(); // prints out SQLException errors to the console (if any)
			return false;
		}
		
		return true;
	}

	private void closeDBConnection() {
		if(connection != null) {
			try {
				connection.close();
				
				if(logExecutedSQL) {
					getLogger().info("DB connection closed.");
				}
			}
			catch(SQLException e) {
				e.printStackTrace();
			}
		}
	}
	
	private void logExecutedSQL(String sql) {
		if(logExecutedSQL) {
			getLogger().info("Executed SQL: " + sql);
		}
	}
	
	private void createRunInfoTable() throws SQLException { // create the parent run_info table if it doesn't exist - this stores the run id, players, and seed.
		dbConnect();
		
		String sql = "CREATE TABLE IF NOT EXISTS `run_info` (\r\n"
		+ "`id` INT NOT NULL AUTO_INCREMENT,\r\n"
		+ "`players` VARCHAR(32) NOT NULL,\r\n"
		+ "`running_seconds` INT(5) DEFAULT 0,\r\n"
		//+ "`finished` BOOLEAN DEFAULT false,\r\n"
		+ "`seed` BIGINT(19) UNIQUE,\r\n"
		+ "`created` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,\r\n"
		+ "`endtimestamp` TIMESTAMP NULL DEFAULT NULL,\r\n"
		+ "PRIMARY KEY (`id`)\r\n"
		+ ");";
		PreparedStatement stmt = (PreparedStatement) connection.prepareStatement(sql);
		stmt.executeUpdate();
		logExecutedSQL(sql);
		
		closeDBConnection();
	}

	private void createLeaderboardTables() throws SQLException { // create the child table for each leaderboard - this stores the run's time, date, and run_id (foreign key) for each specific leaderboard.
		dbConnect();
		
		List<String> leaderboardNames = Arrays.asList("overall", "enternether", "blazerods", "exitnether", "end");
		String sql;
		PreparedStatement stmt;
		
		for (String leaderboardName : leaderboardNames) {
			sql = "CREATE TABLE IF NOT EXISTS `" + leaderboardName + "` (\r\n" + "`id` INT NOT NULL AUTO_INCREMENT,\r\n"
			+ "`time` VARCHAR(8) NOT NULL,\r\n" + "`date` DATE NOT NULL,\r\n" + "`run_id` INT(11),\r\n"
			+ "PRIMARY KEY (`id`),\r\n" + "CONSTRAINT `fk_" + leaderboardName
			+ "` FOREIGN KEY (`run_id`) REFERENCES `run_info`(`id`) ON DELETE CASCADE\r\n" + ");";
			stmt = (PreparedStatement) connection.prepareStatement(sql);
			stmt.executeUpdate();
			logExecutedSQL(sql);
		}
		getLogger().info("Leaderboard tables created (or already exist).");
		
		closeDBConnection();
	}
	
	private void checkRunExists(long seed, String players) {
		String sql;
		PreparedStatement stmt;
		ResultSet results;

		dbConnect();
		
		try {
			sql = "SELECT id, running_seconds, endtimestamp FROM run_info WHERE seed = " + seed + " AND players = '" + players + "'";
			stmt = (PreparedStatement) connection.prepareStatement(sql);
			results = stmt.executeQuery();
			logExecutedSQL(sql);

			if (results.next()) { // if the current world seed with the current runners already exists in run_info, get that runID and set the appropriate checkpoints.
				int runID = results.getInt("id");
				runningSeconds = results.getInt("running_seconds");
				runCompleted = results.getTimestamp("endtimestamp") != null;

				sql = "SELECT time FROM enternether WHERE run_id = '" + runID + "'";
				stmt = (PreparedStatement) connection.prepareStatement(sql);
				results = stmt.executeQuery();
				logExecutedSQL(sql);
				if (results.next()) {
					enterNether = true;
					enterNetherTime = results.getString("time");
				}

				sql = "SELECT time FROM blazerods WHERE run_id = '" + runID + "'";
				stmt = (PreparedStatement) connection.prepareStatement(sql);
				results = stmt.executeQuery();
				logExecutedSQL(sql);
				if (results.next()) {
					foundBlazeRods = true;
					foundBlazeRodsTime = results.getString("time");
				}

				sql = "SELECT time FROM exitnether WHERE run_id = '" + runID + "'";
				stmt = (PreparedStatement) connection.prepareStatement(sql);
				results = stmt.executeQuery();
				logExecutedSQL(sql);
				if (results.next()) {
					exitNether = true;
					exitNetherTime = results.getString("time");
				}

				sql = "SELECT time FROM end WHERE run_id = '" + runID + "'";
				stmt = (PreparedStatement) connection.prepareStatement(sql);
				results = stmt.executeQuery();
				logExecutedSQL(sql);
				if (results.next()) {
					enteredEnd = true;
					enteredEndTime = results.getString("time");
				}

//				sql = "SELECT finished FROM run_info WHERE id = '" + runID + "'";
//				stmt = (PreparedStatement) connection.prepareStatement(sql);
//				results = stmt.executeQuery();
//				logExecutedSQL(sql);
//				if (results.next()) {
//					runCompleted = results.getBoolean("finished");
//				}
			}
			
			closeDBConnection();
		}
		catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	private void updateRunningSeconds(long seed, int seconds) {
		String playerString = String.join(", ", currentRunners);
		String sql = "UPDATE run_info SET running_seconds = " + seconds + " WHERE seed = " + seed + " AND players = '" + playerString + "';";

		dbConnect();
		
		try {
			PreparedStatement stmt = (PreparedStatement) connection.prepareStatement(sql);
			stmt.executeUpdate();
			logExecutedSQL(sql);
			
			closeDBConnection();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	// This will be called to print the leaderboards when a player dies or the dragon is killed.
	// The appropriate leaderboard will be printed for each section that was completed during the run.
	private void endRun(Boolean dragonKilled) {
		runCompleted = true;
		pauseTimer(null, true);
//		setRunFinished();
		
		if(enterNether || runningSeconds >= 60) {
			updateRunEndTime();
			updateRunningSeconds(seed, runningSeconds);
		}
		else { // delete the ended run from run_info if nether wasn't entered or run time was less than 60 seconds
			deleteRun();
		}

		Map<String, String> leaderboardTimes = new LinkedHashMap<>();

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

		new BukkitRunnable() { // prints the leaderboard after 3 seconds (60L)
			public void run() {
				if (leaderboardTimes.size() > 0) {
					Map.Entry<String, String> entry = leaderboardTimes.entrySet().iterator().next();
					String leaderboardName = entry.getKey();

					printLeaderboard(null, leaderboardName, true);
					leaderboardTimes.remove(leaderboardName);
				}

				if (leaderboardTimes.size() == 0) {
					this.cancel();
				}
			}
		}.runTaskTimer(this, 60L, 60L);

		resetTimer();
	}

	private String getLeaderboardHeading(String leaderboardName) {
		switch (leaderboardName) {
		case "overall":
			return ChatColor.WHITE + "\n---OVERALL TIMES---\n";
		case "blazerods":
			return ChatColor.WHITE + "\n---BLAZE ROD TIMES---\n";
		case "enternether":
			return ChatColor.WHITE + "\n---ENTER NETHER TIMES---\n";
		case "exitnether":
			return ChatColor.WHITE + "\n---EXIT NETHER TIMES---\n";
		case "end":
			return ChatColor.WHITE + "\n---ENTER END TIMES---\n";
		default:
			return ChatColor.WHITE + "\n---LEADERBOARD---\n";
		}
	}

	private void printLeaderboard(CommandSender sender, String leaderboardName, Boolean highlightPositions) {
		Boolean calledFromCommand = (sender != null);
		String fullLeaderboardString = getLeaderboardHeading(leaderboardName);

		String sql = "SELECT * FROM " + leaderboardName + " INNER JOIN run_info ON " + leaderboardName
				+ ".run_id = run_info.id ORDER BY time LIMIT 10";
		ResultSet results;
		
		dbConnect();
		
		try {
			PreparedStatement stmt = (PreparedStatement) connection.prepareStatement(sql);
			results = stmt.executeQuery();
			logExecutedSQL(sql);
			int placementPosition = 1;

			while (results.next()) {
				SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yy");

				long seed = results.getLong("seed");
				String time = results.getString("time");
				String players = results.getString("players");
				String date = formatter.format(results.getDate("date"));

				if (highlightPositions && seed == this.seed) { // if the run's positions should be highlighted, that line will be highlighted green
					fullLeaderboardString += ChatColor.GREEN + Integer.toString(placementPosition) + " - " + time
							+ " - " + players + " - " + date + ChatColor.WHITE + "\n";
				} else { // otherwise, print the line as normal
					fullLeaderboardString += placementPosition + " - " + time + " - " + players + " - " + date + "\n";
				}

				placementPosition++;
			}

			fullLeaderboardString += "\n\n";

			if (calledFromCommand) {
				sender.sendMessage(fullLeaderboardString);
			} else {
				Bukkit.getServer().broadcastMessage(fullLeaderboardString);
			}
			
			closeDBConnection();
		}
		catch (SQLException e) {
			e.printStackTrace();
		}
	}

	private void insertRunInfo(TreeSet<String> runnerNames) {
		String runners = String.join(", ", runnerNames);
		String sql = "INSERT INTO run_info (players, seed) VALUES ('" + runners + "', " + seed + ")";
		
		dbConnect();

		try {
			PreparedStatement stmt = (PreparedStatement) connection.prepareStatement(sql);
			stmt.executeUpdate();
			logExecutedSQL(sql);
			
			closeDBConnection();
		}
		catch (SQLException e) {
			Bukkit.getServer().getLogger().info("Row already exists in run_info, or an error occurred.");
//			e.printStackTrace();
		}
	}

	private void updateRunEndTime() {
		java.util.Date date = new java.util.Date();
		java.sql.Timestamp timestamp = new java.sql.Timestamp(date.getTime());
		String playerString = String.join(", ", currentRunners);
		String sql = "UPDATE run_info SET endtimestamp = (?) WHERE seed = " + seed + " AND players = '" + playerString + "';";
		
		dbConnect();

		try {
			PreparedStatement stmt = (PreparedStatement) connection.prepareStatement(sql);
			stmt.setTimestamp(1, timestamp);
			stmt.executeUpdate();
			logExecutedSQL(sql);
			
			closeDBConnection();
		}
		catch (SQLException e) {
			e.printStackTrace();
		}
		
//		String playerString = String.join(", ", currentRunners);
//		String sql = "UPDATE run_info SET finished = true WHERE seed = " + seed + " AND players = '" + playerString + "';";
//		
//		dbConnect();
//
//		try {
//			PreparedStatement stmt = (PreparedStatement) connection.prepareStatement(sql);
//			stmt.executeUpdate();
//			logExecutedSQL(sql);
//			
//			closeDBConnection();
//		}
//		catch (SQLException e) {
//			e.printStackTrace();
//		}
	}
	
	private void deleteRun() {
		String playerString = String.join(", ", currentRunners);
		String sql = "DELETE FROM run_info WHERE seed = " + seed + " AND players = '" + playerString + "';";
		
		dbConnect();

		try {
			PreparedStatement stmt = (PreparedStatement) connection.prepareStatement(sql);
			stmt.executeUpdate();
			logExecutedSQL(sql);
			
			closeDBConnection();
		}
		catch (SQLException e) {
			e.printStackTrace();
		}
	}

	private void addToLeaderboard(String leaderboardName, String time, TreeSet<String> runnerNames) {
		java.util.Date date = new java.util.Date();
		java.sql.Date sqlDate = new java.sql.Date(date.getTime());
		String playersString = String.join(", ", runnerNames);

		String sql = "INSERT INTO " + leaderboardName
				+ " (run_id, time, date) VALUES ((SELECT id FROM run_info WHERE seed = " + seed + " AND players = '" + playersString + "'), '" + time
				+ "', ?)";
		
		dbConnect();

		try {
			PreparedStatement stmt = (PreparedStatement) connection.prepareStatement(sql);
			stmt.setDate(1, sqlDate);
			stmt.executeUpdate();
			logExecutedSQL(sql);
			
			closeDBConnection();
		}
		catch (SQLException e) {
			e.printStackTrace();
		}
	}

	private void addSeconds(int seconds) {
		runningSeconds += seconds;
		broadcastRunningSeconds("Time set to");
	}

	private void subtractSeconds(int seconds) {
		runningSeconds -= seconds;
		runningSeconds = runningSeconds >= 0 ? runningSeconds : 0;
		broadcastRunningSeconds("Time set to");
	}

	private void pauseTimer(CommandSender sender, Boolean runEnded) {
		Boolean calledFromCommand = (sender != null);
		String pausedOrStopped = runEnded ? "stopped" : "paused";

		if (isRunning) { // if running, then pause
			isRunning = false;
			timer.cancel();

			if (!runCompleted) { // if run is still active, but timer is paused (meaning timer is temporarily paused)
				setDoDaylightCycle(false);
			}

			Bukkit.getServer().broadcastMessage(ChatColor.DARK_RED + "Timer " + pausedOrStopped + "!" + ChatColor.RESET);
			broadcastRunningSeconds("Duration:");
		} else if (calledFromCommand) { // if not running, and function was called from a command
			sender.sendMessage("Error: no active timer running.");
		}
	}

	private void setDoDaylightCycle(Boolean shouldCycle) {
		for (World world : Bukkit.getServer().getWorlds()) {
			world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, shouldCycle);
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

	private String convertSecondsToString(int seconds) {
		return String.format("%02d:%02d:%02d", TimeUnit.SECONDS.toHours(seconds),
				TimeUnit.SECONDS.toMinutes(seconds) - TimeUnit.HOURS.toMinutes(TimeUnit.SECONDS.toHours(seconds)),
				TimeUnit.SECONDS.toSeconds(seconds) - TimeUnit.MINUTES.toSeconds(TimeUnit.SECONDS.toMinutes(seconds)));
	}
}