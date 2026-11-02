package com.solarrabbit.largeraids.command;

import com.solarrabbit.largeraids.LargeRaids;
import com.solarrabbit.largeraids.util.ChatColorUtil;

import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class GiveSummonItemCommand implements CommandExecutor {
    private final LargeRaids plugin;

    public GiveSummonItemCommand(LargeRaids plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1)
            return false;
        Player targetPlayer = Bukkit.getPlayer(args[0]);
        if (targetPlayer == null) {
            ChatColorUtil.send(sender, NamedTextColor.RED, this.plugin.getMessage("give-items.cannot-find"));
            return false;
        }

        if (args.length < 2) {
            giveItems(sender, targetPlayer, 1);
            return true;
        } else {
            try {
                int amount = Integer.parseInt(args[1]);
                if (amount <= 0)
                    ChatColorUtil.send(sender, NamedTextColor.RED, this.plugin.getMessage("give-items.need-positive"));
                else
                    giveItems(sender, targetPlayer, amount);
                return true;
            } catch (NumberFormatException e) {
                ChatColorUtil.send(sender, NamedTextColor.RED, this.plugin.getMessage("give-items.invalid-number"));
                return false;
            }
        }
    }

    private void giveItems(CommandSender requester, Player receiver, int requestAmount) {
        Inventory inventory = receiver.getInventory();
        for (int i = 0; i < requestAmount; i++) {
            inventory.addItem(plugin.getTriggerConfig().getDropInLavaConfig().getItem())
                    .forEach((index, item) -> receiver.getWorld().dropItem(receiver.getLocation(), item));
        }
        ChatColorUtil.send(requester, NamedTextColor.GREEN, String.format(this.plugin.getMessage("give-items.success"),
                receiver.getName(), requestAmount, requestAmount == 1 ? "" : "s"));
    }
}