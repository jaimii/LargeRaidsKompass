package com.solarrabbit.largeraids.command;

import com.solarrabbit.largeraids.LargeRaids;
import com.solarrabbit.largeraids.nms.AbstractBlockPositionWrapper;
import com.solarrabbit.largeraids.nms.AbstractWorldServerWrapper;
import com.solarrabbit.largeraids.util.ChatColorUtil;
import com.solarrabbit.largeraids.util.VersionUtil;

import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Makes the entity type in range of the player path-find to a given location.
 */
public class MobControlCommand implements CommandExecutor {
    private final LargeRaids plugin;

    public MobControlCommand(LargeRaids plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            ChatColorUtil.send(sender, NamedTextColor.RED, this.plugin.getMessage("sender-player"));
            return false;
        }

        if (args.length < 3)
            return false;

        Class<?> entityClass;
        try {
            entityClass = Class.forName(args[0]);
        } catch (ClassNotFoundException e) {
            ChatColorUtil.send(sender, NamedTextColor.RED, this.plugin.getMessage("mob-control.class-not-found"));
            return false;
        }

        Location search = player.getLocation();
        double range;
        try {
            range = Double.parseDouble(args[1]);
            if (range <= 0) {
                ChatColorUtil.send(sender, NamedTextColor.RED, this.plugin.getMessage("mob-control.invalid-range"));
                return false;
            }
        } catch (NumberFormatException e) {
            ChatColorUtil.send(sender, NamedTextColor.RED, this.plugin.getMessage("mob-control.invalid-range"));
            return false;
        }

        if (args.length == 3 && args[2].equalsIgnoreCase("clear")) {
            AbstractWorldServerWrapper world = VersionUtil.getCraftWorldWrapper(search.getWorld()).getHandle();
            AbstractBlockPositionWrapper searchWrapper = VersionUtil.getBlockPositionWrapper(search);
            int result = world.clearMobTargets(entityClass, searchWrapper, range);
            if (result == -1) {
                ChatColorUtil.send(sender, NamedTextColor.RED, this.plugin.getMessage("mob-control.invalid-entity-class"));
            } else if (result == 0) {
                ChatColorUtil.send(sender, NamedTextColor.YELLOW, String.format(this.plugin.getMessage("mob-control.no-mobs-cleared"),
                        entityClass.getSimpleName(), range, (int) Math.floor(search.getX()),
                        (int) Math.floor(search.getY()), (int) Math.floor(search.getZ())));
            } else {
                ChatColorUtil.send(sender, NamedTextColor.GREEN, String.format(this.plugin.getMessage("mob-control.mobs-cleared"), result,
                        entityClass.getSimpleName()));
            }
            return true;
        }

        if (args.length != 9)
            return false;

        Location target;
        try {
            double x = parseDoubleOrRelative(args[2], search, 0);
            double y = parseDoubleOrRelative(args[3], search, 1);
            double z = parseDoubleOrRelative(args[4], search, 2);
            target = new Location(search.getWorld(), x, y, z);
        } catch (NumberFormatException e) {
            ChatColorUtil.send(sender, NamedTextColor.RED, this.plugin.getMessage("mob-control.invalid-coordinates"));
            return false;
        }

        double radius;
        try {
            radius = Double.parseDouble(args[5]);
            if (radius < 0) {
                ChatColorUtil.send(sender, NamedTextColor.RED, this.plugin.getMessage("mob-control.invalid-radius"));
                return false;
            }
        } catch (NumberFormatException e) {
            ChatColorUtil.send(sender, NamedTextColor.RED, this.plugin.getMessage("mob-control.invalid-radius"));
            return false;
        }

        double navSpeed;
        try {
            navSpeed = Double.parseDouble(args[6]);
            if (navSpeed <= 0) {
                ChatColorUtil.send(sender, NamedTextColor.RED, this.plugin.getMessage("mob-control.invalid-navspeed"));
                return false;
            }
        } catch (NumberFormatException e) {
            ChatColorUtil.send(sender, NamedTextColor.RED, this.plugin.getMessage("mob-control.invalid-navspeed"));
            return false;
        }

        int prio;
        try {
            prio = Integer.parseInt(args[7]);
            if (prio < 0) {
                ChatColorUtil.send(sender, NamedTextColor.RED, this.plugin.getMessage("mob-control.invalid-prio"));
                return false;
            }
        } catch (NumberFormatException e) {
            ChatColorUtil.send(sender, NamedTextColor.RED, this.plugin.getMessage("mob-control.invalid-prio"));
            return false;
        }

        if (!args[8].equalsIgnoreCase("true") && !args[8].equalsIgnoreCase("false")) {
            ChatColorUtil.send(sender, NamedTextColor.RED, this.plugin.getMessage("mob-control.pathfind-once-boolean"));
            return false;
        }
        boolean pathfindOnce = Boolean.parseBoolean(args[8]);

        AbstractWorldServerWrapper world = VersionUtil.getCraftWorldWrapper(search.getWorld()).getHandle();
        AbstractBlockPositionWrapper searchWrapper = VersionUtil.getBlockPositionWrapper(search);
        AbstractBlockPositionWrapper targetWrapper = VersionUtil.getBlockPositionWrapper(target);
        int result = world.setMobTargets(entityClass, searchWrapper, range, targetWrapper, radius, navSpeed, prio, pathfindOnce);

        if (result == -1) {
            ChatColorUtil.send(sender, NamedTextColor.RED, this.plugin.getMessage("mob-control.invalid-entity-class"));
        } else if (result == 0) {
            ChatColorUtil.send(sender, NamedTextColor.YELLOW, String.format(this.plugin.getMessage("mob-control.no-mobs-found"),
                    entityClass.getSimpleName(), range, (int) Math.floor(search.getX()),
                    (int) Math.floor(search.getY()), (int) Math.floor(search.getZ())));
        } else {
            ChatColorUtil.send(sender, NamedTextColor.GREEN, String.format(this.plugin.getMessage("mob-control.mobs-set"), result,
                    entityClass.getSimpleName(), radius, (int) Math.floor(target.getX()),
                    (int) Math.floor(target.getY()), (int) Math.floor(target.getZ()), navSpeed));
        }

        return true;
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