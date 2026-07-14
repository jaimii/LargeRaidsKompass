package com.solarrabbit.largeraids.raid.mob.manager;

import com.solarrabbit.largeraids.LargeRaids;
import com.solarrabbit.largeraids.config.custommobs.CustomMobsConfig;
import com.solarrabbit.largeraids.raid.mob.Necromancer;
import com.solarrabbit.largeraids.util.VersionUtil;

import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Raider;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Spellcaster;
import org.bukkit.entity.Vex;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockIgniteEvent.IgniteCause;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class NecromancerManager implements CustomRaiderManager, Listener {

    @Override
    public void loadSettings(CustomMobsConfig config) {
    }

    @Override
    public Necromancer spawn(Location location) {
        Spellcaster evoker = (Spellcaster) location.getWorld().spawnEntity(location, EntityType.EVOKER);
        EntityEquipment equipment = evoker.getEquipment();
        equipment.setHelmet(getDefaultBanner());
        equipment.setHelmetDropChance(1.0f);

        evoker.setPatrolLeader(true);

        evoker.getPersistentDataContainer().set(getNecromancerNamespacedKey(), PersistentDataType.BYTE, (byte) 0);
        evoker.setCustomName("Necromancer");
        return new Necromancer(evoker);
    }

    @EventHandler
    private void onSummonVex(CreatureSpawnEvent evt) {
        if (evt.getEntityType() != EntityType.VEX)
            return;
        Vex vex = (Vex) evt.getEntity();
        LivingEntity owner = VersionUtil.getCraftVexWrapper(vex).getOwner();
        if (!(owner instanceof Spellcaster))
            return;
        Spellcaster evoker = (Spellcaster) owner;
        if (isNecromancer(evoker)) {
            evt.setCancelled(true);
            LivingEntity target = evoker.getTarget();
            if (target instanceof HumanEntity) {
                vex.getWorld().strikeLightningEffect(vex.getLocation());
                Skeleton skeleton = (Skeleton) vex.getWorld().spawnEntity(vex.getLocation(), EntityType.SKELETON);
                skeleton.getEquipment().setHelmet(new ItemStack(Material.CHAINMAIL_HELMET));
            } else if (target instanceof Villager) {
                vex.getWorld().strikeLightningEffect(vex.getLocation());
                Zombie zombie = (Zombie) vex.getWorld().spawnEntity(vex.getLocation(), EntityType.ZOMBIE);
                zombie.getEquipment().setHelmet(new ItemStack(Material.CARVED_PUMPKIN));
            }
        }
    }

    @EventHandler
    private void onFangSpawn(EntitySpawnEvent evt) {
        if (evt.getEntityType() != EntityType.EVOKER_FANGS)
            return;
        EvokerFangs fangs = (EvokerFangs) evt.getEntity();
        LivingEntity owner = fangs.getOwner();
        if (owner instanceof Spellcaster && isNecromancer((Spellcaster) owner)) {
            evt.setCancelled(true);
            LightningStrike lightning = fangs.getWorld().strikeLightning(fangs.getLocation());
            lightning.getPersistentDataContainer().set(getNecromancerLightningNamespacedKey(), PersistentDataType.BYTE,
                    (byte) 0);
        }
    }

    @EventHandler
    private void onLightningFire(BlockIgniteEvent evt) {
        if (evt.getCause() != IgniteCause.LIGHTNING)
            return;
        LightningStrike lightning = (LightningStrike) evt.getIgnitingEntity();
        if (isNecromancerLightning(lightning))
            evt.setCancelled(true);
    }

    @EventHandler
    private void onLightningDamage(EntityDamageByEntityEvent evt) {
        if (evt.getCause() != DamageCause.LIGHTNING)
            return;
        if (!(evt.getEntity() instanceof Raider))
            return;
        if (isNecromancerLightning((LightningStrike) evt.getDamager()))
            evt.setCancelled(true);
    }

    @EventHandler
    private void onLightningSetFire(EntityCombustByEntityEvent evt) {
        if (!(evt.getCombuster() instanceof LightningStrike))
            return;
        if (!isNecromancerLightning((LightningStrike) evt.getCombuster()))
            return;
        if (evt.getEntity() instanceof Raider)
            evt.setCancelled(true);
    }

    @EventHandler
    private void onVillagerTransform(org.bukkit.event.entity.EntityTransformEvent evt) {
        if (evt.getTransformReason() == org.bukkit.event.entity.EntityTransformEvent.TransformReason.LIGHTNING) {
            if (evt.getEntityType() == EntityType.WITCH) {
                if (isNecromancerLightningNearby(evt.getEntity().getLocation())) {
                    evt.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    private void onCreatureSpawn(CreatureSpawnEvent evt) {
        if (evt.getSpawnReason() == CreatureSpawnEvent.SpawnReason.LIGHTNING) {
            if (evt.getEntityType() == EntityType.WITCH) {
                if (isNecromancerLightningNearby(evt.getLocation())) {
                    evt.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    private void onDeath(EntityDeathEvent evt) {
        if (evt.getEntityType() != EntityType.EVOKER)
            return;
        Spellcaster evoker = (Spellcaster) evt.getEntity();
        if (isNecromancer(evoker)) {
            evt.setDroppedExp(getXpForLevel(25));
        }
    }

    private int getXpForLevel(int level) {
        if (level <= 16) {
            return level * level + 6 * level;
        } else if (level <= 31) {
            return (int) (2.5 * level * level - 40.5 * level + 360);
        } else {
            return (int) (4.5 * level * level - 162.5 * level + 2220);
        }
    }

    private ItemStack getDefaultBanner() {
        ItemStack banner = new ItemStack(Material.GRAY_BANNER);
        BannerMeta meta = (BannerMeta) banner.getItemMeta();
        meta.addPattern(new Pattern(DyeColor.YELLOW, PatternType.RHOMBUS));
        meta.addPattern(new Pattern(DyeColor.GRAY, PatternType.STRIPE_DOWNLEFT));
        meta.addPattern(new Pattern(DyeColor.YELLOW, PatternType.STRIPE_MIDDLE));
        meta.addPattern(new Pattern(DyeColor.BLACK, PatternType.SKULL));
        meta.addPattern(new Pattern(DyeColor.WHITE, PatternType.SKULL));
        meta.addPattern(new Pattern(DyeColor.BLACK, PatternType.BORDER));
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.setDisplayName(ChatColor.GOLD.toString() + ChatColor.ITALIC + "Necromancer Banner");
        banner.setItemMeta(meta);
        return banner;
    }

    private boolean isNecromancer(Spellcaster entity) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        return pdc.has(getNecromancerNamespacedKey(), PersistentDataType.BYTE);
    }

    private boolean isNecromancerLightning(LightningStrike lightning) {
        PersistentDataContainer pdc = lightning.getPersistentDataContainer();
        return pdc.has(getNecromancerLightningNamespacedKey(), PersistentDataType.BYTE);
    }

    private boolean isNecromancerLightningNearby(Location loc) {
        double radius = 6.0;
        for (org.bukkit.entity.Entity entity : loc.getWorld().getNearbyEntities(loc, radius, radius, radius)) {
            if (entity instanceof org.bukkit.entity.LightningStrike) {
                org.bukkit.entity.LightningStrike lightning = (org.bukkit.entity.LightningStrike) entity;
                if (lightning.getPersistentDataContainer().has(getNecromancerLightningNamespacedKey(), PersistentDataType.BYTE)) {
                    return true;
                }
            }
        }
        return false;
    }

    private NamespacedKey getNecromancerNamespacedKey() {
        return new NamespacedKey(JavaPlugin.getPlugin(LargeRaids.class), "necromancer");
    }

    private NamespacedKey getNecromancerLightningNamespacedKey() {
        return new NamespacedKey(JavaPlugin.getPlugin(LargeRaids.class), "necromancer_lightning");
    }
}