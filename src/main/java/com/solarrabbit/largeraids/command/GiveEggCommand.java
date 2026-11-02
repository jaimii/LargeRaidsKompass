package com.solarrabbit.largeraids.command;

import com.solarrabbit.largeraids.util.ChatColorUtil;
import com.solarrabbit.largeraids.util.CustomEggUtil;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class GiveEggCommand implements CommandExecutor {

    private final CustomEggUtil eggUtil;

    public GiveEggCommand(CustomEggUtil eggUtil) {
        this.eggUtil = eggUtil;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("largeraids.admin.giveegg")) {
            ChatColorUtil.send(sender, "<red>You do not have permission to execute this command.");
            return true;
        }

        if (args.length < 2) {
            ChatColorUtil.send(sender, "<red>Usage: /" + label + " <player> <variant> [amount]");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            ChatColorUtil.send(sender, "<red>Player not found: " + args[0]);
            return true;
        }

        String variant = args[1];
        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
                if (amount <= 0) {
                    ChatColorUtil.send(sender, "<red>Amount must be a positive number.");
                    return true;
                }
            } catch (NumberFormatException e) {
                ChatColorUtil.send(sender, "<red>Invalid amount: " + args[2]);
                return true;
            }
        }

        ItemStack egg = eggUtil.createCustomSpawnEgg(variant, amount);
        if (egg == null) {
            ChatColorUtil.send(sender, "<red>Unknown variant: " + variant);
            return true;
        }

        target.getInventory().addItem(egg)
                .forEach((index, leftover) -> target.getWorld().dropItem(target.getLocation(), leftover));
        ChatColorUtil.send(sender, "<green>Gave " + amount + "x " + variant + " spawn egg to " + target.getName() + ".");
        return true;
    }
}