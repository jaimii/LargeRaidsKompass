package com.solarrabbit.largeraids.raid.mob.manager;

import com.solarrabbit.largeraids.LargeRaids;
import com.solarrabbit.largeraids.config.custommobs.CustomMobsConfig;
import com.solarrabbit.largeraids.raid.mob.Necromancer;
import com.solarrabbit.largeraids.util.ChatColorUtil;
import com.solarrabbit.largeraids.util.VersionUtil;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;

import net.kyori.adventure.text.Component;

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
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class NecromancerManager implements CustomRaiderManager, Listener {
    private static final int DEATH_XP_POINTS = getXpForLevel(25);
    private static final Component NECROMANCER_NAME = ChatColorUtil.parse("<gold>Necromancer");

    private final LargeRaids plugin;
    private final NamespacedKey necromancerKey;
    private final NamespacedKey necromancerLightningKey;
    private final ItemStack bannerTemplate;
    private final ItemStack chainHelmetTemplate;
    private final ItemStack pumpkinTemplate;

    public NecromancerManager() {
        this.plugin = JavaPlugin.getPlugin(LargeRaids.class);
        this.necromancerKey = new NamespacedKey(plugin, "necromancer");
        this.necromancerLightningKey = new NamespacedKey(plugin, "necromancer_lightning");
        this.bannerTemplate = createDefaultBanner();
        this.chainHelmetTemplate = new ItemStack(Material.CHAINMAIL_HELMET);
        this.pumpkinTemplate = new ItemStack(Material.CARVED_PUMPKIN);
    }

    @Override
    public void loadSettings(CustomMobsConfig config) {
    }

    @Override
    public Necromancer spawn(Location location) {
        Spellcaster evoker = (Spellcaster) location.getWorld().spawnEntity(location, EntityType.EVOKER);
        EntityEquipment equipment = evoker.getEquipment();
        if (equipment != null) {
            equipment.setHelmet(bannerTemplate.clone());
            equipment.setHelmetDropChance(1.0f);
        }

        evoker.setPatrolLeader(true);
        evoker.getPersistentDataContainer().set(necromancerKey, PersistentDataType.BYTE, (byte) 0);
        evoker.customName(NECROMANCER_NAME);

        EvokerGoalUtil.injectRaidFleeGoal(evoker, location);

        return new Necromancer(evoker);
    }

    @EventHandler
    private void onSummonVex(CreatureSpawnEvent evt) {
        if (evt.getEntityType() != EntityType.VEX)
            return;
        Vex vex = (Vex) evt.getEntity();
        LivingEntity owner = VersionUtil.getCraftVexWrapper(vex).getOwner();
        if (!(owner instanceof Spellcaster evoker))
            return;
        if (isNecromancer(evoker)) {
            evt.setCancelled(true);
            LivingEntity target = evoker.getTarget();
            Location loc = vex.getLocation();
            if (target instanceof HumanEntity) {
                loc.getWorld().strikeLightningEffect(loc);
                Skeleton skeleton = (Skeleton) loc.getWorld().spawnEntity(loc, EntityType.SKELETON);
                if (skeleton.getEquipment() != null) {
                    skeleton.getEquipment().setHelmet(chainHelmetTemplate.clone());
                }
            } else if (target instanceof Villager) {
                loc.getWorld().strikeLightningEffect(loc);
                Zombie zombie = (Zombie) loc.getWorld().spawnEntity(loc, EntityType.ZOMBIE);
                if (zombie.getEquipment() != null) {
                    zombie.getEquipment().setHelmet(pumpkinTemplate.clone());
                }
            }
        }
    }

    @EventHandler
    private void onFangSpawn(EntitySpawnEvent evt) {
        if (evt.getEntityType() != EntityType.EVOKER_FANGS)
            return;
        EvokerFangs fangs = (EvokerFangs) evt.getEntity();
        LivingEntity owner = fangs.getOwner();
        if (owner instanceof Spellcaster evoker && isNecromancer(evoker)) {
            evt.setCancelled(true);
            LightningStrike lightning = fangs.getWorld().strikeLightning(fangs.getLocation());
            lightning.getPersistentDataContainer().set(necromancerLightningKey, PersistentDataType.BYTE, (byte) 0);
        }
    }

    @EventHandler
    private void onLightningFire(BlockIgniteEvent evt) {
        if (evt.getCause() != IgniteCause.LIGHTNING)
            return;
        if (evt.getIgnitingEntity() instanceof LightningStrike lightning && isNecromancerLightning(lightning))
            evt.setCancelled(true);
    }

    @EventHandler
    private void onLightningDamage(EntityDamageByEntityEvent evt) {
        if (evt.getCause() != DamageCause.LIGHTNING)
            return;
        if (!(evt.getEntity() instanceof Raider))
            return;
        if (evt.getDamager() instanceof LightningStrike lightning && isNecromancerLightning(lightning))
            evt.setCancelled(true);
    }

    @EventHandler
    private void onLightningSetFire(EntityCombustByEntityEvent evt) {
        if (!(evt.getCombuster() instanceof LightningStrike lightning))
            return;
        if (!isNecromancerLightning(lightning))
            return;
        if (evt.getEntity() instanceof Raider)
            evt.setCancelled(true);
    }

    @EventHandler
    private void onVillagerTransform(EntityTransformEvent evt) {
        if (evt.getTransformReason() == EntityTransformEvent.TransformReason.LIGHTNING
                && evt.getEntityType() == EntityType.WITCH) {
            if (isNecromancerLightningNearby(evt.getEntity().getLocation())) {
                evt.setCancelled(true);
            }
        }
    }

    @EventHandler
    private void onCreatureSpawn(CreatureSpawnEvent evt) {
        if (evt.getSpawnReason() == CreatureSpawnEvent.SpawnReason.LIGHTNING
                && evt.getEntityType() == EntityType.WITCH) {
            if (isNecromancerLightningNearby(evt.getLocation())) {
                evt.setCancelled(true);
            }
        }
    }

    @EventHandler
    private void onDeath(EntityDeathEvent evt) {
        if (evt.getEntityType() != EntityType.EVOKER)
            return;
        Spellcaster evoker = (Spellcaster) evt.getEntity();
        if (isNecromancer(evoker)) {
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

    private static ItemStack createDefaultBanner() {
        ItemStack banner = new ItemStack(Material.GRAY_BANNER);
        BannerMeta meta = (BannerMeta) banner.getItemMeta();
        if (meta != null) {
            meta.addPattern(new Pattern(DyeColor.YELLOW, PatternType.RHOMBUS));
            meta.addPattern(new Pattern(DyeColor.GRAY, PatternType.STRIPE_DOWNLEFT));
            meta.addPattern(new Pattern(DyeColor.YELLOW, PatternType.STRIPE_MIDDLE));
            meta.addPattern(new Pattern(DyeColor.BLACK, PatternType.SKULL));
            meta.addPattern(new Pattern(DyeColor.WHITE, PatternType.SKULL));
            meta.addPattern(new Pattern(DyeColor.BLACK, PatternType.BORDER));
            meta.displayName(ChatColorUtil.parseItem("<gold><italic>Necromancer Banner"));
            banner.setItemMeta(meta);
        }
        banner.setData(DataComponentTypes.TOOLTIP_DISPLAY,
                TooltipDisplay.tooltipDisplay().addHiddenComponents(DataComponentTypes.BANNER_PATTERNS).build());
        return banner;
    }

    private boolean isNecromancer(Spellcaster entity) {
        return entity.getPersistentDataContainer().has(necromancerKey, PersistentDataType.BYTE);
    }

    private boolean isNecromancerLightning(LightningStrike lightning) {
        return lightning.getPersistentDataContainer().has(necromancerLightningKey, PersistentDataType.BYTE);
    }

    private boolean isNecromancerLightningNearby(Location loc) {
        if (loc.getWorld() == null) return false;
        for (LightningStrike lightning : loc.getWorld().getNearbyEntitiesByType(LightningStrike.class, loc, 6.0, 6.0, 6.0)) {
            if (isNecromancerLightning(lightning)) {
                return true;
            }
        }
        return false;
    }
}