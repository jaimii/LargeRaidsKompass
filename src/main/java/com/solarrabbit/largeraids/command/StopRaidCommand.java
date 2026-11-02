package com.solarrabbit.largeraids.command;

import com.solarrabbit.largeraids.LargeRaids;
import com.solarrabbit.largeraids.raid.LargeRaid;
import com.solarrabbit.largeraids.raid.RaidManager;
import com.solarrabbit.largeraids.util.ChatColorUtil;

import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class StopRaidCommand implements CommandExecutor {
    private final LargeRaids plugin;

    public StopRaidCommand(LargeRaids plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        RaidManager listener = plugin.getRaidManager();
        if (args.length >= 1) {
            if (args.length < 2)
                return false;
            Location location;
            switch (args[0]) {
                case "player":
                    Player player = Bukkit.getPlayer(args[1]);
                    location = player == null ? null : player.getLocation();
                    if (location == null) {
                        ChatColorUtil.send(sender, NamedTextColor.RED, this.plugin.getMessage("stop-raid.player-not-found"));
                        return false;
                    }
                    break;
                case "center":
                    location = plugin.getDatabaseAdapter().getCentre(args[1]);
                    if (location == null) {
                        ChatColorUtil.send(sender, NamedTextColor.RED, this.plugin.getMessage("stop-raid.no-village-centers"));
                        return false;
                    } else if (location.getWorld() == null) {
                        ChatColorUtil.send(sender, NamedTextColor.RED, this.plugin.getMessage("stop-raid.village-center-world-missing"));
                        return false;
                    }
                    break;
                default:
                    return false;
            }
            Optional<LargeRaid> raid = listener.getLargeRaid(location);
            if (raid.isPresent()) {
                listener.setIdle();
                raid.get().stopRaid();
                listener.setActive();
                listener.currentRaids.remove(raid.get());
                listener.saveRaids();
                ChatColorUtil.send(sender, NamedTextColor.GREEN, String.format(this.plugin.getMessage("stop-raid.stopped-raid"),
                        location.getBlockX(), location.getBlockY(), location.getBlockZ()));
            } else {
                ChatColorUtil.send(sender, NamedTextColor.RED, String.format(this.plugin.getMessage("stop-raid.no-large-raid"),
                        location.getBlockX(), location.getBlockY(), location.getBlockZ()));
            }
            return true;
        } else if (sender instanceof Player player) {
            Location location = player.getLocation();
            Optional<LargeRaid> raid = listener.getLargeRaid(location);
            if (raid.isPresent()) {
                listener.setIdle();
                raid.get().stopRaid();
                listener.setActive();
                listener.currentRaids.remove(raid.get());
                listener.saveRaids();
                ChatColorUtil.send(sender, NamedTextColor.GREEN, String.format(this.plugin.getMessage("stop-raid.stopped-raid"),
                        location.getBlockX(), location.getBlockY(), location.getBlockZ()));
            } else {
                ChatColorUtil.send(sender, NamedTextColor.RED, String.format(this.plugin.getMessage("stop-raid.no-large-raid"),
                        location.getBlockX(), location.getBlockY(), location.getBlockZ()));
            }
            return true;
        }
        ChatColorUtil.send(sender, NamedTextColor.RED, this.plugin.getMessage("stop-raid.specify-player"));
        return false;
    }
}