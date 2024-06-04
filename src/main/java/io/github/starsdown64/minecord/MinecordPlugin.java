package io.github.starsdown64.minecord;

import com.google.gson.*;
import de.myzelyam.api.vanish.VanishAPI;
import io.github.starsdown64.minecord.api.ExternalMessageEvent;
import io.github.starsdown64.minecord.command.CommandMinecordOff;
import io.github.starsdown64.minecord.command.CommandMinecordOn;
import io.github.starsdown64.minecord.listeners.SuperVanishListener;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementDisplay;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.server.RemoteServerCommandEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Team;

import javax.security.auth.login.LoginException;
import java.util.LinkedList;
import java.util.Locale;

public class MinecordPlugin extends JavaPlugin implements Listener
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
        new Thread(new Runnable()
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
                catch (LoginException | NumberFormatException exception)
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
                            if (!connected || listM2D.isEmpty())
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
        }).start();
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
        String strippedMessage = ChatColor.stripColor(message);
        if (strippedMessage == null || strippedMessage.isEmpty())
            return;
        if (!connected && listM2D.size() >= historyAmount)
            return;
        synchronized (syncSleep)
        {
            synchronized (syncListM2D)
            {
                listM2D.addLast(strippedMessage);
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

    public final String getFormattedTabMenu()
    {
        StringBuilder output = new StringBuilder("**Players Online:**\n```\n");
        for (Player p : getServer().getOnlinePlayers())
        {
            if (isVanished(p))
                continue;
            output.append(ChatColor.stripColor(teamedName(p))).append("\n");
        }
        output.append("```");
        return output.toString().equals("**Players Online:**\n```\n```") ? "**No players online**" : output.toString();
    }

    private final boolean isVanished(Player p)
    {
        if (hasVanish)
            return VanishAPI.isInvisible(p);
        else
        {
            for (MetadataValue meta : p.getMetadata("vanished"))
                if (meta.asBoolean())
                    return true;
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
            JsonElement document = JsonParser.parseString(message);
            return document == null ? null : extractJSONMessage(document);
        }
        catch (JsonSyntaxException e)
        {
            return null;
        }
    }

    private final String extractJSONMessage(JsonElement element)
    {
        if (element.isJsonNull())
            return "";

        try
        {
            if (element.isJsonPrimitive())
                return extractJSONMessage(element.getAsJsonPrimitive());
            if (element.isJsonObject())
                return extractJSONMessage(element.getAsJsonObject());
            if (element.isJsonArray())
                return extractJSONMessage(element.getAsJsonArray());
        }
        catch (Error e)
        {
            return null;
        }

        return "";
    }

    private final String extractJSONMessage(JsonPrimitive primitive)
    {
        if (primitive.isString())
            return primitive.getAsString();
        if (primitive.isNumber())
            return primitive.getAsNumber().toString();
        if (primitive.isBoolean())
            return Boolean.toString(primitive.getAsBoolean());

        throw new Error();
    }

    private final String extractJSONMessage(JsonObject object)
    {
        String output = "";

        if (object.has("text"))
        {
            JsonElement nested = object.get("text");
            if (!nested.isJsonPrimitive())
                throw new Error();
            output += extractJSONMessage(nested.getAsJsonPrimitive());
        }

        if (object.has("extra"))
        {
            JsonElement nested = object.get("extra");
            if (!nested.isJsonArray())
                throw new Error();
            output += extractJSONMessage(nested.getAsJsonArray());
        }

        if (object.has("bold"))
        {
            JsonElement nested = object.get("bold");
            if (!nested.isJsonPrimitive())
                throw new Error();
            if (Boolean.TRUE.equals(nested.getAsBoolean()))
                output = "**" + output + "**";
        }

        if (object.has("italic"))
        {
            JsonElement nested = object.get("italic");
            if (!nested.isJsonPrimitive())
                throw new Error();
            if (Boolean.TRUE.equals(nested.getAsBoolean()))
                output = "*" + output + "*";
        }

        if (object.has("underlined"))
        {
            JsonElement nested = object.get("underlined");
            if (!nested.isJsonPrimitive())
                throw new Error();
            if (Boolean.TRUE.equals(nested.getAsBoolean()))
                output = "__" + output + "__";
        }

        if (object.has("strikethrough"))
        {
            JsonElement nested = object.get("strikethrough");
            if (!nested.isJsonPrimitive())
                throw new Error();
            if (Boolean.TRUE.equals(nested.getAsBoolean()))
                output = "~~" + output + "~~";
        }

        if (object.has("obfuscated"))
        {
            JsonElement nested = object.get("obfuscated");
            if (!nested.isJsonPrimitive())
                throw new Error();
            if (Boolean.TRUE.equals(nested.getAsBoolean()))
                output = "||" + output + "||";
        }

        return output;
    }

    private final String extractJSONMessage(JsonArray array)
    {
        StringBuilder output = new StringBuilder();

        for (JsonElement child : array)
            output.append(extractJSONMessage(child));

        return output.toString();
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
            printToDiscord("[" + event.getSender().getName() + "] " + message);
        }
        else if (commandLowerCase.startsWith("me "))
        {
            String message = command.substring(3);
            printToDiscord("* " + event.getSender().getName() + " " + message);
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
            printToDiscord("[" + event.getSender().getName() + "] " + message);
        }
        else if (commandLowerCase.startsWith("me "))
        {
            String message = command.substring(3);
            printToDiscord("* " + event.getSender().getName() + " " + message);
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
        printToDiscord(MarkdownSanitizer.escape(event.getJoinMessage()));
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
        if (noAdvancementMessages)
            return;
        final Advancement advancement = event.getAdvancement();
        final AdvancementDisplay display = advancement.getDisplay();
        if (display == null || !display.shouldAnnounceChat())
            return;
        final NamespacedKey namespacedKey = advancement.getKey();
        if (namespacedKey.getKey().contains("recipe/") || namespacedKey.toString().contains("/root"))
            return;
        final String intermediate;
        switch (display.getType())
        {
            case CHALLENGE:    intermediate = " has completed the challenge ["; break;
            case GOAL:        intermediate = " has reached the goal ["; break;
            case TASK:        intermediate = " has made the advancement ["; break;
            default:
                return;
        }
        printToDiscord(MarkdownSanitizer.escape(event.getPlayer().getName()) + intermediate + display.getTitle() + "]");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public final void onExternalMessage(ExternalMessageEvent event)
    {
        if (!allowExternalMessages)
            return;
        printToDiscord(event.getMessage());
    }
}