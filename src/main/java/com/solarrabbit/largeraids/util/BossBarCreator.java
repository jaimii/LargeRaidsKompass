package com.solarrabbit.largeraids.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.solarrabbit.largeraids.nms.AbstractCraftRaidWrapper;
import com.solarrabbit.largeraids.nms.AbstractRaidWrapper;
import com.solarrabbit.largeraids.raid.LargeRaid;
import com.solarrabbit.largeraids.raid.RaidManager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Raider;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class BossBarCreator implements Listener {
    private static final Map<Raider, BossBar> RAID_BOSSES = new HashMap<>();
    private static final int TICK_PERIOD = 10;
    private static final double FALLBACK_RADIUS_SQ = 144.0 * 144.0;

    private final RaidManager raidManager;

    public BossBarCreator(RaidManager manager) {
        raidManager = manager;
    }

    public static BossBar createRaidBossBar(Raider boss) {
        return createRaidBossBar(boss, BarColor.RED);
    }

    public static BossBar createRaidBossBar(Raider boss, BarColor color) {
        BossBar existing = RAID_BOSSES.get(boss);
        if (existing != null) {
            existing.setColor(color);
            return existing;
        }
        BossBar bar = Bukkit.createBossBar(boss.getName(), color, BarStyle.SEGMENTED_10, BarFlag.DARKEN_SKY);
        registerEntity(boss, bar);
        return bar;
    }

    public static void ensureRaidBossBar(Raider boss) {
        ensureRaidBossBar(boss, BarColor.RED);
    }

    public static void ensureRaidBossBar(Raider boss, BarColor color) {
        if (!RAID_BOSSES.containsKey(boss)) {
            createRaidBossBar(boss, color);
        }
    }

    public void clearAllBars() {
        for (BossBar bar : RAID_BOSSES.values()) {
            if (bar != null) {
                bar.removeAll();
            }
        }
        RAID_BOSSES.clear();
    }

    @EventHandler
    private void onBossDeath(EntityDeathEvent evt) {
        Entity entity = evt.getEntity();
        if (!(entity instanceof Raider raider))
            return;
        unregisterEntity(raider);
    }

    public void init(JavaPlugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0, TICK_PERIOD);
    }

    private void tick() {
        if (RAID_BOSSES.isEmpty())
            return;

        Map<LargeRaid, Set<Player>> raidPlayersCache = new HashMap<>();
        Raider[] bosses = RAID_BOSSES.keySet().toArray(Raider[]::new);

        for (Raider boss : bosses) {
            if (boss == null || boss.isDead() || !boss.isValid()) {
                unregisterEntity(boss);
                continue;
            }
            updateBossBarProgress(boss);
            updateBossBarVisibility(boss, raidPlayersCache);
        }
    }

    private void updateBossBarProgress(Raider boss) {
        BossBar bar = RAID_BOSSES.get(boss);
        if (bar == null) return;

        AttributeInstance maxHealthAttr = boss.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = maxHealthAttr != null ? maxHealthAttr.getValue() : 20.0;
        double progress = boss.getHealth() / maxHealth;
        progress = Math.min(1.0, Math.max(0.0, progress));

        if (Math.abs(bar.getProgress() - progress) > 1e-4) {
            bar.setProgress(progress);
        }
    }

    private void updateBossBarVisibility(Raider boss, Map<LargeRaid, Set<Player>> raidPlayersCache) {
        BossBar bar = RAID_BOSSES.get(boss);
        if (bar == null) return;

        Set<Player> targetPlayers;
        AbstractRaidWrapper nmsRaid = VersionUtil.getCraftRaiderWrapper(boss).getHandle().getCurrentRaid();

        if (nmsRaid.isEmpty()) {
            Location bossLoc = boss.getLocation();
            List<Player> worldPlayers = boss.getWorld().getPlayers();
            targetPlayers = new HashSet<>(worldPlayers.size());
            for (Player player : worldPlayers) {
                if (player.getLocation().distanceSquared(bossLoc) <= FALLBACK_RADIUS_SQ) {
                    targetPlayers.add(player);
                }
            }
        } else {
            AbstractCraftRaidWrapper craftRaid = VersionUtil.getCraftRaidWrapper(nmsRaid);
            Optional<LargeRaid> lr = raidManager.getLargeRaid(craftRaid.getRaid());
            targetPlayers = lr.map(largeRaid -> raidPlayersCache.computeIfAbsent(largeRaid, LargeRaid::getPlayersInRadius))
                    .orElse(Collections.emptySet());
        }

        syncBarPlayers(bar, targetPlayers);
    }

    private void syncBarPlayers(BossBar bar, Set<Player> targetPlayers) {
        List<Player> currentPlayers = bar.getPlayers();
        if (!currentPlayers.isEmpty()) {
            for (Player current : new ArrayList<>(currentPlayers)) {
                if (!targetPlayers.contains(current)) {
                    bar.removePlayer(current);
                }
            }
        }
        for (Player target : targetPlayers) {
            bar.addPlayer(target);
        }
    }

    private static void registerEntity(Raider boss, BossBar bar) {
        RAID_BOSSES.put(boss, bar);
    }

    private void unregisterEntity(Raider boss) {
        BossBar bar = RAID_BOSSES.remove(boss);
        if (bar != null)
            bar.removeAll();
    }
}