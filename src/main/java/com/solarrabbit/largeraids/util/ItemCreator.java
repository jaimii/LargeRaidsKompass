package com.solarrabbit.largeraids.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.kyori.adventure.text.Component;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

public class ItemCreator {

    public static ItemStack getItemFromConfig(ConfigurationSection config) {
        if (config == null) {
            return new ItemStack(Material.STICK);
        }
        ItemStack item = Optional.ofNullable(config.getString("material", null))
                .map(Material::matchMaterial)
                .map(ItemStack::new)
                .orElse(new ItemStack(Material.STICK));

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            Optional.ofNullable(config.getString("display-name", null))
                    .ifPresent(name -> meta.displayName(ChatColorUtil.parseItem("<white>" + name)));

            List<String> rawLore = config.getStringList("lore");
            if (!rawLore.isEmpty()) {
                List<Component> loreComponents = new ArrayList<>(rawLore.size());
                for (String line : rawLore) {
                    loreComponents.add(ChatColorUtil.parseItem("<white>" + line));
                }
                meta.lore(loreComponents);
            }

            int cmd = config.getInt("custom-model-data", 0);
            if (cmd != 0 && meta != null) {
                CustomModelDataComponent component = meta.getCustomModelDataComponent();
                component.setFloats(List.of((float) cmd));
                meta.setCustomModelDataComponent(component);
            }
            item.setItemMeta(meta);
        }

        int amt = config.getInt("amount", 0);
        if (amt > 0) {
            item.setAmount(amt);
        }
        item.addUnsafeEnchantments(getEnchantments(config.getConfigurationSection("enchantments")));

        return item;
    }

    private static Map<Enchantment, Integer> getEnchantments(ConfigurationSection config) {
        Map<Enchantment, Integer> map = new HashMap<>();
        if (config == null) {
            return map;
        }

        Set<String> keys = config.getKeys(false);
        for (String section : keys) {
            int level = config.getInt(section + ".level");
            String rawType = config.getString(section + ".type", null);
            if (rawType == null) continue;

            Enchantment enchant = resolveEnchantment(rawType);
            if (enchant != null) {
                map.put(enchant, level);
            }
        }
        return map;
    }

    @SuppressWarnings("deprecation")
    private static Enchantment resolveEnchantment(String rawName) {
        String normalized = rawName.trim().toLowerCase(Locale.ROOT);
        NamespacedKey key = NamespacedKey.fromString(normalized);
        if (key != null) {
            Enchantment byKey = Registry.ENCHANTMENT.get(key);
            if (byKey != null) {
                return byKey;
            }
        }
        return Enchantment.getByName(rawName.trim().toUpperCase(Locale.ROOT));
    }
}