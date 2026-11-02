package com.solarrabbit.largeraids.trigger;

import java.util.Map.Entry;

import javax.annotation.Nullable;

import com.solarrabbit.largeraids.LargeRaids;
import com.solarrabbit.largeraids.event.LargeRaidTriggerEvent;
import com.solarrabbit.largeraids.util.ChatColorUtil;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class TriggerManager implements Listener {
    private final LargeRaids plugin;
    private CommandSender lastTriggerer;

    public TriggerManager(LargeRaids plugin) {
        this.plugin = plugin;
    }

    public void setLastTriggerer(CommandSender triggerer) {
        this.lastTriggerer = triggerer;
    }

    @Nullable
    public CommandSender getLastTriggerer() {
        return this.lastTriggerer;
    }

    @EventHandler
    public void onLargeRaidTrigger(LargeRaidTriggerEvent evt) {
        if (plugin.getTriggerConfig().isArtificialOnly()) {
            String centerName = getCenterName(evt.getLargeRaid().getCenter());
            if (centerName == null) {
                evt.setCancelled(true);
                return;
            }
            String broadcast = plugin.getTriggerConfig().getBroadcastMessage(lastTriggerer, centerName);
            if (broadcast != null && !broadcast.isEmpty()) {
                Bukkit.broadcast(ChatColorUtil.parse(broadcast));
            }
        }
    }

    @Nullable
    private String getCenterName(Location location) {
        return plugin.getDatabaseAdapter().getCentres().entrySet().stream()
                .filter(entry -> location.getWorld().equals(entry.getValue().getWorld()))
                .filter(entry -> entry.getValue().distanceSquared(location) < 4096.0) // 64^2
                .map(Entry::getKey)
                .findFirst().orElse(null);
    }
}