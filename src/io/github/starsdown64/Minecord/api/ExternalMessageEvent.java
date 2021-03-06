package io.github.starsdown64.Minecord.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Cancellable;

/**
 * Allows external plugins to send messages to Discord.
 * Can be activated with {@link org.bukkit.plugin.PluginManager#callEvent(Event) Bukkit.getPluginManager().callEvent(Event)}
 * This event's message cannot be changed once called, it can only be cancelled.
 * 
 * @author StarsDown64
 * @version 1.0.0
 * @since 1.4.0
 */
public class ExternalMessageEvent extends Event implements Cancellable
{
	private static final HandlerList handlers = new HandlerList();
	private boolean isCancelled = false;
	private String message;
	
	/**
	 * Allows external plugins to send messages to Discord.
	 * 
	 * @param message the message to be sent.
	 */
	public ExternalMessageEvent(String message)
	{
		this.message = message;
	}
	
	@Override
	public HandlerList getHandlers()
	{
		return handlers;
	}
	
	public static HandlerList getHandlerList()
	{
		return handlers;
	}
	
	public boolean isCancelled()
	{
		return this.isCancelled;
	}
	
	public void setCancelled(boolean cancel)
	{
		this.isCancelled = cancel;
	}
	
	/**
	 * Gets the message that will be sent to Discord.
	 * 
	 * @return the message to be sent.
	 */
	public String getMessage()
	{
		return this.message;
	}
}
