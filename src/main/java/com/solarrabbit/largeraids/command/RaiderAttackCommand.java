package com.solarrabbit.largeraids.command;

import com.solarrabbit.largeraids.LargeRaids;
import com.solarrabbit.largeraids.raid.LargeRaid;
import com.solarrabbit.largeraids.raid.RaidManager;
import com.solarrabbit.largeraids.util.ChatColorUtil;

import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Locale;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Animals;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Raider;

/**
 * Adds a certain entity type to all current raiders' attack list for this wave only.
 */
public class RaiderAttackCommand implements CommandExecutor {
    private final LargeRaids plugin;
    private final RaidManager manager;

    public RaiderAttackCommand(LargeRaids plugin) {
        this.plugin = plugin;
        manager = plugin.getRaidManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 3)
            return false;

        if (!(sender instanceof Player player)) {
            ChatColorUtil.send(sender, NamedTextColor.RED, this.plugin.getMessage("sender-player"));
            return false;
        }

        Location location = player.getLocation();
        Optional<LargeRaid> largeRaid = manager.getLargeRaid(location);
        if (largeRaid.isEmpty()) {
            ChatColorUtil.send(sender, NamedTextColor.RED, this.plugin.getMessage("raider-attack.no-large-raid"));
            return true;
        }

        if (largeRaid.get().isLoading()) {
            ChatColorUtil.send(sender, NamedTextColor.RED, this.plugin.getMessage("raider-attack.no-raiders"));
            return true;
        }

        int prio;
        try {
            prio = Integer.parseInt(args[0]);
            if (prio < 0) {
                ChatColorUtil.send(sender, NamedTextColor.RED, this.plugin.getMessage("raider-attack.prio-too-low"));
                return false;
            }
        } catch (NumberFormatException e) {
            ChatColorUtil.send(sender, NamedTextColor.RED, this.plugin.getMessage("raider-attack.invalid-prio"));
            return false;
        }

        if (!args[1].equalsIgnoreCase("true") && !args[1].equalsIgnoreCase("false")) {
            ChatColorUtil.send(sender, NamedTextColor.RED, this.plugin.getMessage("raider-attack.thru-walls-boolean"));
            return false;
        }
        boolean thruWalls = Boolean.parseBoolean(args[1]);

        Class<?> entityClass = resolveEntityClass(args[2]);
        if (entityClass == null) {
            ChatColorUtil.send(sender, NamedTextColor.RED, this.plugin.getMessage("raider-attack.class-not-found"));
            return false;
        }

        if (!largeRaid.get().addAttackGoal(prio, !thruWalls, entityClass))
            ChatColorUtil.send(sender, NamedTextColor.RED, this.plugin.getMessage("raider-attack.invalid-entity-class"));
        else
            ChatColorUtil.send(sender, NamedTextColor.GREEN, String.format(this.plugin.getMessage("raider-attack.attack-goal-set"),
                    entityClass.getSimpleName()));

        return true;
    }

    static Class<?> resolveEntityClass(String input) {
        if (input == null || input.isBlank()) return null;
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        int lastDot = normalized.lastIndexOf('.');
        if (lastDot >= 0 && lastDot + 1 < normalized.length()) {
            normalized = normalized.substring(lastDot + 1);
        }

        switch (normalized) {
            case "livingentity":
            case "living_entity":
                return LivingEntity.class;
            case "mob":
                return Mob.class;
            case "monster":
                return Monster.class;
            case "animal":
            case "animals":
                return Animals.class;
            case "raider":
                return Raider.class;
            case "player":
            case "serverplayer":
                return Player.class;
            default:
                break;
        }

        NamespacedKey key = NamespacedKey.fromString(normalized);
        if (key != null) {
            EntityType type = Registry.ENTITY_TYPE.get(key);
            if (type != null && type.getEntityClass() != null) {
                return type.getEntityClass();
            }
        }

        for (EntityType type : Registry.ENTITY_TYPE) {
            if (type.getKey().getKey().replace("_", "").equals(normalized) && type.getEntityClass() != null) {
                return type.getEntityClass();
            }
        }
        return null;
    }
}