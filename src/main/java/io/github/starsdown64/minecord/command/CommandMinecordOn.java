package io.github.starsdown64.minecord.command;

import io.github.starsdown64.minecord.MinecordPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class CommandMinecordOn implements CommandExecutor
{
    private final MinecordPlugin master;

    public CommandMinecordOn(MinecordPlugin master)
    {
        this.master = master;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
    {
        if (!sender.hasPermission("minecord.integration.toggle"))
            return false;
        if (master.getIntegration())
        {
            sender.sendMessage("Integration was already on.");
            return true;
        }
        master.setIntegration(true);
        master.getServer().broadcastMessage("[Minecord] Integration has been turned on.");
        master.getSlave().send("Integration has been turned on.");
        return true;
    }
}