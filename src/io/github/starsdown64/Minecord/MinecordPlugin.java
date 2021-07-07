package io.github.starsdown64.Minecord;

import java.util.LinkedList;
import java.util.Locale;

import javax.security.auth.login.LoginException;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.craftbukkit.v1_17_R1.advancement.CraftAdvancement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.RemoteServerCommandEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Team;
import org.json.simple.parser.JSONParser;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.ParseException;

import de.myzelyam.api.vanish.VanishAPI;

import net.dv8tion.jda.api.utils.MarkdownSanitizer;

import io.github.starsdown64.Minecord.api.ExternalMessageEvent;
import io.github.starsdown64.Minecord.command.CommandMinecordOff;
import io.github.starsdown64.Minecord.command.CommandMinecordOn;

public final class MinecordPlugin extends JavaPlugin implements Listener
{
	private FileConfiguration config = getConfig();
	private final Object syncSleep = new Object();
	private final Object syncListM2D = new Object();
	private final Object syncListD2M = new Object();
	private final LinkedList<String> listM2D = new LinkedList<>();
	private final LinkedList<String> listD2M = new LinkedList<>();
	private final boolean noDeathMessages = config.getBoolean("noDeathMessages");
	private final boolean noJoinQuitMessages = config.getBoolean("noJoinQuitMessages");
	private final boolean noAdvancementMessages = config.getBoolean("noAdvancementMessages");
	private final boolean allowExternalMessages = config.getBoolean("allowExternalMessages");
	private final long historyAmount = config.getLong("historyAmount");
	private DiscordSlave slave;
	private Thread thread;
	private boolean running = true;
	private boolean update = false;
	private volatile boolean integrate = true;
	private volatile boolean connected = false;
	private volatile long lastConnected = 0;
	private boolean hasVanish;
	
	@Override
	public final void onEnable()
	{
		saveDefaultConfig();
		getCommand("minecord_on").setExecutor(new CommandMinecordOn(this));
		getCommand("minecord_off").setExecutor(new CommandMinecordOff(this));
		hasVanish = getServer().getPluginManager().isPluginEnabled("SuperVanish") || getServer().getPluginManager().isPluginEnabled("PremiumVanish");
		slave = new DiscordSlave(this);
		thread = new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				boolean loggedIn = false;
				try
				{
					slave.start();
					loggedIn = true;
					connected = true;
				}
				catch (LoginException exception)
				{
					exception.printStackTrace();
					return;
				}
				catch (NumberFormatException exception)
				{
					exception.printStackTrace();
					return;
				}
				
				String message;
				OUTER: while (true)
				{
					synchronized (syncSleep)
					{
						while (!update)
						{
							if (!running)
								break OUTER;
							try
							{
								syncSleep.wait();
							}
							catch (InterruptedException exception)
							{
								exception.printStackTrace();
							}
						}
						update = false;
					}
					while (true)
					{
						synchronized (syncListM2D)
						{
							if (!connected)
								break;
							if (listM2D.isEmpty())
								break;
							message = listM2D.removeFirst();
						}
						if (loggedIn && integrate)
							slave.send(message);
					}
					while (true)
					{
						synchronized (syncListD2M)
						{
							if (listD2M.isEmpty())
								break;
							message = listD2M.removeFirst();
						}
						if (integrate)
							getServer().broadcastMessage(message);
					}
					
				}
				synchronized (syncListM2D)
				{
					listM2D.clear();
				}
				synchronized (syncListD2M)
				{
					listD2M.clear();
				}
				
				slave.stop();
			}
		});
		thread.start();
		getServer().getPluginManager().registerEvents(this, this);
		if (hasVanish)
			getServer().getPluginManager().registerEvents(new SuperVanishListener(this), this);
	}
	
	@Override
	public final void onDisable()
	{
		slave.send("Minecord has shut down.");
		synchronized (syncSleep)
		{
			running = false;
			syncSleep.notify();
		}
	}
	
	public final void setIntegration(boolean integrate)
	{
		this.integrate = integrate;
	}
	
	public final boolean getIntegration()
	{
		return integrate;
	}
	
	public final void setConnected(boolean connected)
	{
		this.connected = connected;
	}
	
	public final boolean getConnected()
	{
		return connected;
	}
	
	public final void setLastConnected(long lastConnected)
	{
		this.lastConnected = lastConnected;
	}
	
	public final long getLastConnected()
	{
		if (connected)
			lastConnected = System.currentTimeMillis();
		return lastConnected;
	}
	
	public final DiscordSlave getSlave()
	{
		return slave;
	}
	
	public final FileConfiguration getConfigFile()
	{
		return config;
	}
	
	public final void printToMinecraft(String message)
	{
		synchronized (syncSleep)
		{
			synchronized (syncListD2M)
			{
				listD2M.addLast(message);
			}
			update = true;
			syncSleep.notify();
		}
	}
	
	public final void printToDiscord(String message)
	{
		message = ChatColor.stripColor(message);
		if (message == null || message.isEmpty())
			return;
		if (!connected && listM2D.size() >= historyAmount)
			return;
		synchronized (syncSleep)
		{
			synchronized (syncListM2D)
			{
				listM2D.addLast(message);
			}
			update = true;
			syncSleep.notify();
		}
	}
	
	/**
	 * Send a message to discord bypassing restrictions.
	 * This method is meant to provide debug or system info only.
	 * Do not use this for normal messages.
	 * 
	 * @param message The debug or system message to send
	 */
	public final void printToDiscordBypass(String message)
	{
		if (message == null || message.isEmpty())
			return;
		synchronized (syncSleep)
		{
			synchronized (syncListM2D)
			{
				listM2D.addLast(message);
			}
			update = true;
			syncSleep.notify();
		}
	}
	
	public final String getTabMenu()
	{
		String output = "**Players Online:**\n```\n";
		for (Player p : getServer().getOnlinePlayers())
		{
			if (isVanished(p))
				continue;
			output += ChatColor.stripColor(teamedName(p)) + "\n";
		}
		output += "```";
		return (output.equals("**Players Online:**\n```\n```")) ? "**No players online**" : output;
	}
	
	private final boolean isVanished(Player p)
	{
		if (hasVanish)
			return VanishAPI.isInvisible(p);
		else
		{
			for (MetadataValue meta : p.getMetadata("vanished"))
			{
				if (meta.asBoolean())
					return true;
			}
			return false;
		}
	}
	
	private final String teamedName(Player p)
	{
		Team t = p.getScoreboard().getEntryTeam(p.getName());
		String prefix = (t == null) ? "" : t.getPrefix();
		String suffix = (t == null) ? "" : t.getSuffix();
		return prefix + p.getName() + suffix;
	}
	
	private final String parseJSONMessage(String message)
	{
		if (message == null)
			return null;
		
		try
		{
			JSONParser parser = new JSONParser();
			Object document = parser.parse(message);
			return document == null ? null : extractJSONMessage(document);
		}
		catch (ParseException e)
		{
			return null;
		}
	}
	
	private final String extractJSONMessage(Object element)
	{
		if (element == null)
			return "";
		
		try
		{
			if (element instanceof String)
				return (String) element;
			if (element instanceof Number)
				return ((Number) element).toString();
			if (element instanceof Boolean)
				return ((Boolean) element).toString();
			if (element instanceof JSONObject)
				return extractJSONMessage((JSONObject) element);
			if (element instanceof JSONArray)
				return extractJSONMessage((JSONArray) element);
		}
		catch (Error e)
		{
			return null;
		}
		
		return "";
	}
	
	private final String extractJSONMessage(JSONObject object)
	{
		String output = "";
		
		if (object.containsKey("text"))
		{
			Object nested = object.get("text");
			
			if (nested instanceof String || nested instanceof Number || nested instanceof Boolean)
				output += extractJSONMessage(nested);
			else
				throw new Error();
		}
		
		if (object.containsKey("extra"))
		{
			Object extra = object.get("extra");
			
			if (extra instanceof JSONArray)
				output += extractJSONMessage((JSONArray) extra);
			else
				throw new Error();
		}
		
		if (object.containsKey("bold") && Boolean.TRUE.equals(object.get("bold")))
			output = "**" + output + "**";
		if (object.containsKey("italic") && Boolean.TRUE.equals(object.get("italic")))
			output = "*" + output + "*";
		if (object.containsKey("underlined") && Boolean.TRUE.equals(object.get("underlined")))
			output = "__" + output + "__";
		if (object.containsKey("strikethrough") && Boolean.TRUE.equals(object.get("strikethrough")))
			output = "~~" + output + "~~";
		if (object.containsKey("obfuscated") && Boolean.TRUE.equals(object.get("obfuscated")))
			output = "||" + output + "||";
			
		return output;
	}
	
	private final String extractJSONMessage(JSONArray array)
	{
		String output = "";
		
		for (Object child : array)
			output += extractJSONMessage(child);
		
		return output;
	}
	
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public final void onChat(AsyncPlayerChatEvent event)
	{
		printToDiscord("<" + MarkdownSanitizer.escape(event.getPlayer().getName()) + "> " + event.getMessage());
	}
	
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public final void onCommand(PlayerCommandPreprocessEvent event)
	{
		String command = event.getMessage();
		String commandLowerCase = command.toLowerCase(Locale.ROOT);
		if (commandLowerCase.startsWith("/say "))
		{
			if (!event.getPlayer().hasPermission("minecraft.command.say"))
				return;
			String message = command.substring(5);
			printToDiscord("[" + MarkdownSanitizer.escape(teamedName(event.getPlayer())) + "] " + message);
		}
		else if (commandLowerCase.startsWith("/me "))
		{
			if (!event.getPlayer().hasPermission("minecraft.command.me"))
				return;
			String message = command.substring(4);
			printToDiscord("* " + MarkdownSanitizer.escape(teamedName(event.getPlayer())) + " " + message);
		}
		else if (commandLowerCase.startsWith("/tellraw @a "))
		{
			if (!event.getPlayer().hasPermission("minecraft.command.tellraw"))
				return;
			String message = command.substring(12);
			message = parseJSONMessage(message);
			printToDiscord(message);
		}
		else if (commandLowerCase.startsWith("/sv login ") || commandLowerCase.equals("/sv login"))
		{
			if (!event.getPlayer().hasPermission("sv.login") || !hasVanish)
				return;
			onJoin(new PlayerJoinEvent(event.getPlayer(), event.getPlayer().getName() + " joined the game"));
		}
		else if (commandLowerCase.startsWith("/sv logout ") || commandLowerCase.equals("/sv logout"))
		{
			if (!event.getPlayer().hasPermission("sv.logout") || !hasVanish)
				return;
			onQuit(new PlayerQuitEvent(event.getPlayer(), event.getPlayer().getName() + " left the game"));
		}
	}
	
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public final void onCommand(ServerCommandEvent event)
	{
		String command = event.getCommand();
		String commandLowerCase = command.toLowerCase(Locale.ROOT);
		if (commandLowerCase.startsWith("say "))
		{
			String message = command.substring(4);
			printToDiscord("[Server] " + message);
		}
		else if (commandLowerCase.startsWith("me "))
		{
			String message = command.substring(3);
			printToDiscord("* Server " + message);
		}
		else if (commandLowerCase.startsWith("tellraw @a "))
		{
			String message = command.substring(11);
			message = parseJSONMessage(message);
			printToDiscord(message);
		}
	}
	
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public final void onCommand(RemoteServerCommandEvent event)
	{
		String command = event.getCommand();
		String commandLowerCase = command.toLowerCase(Locale.ROOT);
		if (commandLowerCase.startsWith("say "))
		{
			String message = command.substring(4);
			printToDiscord("[Server] " + message);
		}
		else if (commandLowerCase.startsWith("me "))
		{
			String message = command.substring(3);
			printToDiscord("* Server " + message);
		}
		else if (commandLowerCase.startsWith("tellraw @a "))
		{
			String message = command.substring(11);
			message = parseJSONMessage(message);
			printToDiscord(message);
		}
	}
	
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public final void onPlayerDeath(PlayerDeathEvent event)
	{
		if (noDeathMessages || event.getDeathMessage() == null)
			return;
		printToDiscord(MarkdownSanitizer.escape(event.getDeathMessage()));
	}
	
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public final void onJoin(PlayerJoinEvent event)
	{
		if (noJoinQuitMessages || event.getJoinMessage() == null)
			return;
		printToDiscord(MarkdownSanitizer.escape(event.getJoinMessage().toString()));
	}
	
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public final void onQuit(PlayerQuitEvent event)
	{
		if (noJoinQuitMessages || event.getQuitMessage() == null)
			return;
		printToDiscord(MarkdownSanitizer.escape(event.getQuitMessage()));
	}
	
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public final void onAdvancement(PlayerAdvancementDoneEvent event)
	{
		if (noAdvancementMessages ||
			event.getAdvancement() == null ||
			event.getAdvancement().getKey().getKey().contains("recipe/") ||
			event.getPlayer() == null ||
			((CraftAdvancement) event.getAdvancement()).getHandle().c() == null ||
			event.getAdvancement().getKey().toString().contains("root"))
			return;
		String advancement = ((CraftAdvancement) event.getAdvancement()).getHandle().c().a().getString();
		String type = ((CraftAdvancement) event.getAdvancement()).getHandle().c().e().a();
		if (type.equals("challenge"))
			printToDiscord(MarkdownSanitizer.escape(event.getPlayer().getName()) + " has completed the challenge [" + advancement + "]");
		else if (type.equals("goal"))
			printToDiscord(MarkdownSanitizer.escape(event.getPlayer().getName()) + " has reached the goal [" + advancement + "]");
		else if (type.equals("task"))
			printToDiscord(MarkdownSanitizer.escape(event.getPlayer().getName()) + " has made the advancement [" + advancement + "]");
	}
	
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public final void onExternalMessage(ExternalMessageEvent event)
	{
		if (!allowExternalMessages)
			return;
		printToDiscord(event.getMessage());
	}
}