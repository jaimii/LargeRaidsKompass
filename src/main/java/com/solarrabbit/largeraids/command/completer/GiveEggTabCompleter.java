package com.solarrabbit.largeraids.command.completer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GiveEggTabCompleter implements TabCompleter {
    private static final List<String> VARIANTS = List.of(
            "necromancer",
            "bomber",
            "firework",
            "king",
            "adjudicator",
            "archillager",
            "mythic:"
    );

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("largeraids.admin.giveegg")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> players = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    players.add(player.getName());
                }
            }
            return players;
        }

        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> matches = new ArrayList<>();
            for (String variant : VARIANTS) {
                if (variant.startsWith(prefix)) {
                    matches.add(variant);
                }
            }
            return matches;
        }

        if (args.length == 3) {
            return List.of("1", "5", "16", "64");
        }

        return Collections.emptyList();
    }
}