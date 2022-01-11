package io.github.starsdown64.minecord.listeners;

import de.myzelyam.api.vanish.PlayerHideEvent;
import de.myzelyam.api.vanish.PlayerShowEvent;
import io.github.starsdown64.minecord.MinecordPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class SuperVanishListener implements Listener {
    private final MinecordPlugin master;

    public SuperVanishListener(MinecordPlugin master)
    {
        this.master = master;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public final void onVanish(PlayerHideEvent event)
    {
        if (event.isSilent())
            return;
        master.onQuit(new PlayerQuitEvent(event.getPlayer(), event.getPlayer().getName() + " left the game"));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public final void onAppear(PlayerShowEvent event)
    {
        if (event.isSilent())
            return;
        master.onJoin(new PlayerJoinEvent(event.getPlayer(), event.getPlayer().getName() + " joined the game"));
    }
}