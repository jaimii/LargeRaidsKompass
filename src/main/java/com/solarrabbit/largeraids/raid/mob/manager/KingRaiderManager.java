package com.solarrabbit.largeraids.raid.mob.manager;

import com.solarrabbit.largeraids.LargeRaids;
import com.solarrabbit.largeraids.config.custommobs.CustomMobsConfig;
import com.solarrabbit.largeraids.raid.mob.KingRaider;
import com.solarrabbit.largeraids.util.VersionUtil;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.EntityEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.boss.BossBar;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Raider;
import org.bukkit.entity.Ravager;
import org.bukkit.entity.Spellcaster;
import org.bukkit.entity.Vex;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;

public class KingRaiderManager implements BossRaiderManager, Listener {
    private double ravagerHealth;
    private double ravagerDamage;
    private double fangDamage;
    private int fireTicks;
    private int regenLevel;
    private double evokerHealth;
    private int evokerRegenLevel;
    private static final EntityType RIDER_TYPE = EntityType.EVOKER;

    public KingRaiderManager() {
    }

    @Override
    public void loadSettings(CustomMobsConfig config) {
        ravagerHealth = config.getKingRaiderConfig().getRavagerHealth();
        ravagerDamage = config.getKingRaiderConfig().getRavagerDamage();
        fangDamage = config.getKingRaiderConfig().getFangDamage();
        fireTicks = config.getKingRaiderConfig().getFireTicks();
        regenLevel = config.getKingRaiderConfig().getRegenLevel();
        evokerHealth = config.getKingRaiderConfig().getEvokerHealth();
        evokerRegenLevel = config.getKingRaiderConfig().getEvokerRegenLevel();
    }

    @Override
    public KingRaider spawn(Location location) {
        Ravager ravager = (Ravager) location.getWorld().spawnEntity(location, EntityType.RAVAGER);
        ravager.setCustomName("§cJuggernaut §5Ravager");
        ravager.getAttribute(Attribute.MAX_HEALTH).setBaseValue(ravagerHealth);
        ravager.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(ravagerDamage);
        ravager.setHealth(ravagerHealth);
        ravager.getPersistentDataContainer().set(getJuggernautNamespacedKey(), PersistentDataType.BYTE, (byte) 0);

        NamespacedKey thresholdKey = new NamespacedKey(JavaPlugin.getPlugin(LargeRaids.class), "juggernaut_next_trigger");
        ravager.getPersistentDataContainer().set(thresholdKey, PersistentDataType.DOUBLE, ravagerHealth - 100.0);

        if (regenLevel >= 0)
            ravager.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, regenLevel));

        Spellcaster rider = (Spellcaster) location.getWorld().spawnEntity(location, RIDER_TYPE);
        rider.getAttribute(Attribute.MAX_HEALTH).setBaseValue(evokerHealth);
        rider.setHealth(evokerHealth);

        EntityEquipment equipment = rider.getEquipment();
        equipment.setHelmet(getDefaultBanner());
        equipment.setHelmetDropChance(1.0f);
        rider.getPersistentDataContainer().set(getKingNamespacedKey(), PersistentDataType.BYTE, (byte) 0);
        rider.setCustomName("§cRaid §5Captain");

        if (evokerRegenLevel >= 0) {
            rider.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, evokerRegenLevel, false, false, false));
        }

        BossBar bossBar = createBossBar(rider);
        createBossBar(ravager);

        ravager.addPassenger(rider);
        return new KingRaider(rider, ravager, bossBar);
    }

    @EventHandler
    private void onRavagerTakeDamage(EntityDamageByEntityEvent evt) {
        if (!(evt.getEntity() instanceof Ravager))
            return;

        Entity damager = evt.getDamager();
        Entity realDamager = damager;

        if (damager instanceof Projectile) {
            ProjectileSource source = ((Projectile) damager).getShooter();
            if (source instanceof Entity) {
                realDamager = (Entity) source;
            }
        }

        if (damager instanceof EvokerFangs) {
            LivingEntity owner = ((EvokerFangs) damager).getOwner();
            if (owner != null) {
                realDamager = owner;
            }
        }

        if (realDamager instanceof Raider) {
            evt.setCancelled(true);
        }
    }

    @EventHandler
    private void onJuggernautDamage(EntityDamageEvent evt) {
        if (!(evt.getEntity() instanceof Ravager))
            return;

        Ravager ravager = (Ravager) evt.getEntity();
        if (!isJuggernaut(ravager))
            return;

        double currentHealth = ravager.getHealth();
        double finalDamage = evt.getFinalDamage();
        double nextHealth = currentHealth - finalDamage;

        if (nextHealth <= 0)
            return;

        PersistentDataContainer pdc = ravager.getPersistentDataContainer();
        NamespacedKey thresholdKey = new NamespacedKey(JavaPlugin.getPlugin(LargeRaids.class), "juggernaut_next_trigger");

        double maxHealth = ravager.getAttribute(Attribute.MAX_HEALTH).getBaseValue();
        double nextTrigger = pdc.has(thresholdKey, PersistentDataType.DOUBLE)
                ? pdc.get(thresholdKey, PersistentDataType.DOUBLE)
                : maxHealth - 100.0;

        if (nextHealth <= nextTrigger) {
            LivingEntity target = ravager.getTarget();
            if (target == null) {
                double scanRadius = 15.0;
                for (Entity near : ravager.getNearbyEntities(scanRadius, scanRadius, scanRadius)) {
                    if (near instanceof LivingEntity) {
                        LivingEntity living = (LivingEntity) near;
                        if (isValidTarget(living)) {
                            target = living;
                            break;
                        }
                    }
                }
            }

            if (target != null) {
                triggerSonicBoomAttack(ravager, target);
            }

            while (nextHealth <= nextTrigger) {
                nextTrigger -= 100.0;
            }
            pdc.set(thresholdKey, PersistentDataType.DOUBLE, nextTrigger);
        }
    }

    @EventHandler
    private void onJuggernautShieldBlock(EntityDamageByEntityEvent evt) {
        if (!(evt.getDamager() instanceof Ravager))
            return;

        Ravager ravager = (Ravager) evt.getDamager();
        if (!isJuggernaut(ravager))
            return;

        if (!(evt.getEntity() instanceof Player))
            return;

        Player player = (Player) evt.getEntity();

        if (player.isBlocking()) {
            Vector playerLook = player.getLocation().getDirection().setY(0).normalize();
            Vector toRavager = ravager.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0).normalize();
            double dot = playerLook.dot(toRavager);

            if (dot > 0.0) {
                ravager.playEffect(EntityEffect.RAVAGER_STUNNED);
                triggerSonicBoomAttack(ravager, player);
            }
        }
    }

    private void triggerSonicBoomAttack(Ravager ravager, LivingEntity target) {
        if (target == null) return;
        final LivingEntity finalTarget = target;

        ravager.playEffect(EntityEffect.RAVAGER_STUNNED);
        ravager.getWorld().playSound(ravager.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.5F, 1.1F);

        Bukkit.getScheduler().runTaskLater(JavaPlugin.getPlugin(LargeRaids.class), () -> {
            if (ravager.isDead() || finalTarget.isDead()) return;

            Location start = ravager.getEyeLocation();
            Location end = finalTarget.getEyeLocation();
            Vector direction = end.toVector().subtract(start.toVector()).normalize();
            double maxRange = 20.0;

            ravager.playEffect(EntityEffect.ENTITY_ATTACK);
            ravager.getWorld().playSound(start, Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0F, 1.0F);

            DamageSource damageSource = DamageSource.builder(DamageType.SONIC_BOOM)
                    .withCausingEntity(ravager)
                    .withDirectEntity(ravager)
                    .build();

            Set<LivingEntity> hitEntities = new HashSet<>();

            for (double d = 0.0; d < maxRange; d += 1.5) {
                Location point = start.clone().add(direction.clone().multiply(d));
                point.getWorld().spawnParticle(Particle.SONIC_BOOM, point, 1, 0.0, 0.0, 0.0, 0.0);

                for (Entity entity : point.getWorld().getNearbyEntities(point, 1.5, 1.5, 1.5)) {
                    if (entity instanceof LivingEntity && entity != ravager) {
                        LivingEntity living = (LivingEntity) entity;
                        if (isValidTarget(living) && hitEntities.add(living)) {
                            living.damage(8.0, damageSource);
                            living.setVelocity(living.getVelocity().add(direction.clone().multiply(0.4)));
                        }
                    }
                }
            }

        }, 40L);
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (entity instanceof Player) {
            Player p = (Player) entity;
            return p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR;
        }
        if (entity instanceof Raider || entity instanceof Vex) {
            return false;
        }
        return !entity.isDead();
    }

    @EventHandler
    private void onFangsSpawn(EntitySpawnEvent evt) {
        if (evt.getEntityType() != EntityType.EVOKER_FANGS)
            return;
        EvokerFangs fangs = (EvokerFangs) evt.getEntity();
        LivingEntity owner = fangs.getOwner();
        if (owner instanceof Spellcaster && isKing((Spellcaster) owner)) {
            fangs.setVisualFire(true);
            fangs.getPersistentDataContainer().set(getKingFangsNamespacedKey(), PersistentDataType.BYTE, (byte) 0);
        }
    }

    @EventHandler
    private void onFangsAttack(EntityDamageByEntityEvent evt) {
        if (evt.getDamager().getType() != EntityType.EVOKER_FANGS)
            return;
        if (evt.getEntity() instanceof Raider)
            return;
        if (isKingFangs((EvokerFangs) evt.getDamager())) {
            evt.getEntity().setFireTicks(fireTicks);
            evt.setDamage(fangDamage);
        }
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
        if (isKing(evoker))
            vex.getEquipment().setItemInMainHand(getKingVexSword());
    }

    @EventHandler
    private void onKingDamage(EntityDamageEvent evt) {
        if (evt.getEntityType() != RIDER_TYPE)
            return;
        Spellcaster king = (Spellcaster) evt.getEntity();
        Entity vehicle = king.getVehicle();
        if (isKing(king) && vehicle instanceof Ravager && isJuggernaut((Ravager) vehicle))
            evt.setCancelled(true);
    }

    private ItemStack getDefaultBanner() {
        ItemStack banner = new ItemStack(Material.WHITE_BANNER);
        BannerMeta meta = (BannerMeta) banner.getItemMeta();
        meta.addPattern(new Pattern(DyeColor.CYAN, PatternType.RHOMBUS));
        meta.addPattern(new Pattern(DyeColor.LIGHT_GRAY, PatternType.STRIPE_BOTTOM));
        meta.addPattern(new Pattern(DyeColor.BLACK, PatternType.HALF_HORIZONTAL));
        meta.addPattern(new Pattern(DyeColor.BLACK, PatternType.STRIPE_MIDDLE));
        meta.addPattern(new Pattern(DyeColor.GRAY, PatternType.STRIPE_CENTER));
        meta.addPattern(new Pattern(DyeColor.BLACK, PatternType.SKULL));
        meta.addPattern(new Pattern(DyeColor.LIGHT_GRAY, PatternType.CIRCLE));
        meta.addPattern(new Pattern(DyeColor.BLACK, PatternType.TRIANGLE_TOP));
        meta.addPattern(new Pattern(DyeColor.BLACK, PatternType.BORDER));
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.setDisplayName(ChatColor.DARK_PURPLE.toString() + ChatColor.BOLD + "Raid Captain Banner");
        banner.setItemMeta(meta);
        return banner;
    }

    private ItemStack getKingVexSword() {
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
        item.addEnchantment(Enchantment.FIRE_ASPECT, 2);
        item.addEnchantment(Enchantment.SHARPNESS, 3);
        return item;
    }

    private boolean isJuggernaut(Ravager entity) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        return pdc.has(getJuggernautNamespacedKey(), PersistentDataType.BYTE);
    }

    private boolean isKing(Raider entity) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        return pdc.has(getKingNamespacedKey(), PersistentDataType.BYTE);
    }

    private boolean isKingFangs(EvokerFangs fangs) {
        PersistentDataContainer pdc = fangs.getPersistentDataContainer();
        return pdc.has(getKingFangsNamespacedKey(), PersistentDataType.BYTE);
    }

    private NamespacedKey getJuggernautNamespacedKey() {
        return new NamespacedKey(JavaPlugin.getPlugin(LargeRaids.class), "juggernaut");
    }

    private NamespacedKey getKingNamespacedKey() {
        return new NamespacedKey(JavaPlugin.getPlugin(LargeRaids.class), "juggernaut_king");
    }

    private NamespacedKey getKingFangsNamespacedKey() {
        return new NamespacedKey(JavaPlugin.getPlugin(LargeRaids.class), "juggernaut_king_fangs");
    }
}