package io.github.starsdown64.minecord.command;

import io.github.starsdown64.minecord.MinecordPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class CommandMinecordOff implements CommandExecutor {
    private final MinecordPlugin master;

    public CommandMinecordOff(MinecordPlugin master)
    {
        this.master = master;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
    {
        if (!sender.hasPermission("minecord.integration.toggle"))
            return false;
        if (!master.getIntegration())
        {
            sender.sendMessage("Integration was already off.");
            return true;
        }
        master.setIntegration(false);
        master.getServer().broadcastMessage("[Minecord] Integration has been turned off.");
        master.getSlave().send("Integration has been turned off.");
        return true;
    }
}