package com.solarrabbit.largeraids.command;

import com.solarrabbit.largeraids.LargeRaids;
import com.solarrabbit.largeraids.util.ChatColorUtil;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ReloadPluginCommand implements CommandExecutor {
    private final LargeRaids plugin;

    public ReloadPluginCommand(LargeRaids plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        this.plugin.reload();
        ChatColorUtil.send(sender, "<gold>[LargeRaids] <green>Successfully reloaded configurations!");
        return true;
    }
}