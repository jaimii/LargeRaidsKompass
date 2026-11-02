package com.solarrabbit.largeraids.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public class CustomEggUtil {

    private final Plugin plugin;
    public static final String PDC_KEY = "custom_mob_type";

    public CustomEggUtil(Plugin plugin) {
        this.plugin = plugin;
    }

    public ItemStack createCustomSpawnEgg(String variant, int amount) {
        Material material;
        String displayName;
        NamedTextColor color;

        String lowerVariant = variant.toLowerCase(java.util.Locale.ROOT);

        if (lowerVariant.startsWith("mythic:")) {
            String mobName = variant.substring(7);
            material = Material.WITCH_SPAWN_EGG;
            displayName = mobName + " Spawn Egg";
            color = NamedTextColor.AQUA;
        } else {
            switch (lowerVariant) {
                case "necromancer":
                    material = Material.EVOKER_SPAWN_EGG;
                    displayName = "Necromancer Spawn Egg";
                    color = NamedTextColor.DARK_PURPLE;
                    break;
                case "bomber":
                    material = Material.VEX_SPAWN_EGG;
                    displayName = "Bomber Spawn Egg";
                    color = NamedTextColor.RED;
                    break;
                case "firework":
                    material = Material.PILLAGER_SPAWN_EGG;
                    displayName = "Firework Pillager Spawn Egg";
                    color = NamedTextColor.GOLD;
                    break;
                case "king":
                    material = Material.EVOKER_SPAWN_EGG;
                    displayName = "King Raider Spawn Egg";
                    color = NamedTextColor.YELLOW;
                    break;
                case "adjudicator":
                    material = Material.VINDICATOR_SPAWN_EGG;
                    displayName = "Adjudicator Spawn Egg";
                    color = NamedTextColor.GOLD;
                    break;
                case "archillager":
                case "arch-illager":
                case "arch":
                    material = Material.RAVAGER_SPAWN_EGG;
                    displayName = "Arch-Illager Spawn Egg";
                    color = NamedTextColor.BLUE;
                    variant = "archillager";
                    break;
                default:
                    return null;
            }
        }

        ItemStack egg = new ItemStack(material, amount);
        ItemMeta meta = egg.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(displayName, color).decoration(TextDecoration.ITALIC, false));
            NamespacedKey key = new NamespacedKey(plugin, PDC_KEY);
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, variant);
            meta.setEnchantmentGlintOverride(true);
            egg.setItemMeta(meta);
        }
        return egg;
    }
}