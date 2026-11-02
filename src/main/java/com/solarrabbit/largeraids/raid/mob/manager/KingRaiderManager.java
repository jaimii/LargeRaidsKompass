package com.solarrabbit.largeraids.raid.mob.manager;

import com.solarrabbit.largeraids.LargeRaids;
import com.solarrabbit.largeraids.config.custommobs.CustomMobsConfig;
import com.solarrabbit.largeraids.raid.mob.KingRaider;
import com.solarrabbit.largeraids.util.ChatColorUtil;
import com.solarrabbit.largeraids.util.VersionUtil;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.util.TriState;

import org.bukkit.Bukkit;
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
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.inventory.EntityEquipment;
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
    private static final EntityType RIDER_TYPE = EntityType.EVOKER;
    private static final int KING_DEATH_XP = getXpForLevel(50);
    private static final int RAVAGER_DEATH_XP = getXpForLevel(100);
    private static final Component JUGGERNAUT_NAME = ChatColorUtil.parse("<red>Juggernaut <dark_purple>Ravager");
    private static final Component KING_NAME = ChatColorUtil.parse("<red>Raid <dark_purple>Captain");

    private final LargeRaids plugin;
    private final NamespacedKey juggernautKey;
    private final NamespacedKey thresholdKey;
    private final NamespacedKey kingKey;
    private final NamespacedKey kingFangsKey;
    private final ItemStack bannerTemplate;
    private final ItemStack vexSwordTemplate;

    private double ravagerHealth;
    private double ravagerDamage;
    private double fangDamage;
    private int fireTicks;
    private int regenLevel;
    private double evokerHealth;
    private int evokerRegenLevel;

    public KingRaiderManager() {
        this.plugin = JavaPlugin.getPlugin(LargeRaids.class);
        this.juggernautKey = new NamespacedKey(plugin, "juggernaut");
        this.thresholdKey = new NamespacedKey(plugin, "juggernaut_next_trigger");
        this.kingKey = new NamespacedKey(plugin, "juggernaut_king");
        this.kingFangsKey = new NamespacedKey(plugin, "juggernaut_king_fangs");
        this.bannerTemplate = createDefaultBanner();
        this.vexSwordTemplate = createKingVexSword();
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
        ravager.customName(JUGGERNAUT_NAME);
        ravager.getAttribute(Attribute.MAX_HEALTH).setBaseValue(ravagerHealth);
        ravager.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(ravagerDamage);
        ravager.setHealth(ravagerHealth);

        PersistentDataContainer ravagerPdc = ravager.getPersistentDataContainer();
        ravagerPdc.set(juggernautKey, PersistentDataType.BYTE, (byte) 0);
        ravagerPdc.set(thresholdKey, PersistentDataType.DOUBLE, ravagerHealth - 100.0);

        if (regenLevel >= 0) {
            ravager.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, regenLevel));
        }

        Spellcaster rider = (Spellcaster) location.getWorld().spawnEntity(location, RIDER_TYPE);
        rider.getAttribute(Attribute.MAX_HEALTH).setBaseValue(evokerHealth);
        rider.setHealth(evokerHealth);

        EntityEquipment equipment = rider.getEquipment();
        if (equipment != null) {
            equipment.setHelmet(bannerTemplate.clone());
            equipment.setHelmetDropChance(1.0f);
        }

        rider.setPatrolLeader(true);
        rider.getPersistentDataContainer().set(kingKey, PersistentDataType.BYTE, (byte) 0);
        rider.customName(KING_NAME);

        if (evokerRegenLevel >= 0) {
            rider.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, evokerRegenLevel, false, false, false));
        }

        BossBar bossBar = createBossBar(rider);
        createBossBar(ravager);

        EvokerGoalUtil.injectRaidFleeGoal(rider, location);

        ravager.addPassenger(rider);
        return new KingRaider(rider, ravager, bossBar);
    }

    @EventHandler
    private void onRavagerTakeDamage(EntityDamageByEntityEvent evt) {
        if (!(evt.getEntity() instanceof Ravager))
            return;

        Entity damager = evt.getDamager();
        Entity realDamager = damager;

        if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Entity shooter) {
                realDamager = shooter;
            }
        } else if (damager instanceof EvokerFangs fangs) {
            LivingEntity owner = fangs.getOwner();
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
        if (!(evt.getEntity() instanceof Ravager ravager) || !isJuggernaut(ravager))
            return;

        double nextHealth = ravager.getHealth() - evt.getFinalDamage();
        if (nextHealth <= 0)
            return;

        PersistentDataContainer pdc = ravager.getPersistentDataContainer();
        double maxHealth = ravager.getAttribute(Attribute.MAX_HEALTH).getBaseValue();
        double nextTrigger = pdc.getOrDefault(thresholdKey, PersistentDataType.DOUBLE, maxHealth - 100.0);

        if (nextHealth <= nextTrigger) {
            LivingEntity target = ravager.getTarget();
            if (target == null) {
                for (Entity near : ravager.getNearbyEntities(15.0, 15.0, 15.0)) {
                    if (near instanceof LivingEntity living && isValidTarget(living)) {
                        target = living;
                        break;
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
        if (!(evt.getDamager() instanceof Ravager ravager) || !isJuggernaut(ravager))
            return;

        if (!(evt.getEntity() instanceof Player player))
            return;

        if (player.isBlocking()) {
            Vector playerLook = player.getLocation().getDirection().setY(0).normalize();
            Vector toRavager = ravager.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0).normalize();
            if (playerLook.dot(toRavager) > 0.0) {
                ravager.playEffect(EntityEffect.RAVAGER_STUNNED);
                triggerSonicBoomAttack(ravager, player);
            }
        }
    }

    private void triggerSonicBoomAttack(Ravager ravager, LivingEntity target) {
        if (target == null) return;

        ravager.playEffect(EntityEffect.RAVAGER_STUNNED);
        ravager.getWorld().playSound(ravager.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.5F, 1.1F);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (ravager.isDead() || target.isDead()) return;

            Location start = ravager.getEyeLocation();
            Location end = target.getEyeLocation();
            Vector direction = end.toVector().subtract(start.toVector()).normalize();

            ravager.playEffect(EntityEffect.ENTITY_ATTACK);
            ravager.getWorld().playSound(start, Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0F, 1.0F);

            DamageSource damageSource = DamageSource.builder(DamageType.SONIC_BOOM)
                    .withCausingEntity(ravager)
                    .withDirectEntity(ravager)
                    .build();

            Set<LivingEntity> hitEntities = new HashSet<>();

            for (double d = 0.0; d < 20.0; d += 1.5) {
                Location point = start.clone().add(direction.clone().multiply(d));
                point.getWorld().spawnParticle(Particle.SONIC_BOOM, point, 1, 0.0, 0.0, 0.0, 0.0);

                for (Entity entity : point.getWorld().getNearbyEntities(point, 1.5, 1.5, 1.5)) {
                    if (entity instanceof LivingEntity living && entity != ravager) {
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
        if (entity instanceof Player p) {
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
        if (owner instanceof Spellcaster evoker && isKing(evoker)) {
            fangs.setVisualFire(TriState.TRUE);
            fangs.getPersistentDataContainer().set(kingFangsKey, PersistentDataType.BYTE, (byte) 0);
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
        if (owner instanceof Spellcaster evoker && isKing(evoker) && vex.getEquipment() != null) {
            vex.getEquipment().setItemInMainHand(vexSwordTemplate.clone());
        }
    }

    @EventHandler
    private void onKingDamage(EntityDamageEvent evt) {
        if (evt.getCause() == DamageCause.KILL || evt.getCause() == DamageCause.VOID)
            return;
        if (evt.getEntityType() != RIDER_TYPE)
            return;
        Spellcaster king = (Spellcaster) evt.getEntity();
        Entity vehicle = king.getVehicle();
        if (isKing(king) && vehicle instanceof Ravager ravager && isJuggernaut(ravager))
            evt.setCancelled(true);
    }

    @EventHandler
    private void onDeath(EntityDeathEvent evt) {
        if (evt.getEntityType() == EntityType.EVOKER) {
            Spellcaster evoker = (Spellcaster) evt.getEntity();
            if (isKing(evoker)) {
                evt.setDroppedExp(KING_DEATH_XP);
            }
        } else if (evt.getEntityType() == EntityType.RAVAGER) {
            Ravager ravager = (Ravager) evt.getEntity();
            if (isJuggernaut(ravager)) {
                evt.setDroppedExp(RAVAGER_DEATH_XP);
            }
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
        ItemStack banner = new ItemStack(Material.WHITE_BANNER);
        BannerMeta meta = (BannerMeta) banner.getItemMeta();
        if (meta != null) {
            meta.addPattern(new Pattern(DyeColor.CYAN, PatternType.RHOMBUS));
            meta.addPattern(new Pattern(DyeColor.LIGHT_GRAY, PatternType.STRIPE_BOTTOM));
            meta.addPattern(new Pattern(DyeColor.BLACK, PatternType.HALF_HORIZONTAL));
            meta.addPattern(new Pattern(DyeColor.BLACK, PatternType.STRIPE_MIDDLE));
            meta.addPattern(new Pattern(DyeColor.GRAY, PatternType.STRIPE_CENTER));
            meta.addPattern(new Pattern(DyeColor.BLACK, PatternType.SKULL));
            meta.addPattern(new Pattern(DyeColor.LIGHT_GRAY, PatternType.CIRCLE));
            meta.addPattern(new Pattern(DyeColor.BLACK, PatternType.TRIANGLE_TOP));
            meta.addPattern(new Pattern(DyeColor.BLACK, PatternType.BORDER));
            meta.displayName(ChatColorUtil.parseItem("<dark_purple><bold>Raid Captain Banner"));
            banner.setItemMeta(meta);
        }
        banner.setData(DataComponentTypes.TOOLTIP_DISPLAY,
                TooltipDisplay.tooltipDisplay().addHiddenComponents(DataComponentTypes.BANNER_PATTERNS).build());
        return banner;
    }

    private static ItemStack createKingVexSword() {
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
        item.addEnchantment(Enchantment.FIRE_ASPECT, 2);
        item.addEnchantment(Enchantment.SHARPNESS, 3);
        return item;
    }

    private boolean isJuggernaut(Ravager entity) {
        return entity.getPersistentDataContainer().has(juggernautKey, PersistentDataType.BYTE);
    }

    private boolean isKing(Raider entity) {
        return entity.getPersistentDataContainer().has(kingKey, PersistentDataType.BYTE);
    }

    private boolean isKingFangs(EvokerFangs fangs) {
        return fangs.getPersistentDataContainer().has(kingFangsKey, PersistentDataType.BYTE);
    }
}