package org.imaginecraft.apocalypse.events;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import org.imaginecraft.apocalypse.Apocalypse;
import org.imaginecraft.apocalypse.config.ConfigOption;
import org.imaginecraft.apocalypse.teams.ApocTeam;
import org.imaginecraft.apocalypse.tools.ApocTools;

/**
 * Instance of the queued or currently active event.
 */
public class ApocEvent implements ConfigurationSerializable {
	
	private final Apocalypse plugin = JavaPlugin.getPlugin(Apocalypse.class);
	private final ApocTools tools = plugin.getApocTools();
	
	private Map<UUID, ApocChatType> inChat = new HashMap<UUID, ApocChatType>();
	
	private Set<ApocBoss> bosses = new HashSet<ApocBoss>();
	private Set<ApocSiege> sieges = new HashSet<ApocSiege>();
	private Set<ApocTeam> teams = new HashSet<ApocTeam>();
	
	private final EnumSet<ChatColor> colors = EnumSet.of(ChatColor.AQUA, ChatColor.BLUE, ChatColor.DARK_AQUA, ChatColor.DARK_BLUE,
			ChatColor.DARK_GREEN, ChatColor.DARK_PURPLE, ChatColor.DARK_RED, ChatColor.GOLD, ChatColor.GREEN, ChatColor.LIGHT_PURPLE,
			ChatColor.RED, ChatColor.YELLOW);

	private final Random random = new Random();
	
	private boolean active = false, started = false;
	private long duration = 0L, endTime = 0L, warningTime = 0L;
	private int warnings = 0;
	private Scoreboard scoreboard;
	private Objective objective;
	private World world;
	
	public ApocEvent() {
		scoreboard = plugin.getServer().getScoreboardManager().getNewScoreboard();
		objective = scoreboard.registerNewObjective("Points", "dummy");
		objective.setDisplaySlot(DisplaySlot.SIDEBAR);
	}
	
	/**
	 * TODO
	 * @param boss
	 */
	public void addBoss(ApocBoss boss) {
		bosses.add(boss);
	}
	
	/**
	 * TODO
	 * @param siege
	 */
	public void addSiege(ApocSiege siege) {
		sieges.add(siege);
	}
	
	/**
	 * TODO
	 * @param team
	 */
	public void addTeam(ApocTeam team) {
		teams.add(team);
	}
	
	// Assigns each team an initial spawn point
	private void createSpawns() {
		double min = ConfigOption.TEAMS_MINIMUM_SPAWNING_DISTANCE,
				range = Math.min(ConfigOption.TEAMS_MAXIMUM_SPAWNING_DISTANCE, world.getWorldBorder().getSize());
		Location center = world.getHighestBlockAt(world.getWorldBorder().getCenter()).getLocation();
		for (ApocTeam team1 : teams) {
			for (ApocTeam team2 : teams) {
				if (!team1.getName().equalsIgnoreCase("test")
						&& !team2.getName().equalsIgnoreCase("test")) {
					team1.setSpawn(tools.getRandomLocation(center, range));
					if (team2.getSpawn() != null
							&& team1 != team2) {
						while (team1.getSpawn().distanceSquared(team2.getSpawn()) < min * min) {
							team1.setSpawn(tools.getRandomLocation(center, range));
						}
					}
				}
			}
		}
	}
	
	/**
	 * TODO
	 * @param name
	 * @return
	 */
	public ApocTeam createTeam(String name) {
		ChatColor color = null;
		if (name.startsWith("&")) name = name.replaceFirst("&", String.valueOf(ChatColor.COLOR_CHAR));
		if (name.startsWith(String.valueOf(ChatColor.COLOR_CHAR))) {
			String prefix = name.substring(0, 2);
			name = name.substring(2);
			if (ChatColor.getByChar(prefix) != null) {
				color = ChatColor.getByChar(prefix);
				if (color.isColor()
						&& colors.contains(color)) {
					colors.remove(color);
				}
			}
		}
		ApocTeam team = new ApocTeam(color != null ? color : getAvailableTeamColor(), name);
		addTeam(team);
		return team;
	}
	
	/**
	 * TODO
	 * @return
	 */
	public Set<OfflinePlayer> getAllPlayers() {
		Set<OfflinePlayer> players = new HashSet<OfflinePlayer>();
		for (ApocTeam team : teams) {
			players.addAll(team.getPlayers());
		}
		return players;
	}
	
	/**
	 * Returns the smallest team that has enough room. Returns null if all teams are full or no teams exist.
	 */
	public ApocTeam getAvailableTeam() {
		ApocTeam newTeam = null;
		int i = Integer.MAX_VALUE;
		for (ApocTeam team : teams) {
			if (team.getSize() < i
					&& (team.getSize() < ConfigOption.TEAMS_MAXIMUM_MEMBERS
							|| !ConfigOption.TEAMS_ENFORCE_MAXIMUM_MEMBERS)) {
				i = team.getSize();
				newTeam = team;
			}
		}
		return newTeam;
	}
	
	// Find a team color that isn't in use yet
	private ChatColor getAvailableTeamColor() {
		int index = random.nextInt(colors.size());
		Iterator<ChatColor> iter = colors.iterator();
		for (int i = 0; i < index; i ++) {
			iter.next();
		}
		ChatColor color = iter.next();
		colors.remove(color);
		return color;
	}
	
	/**
	 * TODO
	 * @param name
	 * @return
	 */
	public ApocBoss getBoss(String name) {
		for (ApocBoss boss : bosses) {
			if (boss.getName().equalsIgnoreCase(name)) return boss;
		}
		return null;
	}
	
	/**
	 * TODO
	 * @return
	 */
	public Set<ApocBoss> getBosses() {
		return bosses;
	}
	
	/**
	 * TODO
	 * @return
	 */
	public long getEndTime() {
		return endTime;
	}
	
	/**
	 * TODO
	 * @return
	 */
	public Objective getObjective() {
		return objective;
	}
	
	/**
	 * TODO
	 */
	public ApocTeam getPlayerTeam(OfflinePlayer player) {
		for (ApocTeam team : teams) {
			if (team.hasPlayer(player.getUniqueId())) {
				return team;
			}
		}
		return null;
	}
	
	/**
	 * TODO
	 * @return
	 */
	public Scoreboard getScoreboard() {
		if (scoreboard == null) {
			scoreboard = plugin.getServer().getScoreboardManager().getNewScoreboard();
		}
		return scoreboard;
	}
	
	/**
	 * TODO
	 * @param name
	 * @return
	 */
	public ApocSiege getSiege(String name) {
		for (ApocSiege siege : sieges) {
			if (siege.getName().equalsIgnoreCase(name)) return siege;
		}
		return null;
	}
	
	/**
	 * TODO
	 * @return
	 */
	public Set<ApocSiege> getSieges() {
		return sieges;
	}
	
	/**
	 * TODO
	 * @param name
	 * @return
	 */
	public ApocTeam getTeam(String name) {
		if (name.startsWith("&") || name.startsWith(String.valueOf(ChatColor.COLOR_CHAR))) {
			name = name.substring(2);
		}
		for (ApocTeam team : teams) {
			if (team.getName().equalsIgnoreCase(name)) {
				return team;
			}
		}
		return null;
	}
	
	/**
	 * TODO
	 * @return
	 */
	public Set<ApocTeam> getTeams() {
		return teams;
	}
	
	/**
	 * TODO
	 * @return
	 */
	public World getWorld() {
		return world;
	}
	
	/**
	 * TODO
	 */
	public ApocChatType getChatType(OfflinePlayer player) {
		if (!inChat.containsKey(player.getUniqueId())) {
			inChat.put(player.getUniqueId(), ApocChatType.OFF);
		}
		return inChat.get(player.getUniqueId());
	}
	
	/**
	 * TODO
	 * @return
	 */
	public boolean hasStarted() {
		return started;
	}
	
	/**
	 * TODO
	 * @return
	 */
	public boolean isActive() {
		return active;
	}
	
	/**
	 * TODO
	 * @param team
	 */
	public void removeTeam(ApocTeam team) {
		for (OfflinePlayer player : team.getPlayers()) {
			inChat.remove(player.getUniqueId());
		}
		team.remove();
		colors.add(team.getColor());
		teams.remove(team);
	}
	
	/**
	 * TODO
	 */
	public void setChatType(OfflinePlayer player, ApocChatType type) {
		inChat.put(player.getUniqueId(), type);
	}
	
	/**
	 * TODO
	 * @param duration
	 */
	public void setDuration(long duration) {
		this.duration = duration;
	}
	
	/**
	 * TODO
	 */
	public void setEndTime(long endTime) {
		this.endTime = endTime;
	}
	
	/**
	 * TODO
	 */
	public void setStarted(boolean started) {
		this.started = started;
	}
	
	/**
	 * TODO
	 * @param world
	 */
	public void setWorld(World world) {
		this.world = world;
	}
	
	// Start the event after warnings have finished
	private void startEvent() {
		active = true;
		started = true;
		duration = ConfigOption.EVENT_DURATION;
		endTime = System.currentTimeMillis() + duration;
		createSpawns();
		for (ApocTeam team : teams) {
			for (OfflinePlayer player : team.getPlayers()) {
				if (player.isOnline()) {
					((Player)player).teleport(team.getSpawn());
					((Player)player).setScoreboard(scoreboard);
				}
			}
		}
		// TODO
	}
	
	/**
	 * TODO
	 */
	public void startWarning() {
		warnings = ConfigOption.EVENT_WARNING_NOTIFICATIONS;
		warningTime = ConfigOption.EVENT_WARNING_TIME;
		for (int i = 0; i <= warnings; i ++) {
			double dbl1 = (double)i / (double)warnings;
			double dbl2 = dbl1 * (double)warningTime;
			long wTime = (long) dbl2;
			new BukkitRunnable() {
				@SuppressWarnings("deprecation")
				@Override
				public void run() {
					if (wTime > 0L) {
						for (OfflinePlayer player : getAllPlayers()) {
							if (player.isOnline()) {
								((Player) player).sendTitle(null, ChatColor.GOLD + "The Apocalypse will begin in " + tools.getTime(wTime) + ".");
							}
						}
					}
					else {
						for (OfflinePlayer player : getAllPlayers()) {
							if (player.isOnline()) {
								((Player) player).sendTitle(ChatColor.GOLD + "The Apocalypse has begun!", null);
							}
						}
						startEvent();
					}
				}
			}.runTaskLater(plugin, tools.getTicks(warningTime - wTime));
		}
	}
	
	public static ApocEvent deserialize(Map<String, Object> args) {
		ApocEvent event = new ApocEvent();
		event.setStarted((boolean) args.get("started"));
		event.setEndTime((int)args.get("end-time"));
		if (args.containsKey("world")) event.setWorld(Bukkit.getServer().getWorld(UUID.fromString((String) args.get("world"))));
		return event;
	}

	@Override
	public Map<String, Object> serialize() {
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("started", started);
		result.put("end-time", endTime);
		if (world != null) result.put("world", world.getUID().toString());
		return result;
	}
	
}
