package io.github.starsdown64.Minecord;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.security.auth.login.LoginException;

import org.bukkit.ChatColor;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.guild.GuildAvailableEvent;
import net.dv8tion.jda.api.events.guild.GuildUnavailableEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;

public final class DiscordSlave extends ListenerAdapter
{
	private final MinecordPlugin master;
	private final String token;
	private final String channelID;
	private final ArrayList<String> minecordIntegrationToggle;
	private final boolean emptyNewlineTruncation;
	private TextChannel channel;
	private JDA discord;
	private volatile boolean prevState = true;
	
	public DiscordSlave(MinecordPlugin master)
	{
		this.master = master;
		this.token = master.getConfigFile().getString("token");
		this.channelID = master.getConfigFile().getString("channelID");
		this.minecordIntegrationToggle = (ArrayList<String>) master.getConfigFile().getList("minecordIntegrationToggle").stream().map(obj -> Objects.toString(obj, null)).collect(Collectors.toList());
		this.emptyNewlineTruncation = master.getConfigFile().getBoolean("emptyNewlineTruncation");
	}
	
	public final void start() throws LoginException, NumberFormatException
	{
		this.discord = JDABuilder.createLight(token, GatewayIntent.GUILD_MESSAGES)
			.addEventListeners(this)
			.build();
		while (true)
		{
			try
			{
				discord.awaitReady();
				break;
			}
			catch (InterruptedException exception)
			{
				exception.printStackTrace();
			}
		}
		this.channel = discord.getTextChannelById(channelID);
	}
	
	public final void stop()
	{
		discord.setAutoReconnect(false);
		discord.shutdown();
	}
	
	public final void send(String message)
	{
		channel.sendMessage(message).queue();
	}
	
	@Override
	public void onMessageReceived(MessageReceivedEvent event)
	{
		Message message = event.getMessage();
		if (!message.getChannel().getId().equals(channelID) || message.getAuthor().isBot())
			return;
		String content = event.getMessage().getContentDisplay();
		
		// Command handler
		if (content.startsWith("%"))
		{
			if (content.substring(1).equalsIgnoreCase("on"))
			{
				boolean perm = false;
				for (String id : minecordIntegrationToggle)
					if (event.getAuthor().getId().equals(id))
						perm = true;
				if (!perm)
				{
					message.getChannel().sendMessage(event.getAuthor().getAsMention() + ", you do not have permission to toggle integration.").queue();
					return;
				}
				if (master.getIntegration())
				{
					message.getChannel().sendMessage(event.getAuthor().getAsMention() + ", integration is already on.").queue();
					return;
				}
				master.setIntegration(true);
				message.getChannel().sendMessage(event.getAuthor().getAsMention() + ", integration has been turned on.").queue();
				master.getServer().broadcastMessage("[Minecord] Integration has been turned on.");
				return;
			}
			else if (content.substring(1).equalsIgnoreCase("off"))
			{
				boolean perm = false;
				for (String id : minecordIntegrationToggle)
					if (event.getAuthor().getId().equals(id))
						perm = true;
				if (!perm)
				{
					message.getChannel().sendMessage(event.getAuthor().getAsMention() + ", you do not have permission to toggle integration.").queue();
					return;
				}
				if (!master.getIntegration())
				{
					message.getChannel().sendMessage(event.getAuthor().getAsMention() + ", integration is already off.").queue();
					return;
				}
				master.setIntegration(false);
				message.getChannel().sendMessage(event.getAuthor().getAsMention() + ", integration has been turned off.").queue();
				master.getServer().broadcastMessage("[Minecord] Integration has been turned off.");
				return;
			}
			else if (content.substring(1).equalsIgnoreCase("tab"))
			{
				message.getChannel().sendMessage(master.getTabMenu()).queue();
				return;
			}
			else
			{
				message.getChannel().sendMessage(event.getAuthor().getAsMention() + ", that is not a valid command.").queue();
				return;
			}
		}
		
		
		String author = event.getAuthor().getAsTag();
		if (ChatColor.stripColor(content).replaceAll("§", "").isBlank())
			return;
		if (emptyNewlineTruncation)
		{
			LinkedList<Integer> toRemove = new LinkedList<>();
			LinkedList<String> contentList = new LinkedList<>(Arrays.asList(content.split("\n")));
			for (int i = 0; i < contentList.size(); i++)
				if (!(contentList.size() == i + 1) && contentList.get(i + 1).isBlank() && contentList.get(i).isBlank())
					toRemove.addLast(i);
			while (toRemove.size() > 0)
			{
				contentList.remove(toRemove.remove(0).intValue());
				for (int j = 0; j < toRemove.size(); j++)
					toRemove.set(j, toRemove.get(j) - 1);
			}
			content = String.join("\n", contentList);
		}
		content = content.replaceAll("\n", "\n<" + author + "> ");
		master.printToMinecraft("<" + author + "> " + content);
	}
	
	@Override
	public void onGuildUnavailable(GuildUnavailableEvent event)
	{
		if (event.getGuild().equals(channel.getGuild()))
		{
			prevState = master.getIntegration();
			master.setIntegration(false);
			master.getLogger().warning("Minecord has lost connection to the Guild, integration will resume when reconnected.");
		}
	}
	
	@Override
	public void onGuildAvailable(GuildAvailableEvent event)
	{
		if (event.getGuild().equals(channel.getGuild()))
		{
			master.setIntegration(prevState);
			master.getLogger().info("Minecord has reconnected to the Guild.");
		}
	}
}