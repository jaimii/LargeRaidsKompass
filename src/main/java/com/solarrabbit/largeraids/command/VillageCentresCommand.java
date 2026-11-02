package com.solarrabbit.largeraids.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.solarrabbit.largeraids.LargeRaids;
import com.solarrabbit.largeraids.util.ChatColorUtil;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class VillageCentresCommand implements CommandExecutor {
    private static final double SPHERE_RADIUS = 96.0;
    private static final double VIEW_DISTANCE_SQ = 256.0 * 256.0;
    private static final DustOptions DUST_OPTIONS = new DustOptions(Color.ORANGE, 200.0F);
    private static final double[][] SPHERE_OFFSETS;

    static {
        final int freq = 25;
        List<double[]> points = new ArrayList<>(freq * freq);
        for (int i = 0; i <= freq; i++) {
            double y = (double) (2 * i - freq) / freq;
            double r = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            if (r < 1e-6) {
                points.add(new double[] { 0.0, y * SPHERE_RADIUS, 0.0 });
                continue;
            }
            for (int j = 0; j < freq; j++) {
                double alpha = 2 * Math.PI * j / freq;
                double dx = Math.sin(alpha) * SPHERE_RADIUS * r;
                double dy = y * SPHERE_RADIUS;
                double dz = Math.cos(alpha) * SPHERE_RADIUS * r;
                points.add(new double[] { dx, dy, dz });
            }
        }
        SPHERE_OFFSETS = points.toArray(new double[0][]);
    }

    private final LargeRaids plugin;
    private boolean isShowing;
    private int taskId;

    public VillageCentresCommand(LargeRaids plugin) {
        this.plugin = plugin;
        this.isShowing = false;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            this.list(sender);
            return true;
        }

        if (!(sender instanceof Player player))
            return true;

        switch (args[0]) {
            case "add":
                if (args.length < 2)
                    return false;
                this.add(player, args[1]);
                return true;
            case "remove":
                if (args.length < 2)
                    return false;
                this.remove(player, args[1]);
                return true;
            case "show":
                show();
                ChatColorUtil.send(sender, NamedTextColor.GREEN, this.plugin.getMessage("village-centers.show.success"));
                return true;
            case "hide":
                hide();
                ChatColorUtil.send(sender, NamedTextColor.GREEN, this.plugin.getMessage("village-centers.hide.success"));
                return true;
            default:
                return false;
        }
    }

    private void show() {
        if (isShowing)
            return;
        taskId = Bukkit.getScheduler()
                .runTaskTimer(plugin, () -> {
                    Map<String, Location> centers = plugin.getDatabaseAdapter().getCentres();
                    if (centers == null || centers.isEmpty())
                        return;
                    for (Location loc : centers.values()) {
                        if (isLoadedAndViewed(loc)) {
                            highlightLocation(loc);
                            plotSphere(loc);
                        }
                    }
                }, 0L, 20L)
                .getTaskId();
        isShowing = true;
    }

    private void hide() {
        if (isShowing) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        isShowing = false;
    }

    private void add(Player player, String name) {
        Location center = this.plugin.getDatabaseAdapter().getCentre(name);

        if (center != null) {
            ChatColorUtil.send(player, NamedTextColor.RED, this.plugin.getMessage("village-centers.add.already-exist"));
            return;
        }

        Location newCenter = player.getLocation();
        if (plugin.getVillageManager().addVillage(newCenter)) {
            plugin.getDatabaseAdapter().addCentre(newCenter, name);
            ChatColorUtil.send(player, NamedTextColor.GREEN, this.plugin.getMessage("village-centers.add.add-success"));
        } else {
            ChatColorUtil.send(player, NamedTextColor.RED, this.plugin.getMessage("village-centers.add.add-fail"));
        }
    }

    private void remove(Player player, String name) {
        Location center = plugin.getDatabaseAdapter().getCentre(name);

        if (center == null) {
            ChatColorUtil.send(player, NamedTextColor.RED, this.plugin.getMessage("village-centers.remove.no-exist"));
            return;
        }

        if (center.getWorld() != null) {
            plugin.getVillageManager().removeVillage(center);
        }
        plugin.getDatabaseAdapter().removeCentre(name);
        ChatColorUtil.send(player, NamedTextColor.GREEN, this.plugin.getMessage("village-centers.remove.remove-success"));
    }

    private void list(CommandSender sender) {
        Map<String, Location> map = this.plugin.getDatabaseAdapter().getCentres();
        if (map == null || map.isEmpty()) {
            ChatColorUtil.send(sender, NamedTextColor.YELLOW, this.plugin.getMessage("village-centers.list.no-exist"));
            return;
        }
        ChatColorUtil.send(sender, NamedTextColor.GREEN, this.plugin.getMessage("village-centers.list.list-all"));
        map.forEach((str, loc) -> sender.sendMessage(getClickableComponent(str, loc)));
    }

    private Component getClickableComponent(String name, Location loc) {
        return Component.text(name + " " + getLocString(loc), NamedTextColor.GREEN)
                .hoverEvent(HoverEvent.showText(Component.text("Click to teleport")))
                .clickEvent(ClickEvent.suggestCommand(getTeleportCommand(loc)));
    }

    private String getTeleportCommand(Location loc) {
        return String.join(" ", "/tp", "@s", String.valueOf(loc.getX()), String.valueOf(loc.getY()),
                String.valueOf(loc.getZ()));
    }

    private String getLocString(Location loc) {
        return "[" + (loc.getWorld() == null ? "null" : loc.getWorld().getName()) + ", " + getRoundedDouble(loc.getX())
                + ", " + getRoundedDouble(loc.getY()) + ", " + getRoundedDouble(loc.getZ()) + "]";
    }

    private String getRoundedDouble(double d) {
        return String.format("%.3f", d);
    }

    private boolean isLoadedAndViewed(Location center) {
        if (center == null) return false;
        World world = center.getWorld();
        if (world == null || !world.isChunkLoaded(center.getBlockX() >> 4, center.getBlockZ() >> 4))
            return false;

        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(center) <= VIEW_DISTANCE_SQ) {
                return true;
            }
        }
        return false;
    }

    private void highlightLocation(Location location) {
        location.getWorld().spawnParticle(Particle.CRIT, location, 50, 0.5, 0.5, 0.5, 0);
    }

    private void plotSphere(Location center) {
        World world = center.getWorld();
        double baseX = center.getBlockX();
        double baseY = center.getBlockY();
        double baseZ = center.getBlockZ();

        for (double[] offset : SPHERE_OFFSETS) {
            world.spawnParticle(Particle.DUST,
                    baseX + offset[0],
                    baseY + offset[1],
                    baseZ + offset[2],
                    1, 1, 1, 1, 0, DUST_OPTIONS, true);
        }
    }
}