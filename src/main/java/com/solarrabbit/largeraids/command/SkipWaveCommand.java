package com.solarrabbit.largeraids.command;

import com.solarrabbit.largeraids.LargeRaids;
import com.solarrabbit.largeraids.raid.LargeRaid;
import com.solarrabbit.largeraids.raid.RaidManager;
import com.solarrabbit.largeraids.util.ChatColorUtil;

import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SkipWaveCommand implements CommandExecutor {
    private final LargeRaids plugin;
    private final RaidManager manager;

    public SkipWaveCommand(LargeRaids plugin) {
        this.plugin = plugin;
        manager = plugin.getRaidManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatColorUtil.send(sender, NamedTextColor.RED, this.plugin.getMessage("sender-player"));
            return false;
        }

        Location loc = player.getLocation();
        Optional<LargeRaid> raid = manager.getLargeRaid(loc);
        if (raid.isPresent()) {
            if (raid.get().isLastWave()) {
                ChatColorUtil.send(sender, NamedTextColor.GOLD, this.plugin.getMessage("skip-wave.last-wave"));
                return true;
            }
            manager.setIdle();
            raid.get().skipWave();
            manager.setActive();
            ChatColorUtil.send(sender, NamedTextColor.GREEN, String.format(this.plugin.getMessage("skip-wave.skip-success"),
                    raid.get().getCurrentWave() - 1, raid.get().getTotalWaves()));
            return true;
        }
        ChatColorUtil.send(sender, NamedTextColor.RED, this.plugin.getMessage("skip-wave.no-large-raid"));
        return true;
    }
}