package com.solarrabbit.largeraids.raid.mob.manager;

import com.solarrabbit.largeraids.LargeRaids;
import com.solarrabbit.largeraids.config.custommobs.CustomMobsConfig;
import com.solarrabbit.largeraids.raid.mob.FireworkPillager;
import com.solarrabbit.largeraids.util.ChatColorUtil;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;

import net.kyori.adventure.text.Component;

import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.FireworkEffect;
import org.bukkit.FireworkEffect.Type;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Pillager;
import org.bukkit.entity.Raider;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class FireworkPillagerManager implements CustomRaiderManager, Listener {
    private static final int DEATH_XP_POINTS = getXpForLevel(5);
    private static final Component PILLAGER_NAME = ChatColorUtil.parse("<yellow>Enforcer");

    private final LargeRaids plugin;
    private final NamespacedKey pillagerKey;
    private final NamespacedKey fireworkKey;
    private final ItemStack bannerTemplate;
    private final ItemStack fireworkTemplate;

    private double health;

    public FireworkPillagerManager() {
        this.plugin = JavaPlugin.getPlugin(LargeRaids.class);
        this.pillagerKey = new NamespacedKey(plugin, "firework_pillager");
        this.fireworkKey = new NamespacedKey(plugin, "pillager_firework");
        this.bannerTemplate = createDefaultBanner();
        this.fireworkTemplate = createDefaultFirework();
    }

    @Override
    public void loadSettings(CustomMobsConfig config) {
        health = config.getFireworkPillagerConfig().getHealth();
    }

    @Override
    public FireworkPillager spawn(Location location) {
        Pillager entity = (Pillager) location.getWorld().spawnEntity(location, EntityType.PILLAGER);
        entity.getAttribute(Attribute.MAX_HEALTH).setBaseValue(health);
        entity.setHealth(health);

        EntityEquipment equipment = entity.getEquipment();
        if (equipment != null) {
            equipment.setItemInOffHand(fireworkTemplate.clone());
            equipment.setHelmet(bannerTemplate.clone());
            equipment.setHelmetDropChance(1.0f);
        }

        entity.setPatrolLeader(true);
        entity.getPersistentDataContainer().set(pillagerKey, PersistentDataType.BYTE, (byte) 0);
        entity.customName(PILLAGER_NAME);
        return new FireworkPillager(entity);
    }

    @EventHandler
    private void onBowShoot(EntityShootBowEvent evt) {
        if (evt.getEntityType() != EntityType.PILLAGER)
            return;
        Pillager pillager = (Pillager) evt.getEntity();
        if (isFireworkPillager(pillager)) {
            if (pillager.getEquipment() != null) {
                pillager.getEquipment().setItemInOffHand(fireworkTemplate.clone());
            }
            evt.getProjectile().getPersistentDataContainer().set(fireworkKey, PersistentDataType.BYTE, (byte) 0);
        }
    }

    @EventHandler
    private void onDamageRaider(EntityDamageByEntityEvent evt) {
        if (evt.getCause() != DamageCause.ENTITY_EXPLOSION)
            return;
        if (evt.getDamager().getType() != EntityType.FIREWORK_ROCKET)
            return;
        if (!(evt.getEntity() instanceof Raider))
            return;
        Firework firework = (Firework) evt.getDamager();
        if (isPillagerFirework(firework))
            evt.setCancelled(true);
    }

    @EventHandler
    private void onDeath(EntityDeathEvent evt) {
        if (evt.getEntityType() != EntityType.PILLAGER)
            return;
        Pillager pillager = (Pillager) evt.getEntity();
        if (isFireworkPillager(pillager)) {
            evt.setDroppedExp(DEATH_XP_POINTS);
        }
    }

    private static int getXpForLevel(int level) {
        if (level <= 16) {
            return level * level + 6 * level;
        } else if (level <= 31) {
            return (int) (2.5 * level * level - 40.5 * level + 360);
        } else {
            return (int) (4.5 * level * level - 162.5 * level + 2220);
        }
    }

    private static ItemStack createDefaultFirework() {
        ItemStack firework = new ItemStack(Material.FIREWORK_ROCKET);
        FireworkMeta meta = (FireworkMeta) firework.getItemMeta();
        if (meta != null) {
            FireworkEffect effect = FireworkEffect.builder().with(Type.BALL_LARGE)
                    .withColor(Color.AQUA, Color.ORANGE, Color.FUCHSIA).flicker(true).build();
            meta.addEffects(effect, effect, effect, effect, effect);
            firework.setItemMeta(meta);
        }
        return firework;
    }

    private static ItemStack createDefaultBanner() {
        ItemStack banner = new ItemStack(Material.YELLOW_BANNER);
        BannerMeta meta = (BannerMeta) banner.getItemMeta();
        if (meta != null) {
            meta.addPattern(new Pattern(DyeColor.MAGENTA, PatternType.STRIPE_CENTER));
            meta.addPattern(new Pattern(DyeColor.ORANGE, PatternType.CURLY_BORDER));
            meta.addPattern(new Pattern(DyeColor.RED, PatternType.SMALL_STRIPES));
            meta.addPattern(new Pattern(DyeColor.RED, PatternType.RHOMBUS));
            meta.addPattern(new Pattern(DyeColor.YELLOW, PatternType.FLOWER));
            meta.addPattern(new Pattern(DyeColor.BLACK, PatternType.BORDER));
            meta.displayName(ChatColorUtil.parseItem("<gold><italic>Enforcer Banner"));
            banner.setItemMeta(meta);
        }
        banner.setData(DataComponentTypes.TOOLTIP_DISPLAY,
                TooltipDisplay.tooltipDisplay().addHiddenComponents(DataComponentTypes.BANNER_PATTERNS).build());
        return banner;
    }

    private boolean isFireworkPillager(Pillager pillager) {
        return pillager.getPersistentDataContainer().has(pillagerKey, PersistentDataType.BYTE);
    }

    private boolean isPillagerFirework(Firework firework) {
        return firework.getPersistentDataContainer().has(fireworkKey, PersistentDataType.BYTE);
    }
}