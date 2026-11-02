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

/**
 * Configures some settings of the current large raid.
 */
public class RaidConfigCommand implements CommandExecutor {
    private final LargeRaids plugin;
    private final RaidManager manager;

    public RaidConfigCommand(LargeRaids plugin) {
        this.plugin = plugin;
        manager = plugin.getRaidManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatColorUtil.send(sender, NamedTextColor.RED, this.plugin.getMessage("sender-player"));
            return false;
        }

        if (args.length < 1)
            return false;

        Location location = player.getLocation();
        Optional<LargeRaid> largeRaid = manager.getLargeRaid(location);
        if (largeRaid.isEmpty()) {
            ChatColorUtil.send(sender, NamedTextColor.RED, this.plugin.getMessage("raid-config.no-large-raid"));
            return true;
        }

        switch (args[0]) {
            case "spawn":
                if (args.length == 1) {
                    Location spawn = largeRaid.get().getSpawnLocation();
                    if (spawn == null) {
                        ChatColorUtil.send(sender, NamedTextColor.YELLOW, this.plugin.getMessage("raid-config.no-custom-spawn"));
                    } else {
                        ChatColorUtil.send(sender, NamedTextColor.GREEN, String.format(this.plugin.getMessage("raid-config.current-custom-spawn"),
                                (int) Math.floor(spawn.getX()), (int) Math.floor(spawn.getY()), (int) Math.floor(spawn.getZ())));
                    }
                    return true;
                }

                if (args.length == 2 && args[1].equals("clear")) {
                    largeRaid.get().setSpawnLocation(null);
                    ChatColorUtil.send(sender, NamedTextColor.GREEN, this.plugin.getMessage("raid-config.custom-spawn-cleared"));
                    return true;
                }
                if (args.length != 4)
                    return false;

                try {
                    double x = parseDoubleOrRelative(args[1], location, 0);
                    double y = parseDoubleOrRelative(args[2], location, 1);
                    double z = parseDoubleOrRelative(args[3], location, 2);
                    Location spawn = new Location(location.getWorld(), x, y, z);
                    largeRaid.get().setSpawnLocation(spawn);
                    ChatColorUtil.send(sender, NamedTextColor.GREEN, String.format(this.plugin.getMessage("raid-config.custom-spawn-set"),
                            (int) Math.floor(spawn.getX()), (int) Math.floor(spawn.getY()), (int) Math.floor(spawn.getZ())));
                    return true;
                } catch (NumberFormatException e) {
                    ChatColorUtil.send(sender, NamedTextColor.RED, this.plugin.getMessage("raid-config.invalid-coordinates"));
                    return false;
                }
            case "target":
                if (args.length == 1) {
                    Location target = largeRaid.get().getRaidTarget();
                    if (target == null) {
                        ChatColorUtil.send(sender, NamedTextColor.YELLOW, this.plugin.getMessage("raid-config.no-target"));
                    } else {
                        ChatColorUtil.send(sender, NamedTextColor.GREEN, String.format(this.plugin.getMessage("raid-config.current-target"),
                                (int) Math.floor(target.getX()), (int) Math.floor(target.getY()), (int) Math.floor(target.getZ()),
                                largeRaid.get().getRaidTargetRadius(), largeRaid.get().getRaidTargetNavSpeed()));
                    }
                    return true;
                }

                if (args.length == 2 && args[1].equals("clear")) {
                    largeRaid.get().setRaidTarget(null, 0, 0);
                    ChatColorUtil.send(sender, NamedTextColor.GREEN, this.plugin.getMessage("raid-config.target-cleared"));
                    return true;
                }
                if (args.length != 6)
                    return false;

                double x;
                double y;
                double z;
                try {
                    x = parseDoubleOrRelative(args[1], location, 0);
                    y = parseDoubleOrRelative(args[2], location, 1);
                    z = parseDoubleOrRelative(args[3], location, 2);
                } catch (NumberFormatException e) {
                    ChatColorUtil.send(sender, NamedTextColor.RED, this.plugin.getMessage("raid-config.invalid-coordinates"));
                    return false;
                }

                double radius;
                try {
                    radius = Double.parseDouble(args[4]);
                    if (radius < 0) {
                        ChatColorUtil.send(sender, NamedTextColor.RED, this.plugin.getMessage("raid-config.invalid-radius"));
                        return false;
                    }
                } catch (NumberFormatException e) {
                    ChatColorUtil.send(sender, NamedTextColor.RED, this.plugin.getMessage("raid-config.invalid-radius"));
                    return false;
                }

                double navSpeed;
                try {
                    navSpeed = Double.parseDouble(args[5]);
                    if (navSpeed <= 0) {
                        ChatColorUtil.send(sender, NamedTextColor.RED, this.plugin.getMessage("raid-config.invalid-navspeed"));
                        return false;
                    }
                } catch (NumberFormatException e) {
                    ChatColorUtil.send(sender, NamedTextColor.RED, this.plugin.getMessage("raid-config.invalid-navspeed"));
                    return false;
                }

                Location target = new Location(location.getWorld(), x, y, z);
                largeRaid.get().setRaidTarget(target, radius, navSpeed);
                ChatColorUtil.send(sender, NamedTextColor.GREEN, String.format(this.plugin.getMessage("raid-config.target-set"),
                        (int) Math.floor(target.getX()), (int) Math.floor(target.getY()), (int) Math.floor(target.getZ()), radius, navSpeed));
                return true;
            default:
                return false;
        }
    }

    private double parseDoubleOrRelative(String pos, Location loc, int type) {
        if (loc == null || pos.isEmpty() || pos.charAt(0) != '~')
            return Double.parseDouble(pos);
        double relative = pos.length() == 1 ? 0 : Double.parseDouble(pos.substring(1));
        return switch (type) {
            case 0 -> relative + loc.getX();
            case 1 -> relative + loc.getY();
            case 2 -> relative + loc.getZ();
            default -> 0;
        };
    }
}