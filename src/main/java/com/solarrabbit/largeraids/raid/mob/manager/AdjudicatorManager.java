package com.solarrabbit.largeraids.raid.mob.manager;

import com.solarrabbit.largeraids.LargeRaids;
import com.solarrabbit.largeraids.config.custommobs.CustomMobsConfig;
import com.solarrabbit.largeraids.raid.mob.Adjudicator;
import com.solarrabbit.largeraids.raid.mob.AdjudicatorRider;
import com.solarrabbit.largeraids.util.ChatColorUtil;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;

import net.kyori.adventure.text.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;

import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.craftbukkit.entity.CraftRaider;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.Horse;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Raider;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Vindicator;
import org.bukkit.entity.Witch;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.ShieldMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

public class AdjudicatorManager implements CustomRaiderManager, Listener {
    private static final int DEATH_XP_POINTS = getXpForLevel(20);
    private static final Component ADJUDICATOR_NAME = ChatColorUtil.parse("<gold>Adjudicator");
    private static final Component STEED_NAME = ChatColorUtil.parse("<gold>Adjudicator's Steed");
    private static final Sound SPEAR_USE_SOUND = resolveSound("item.spear.use");
    private static final Sound SPEAR_LUNGE_SOUND = resolveSound("item.spear.lunge.1");
    private static final Constructor<?> SPEAR_GOAL_CTOR;
    private static final Object[] SPEAR_GOAL_TEMPLATE_ARGS;

    static {
        Constructor<?> resolvedCtor = null;
        Object[] templateArgs = null;
        try {
            Class<?> goalClass = Class.forName("net.minecraft.world.entity.ai.goal.SpearUseGoal");
            for (Constructor<?> ctor : goalClass.getConstructors()) {
                Class<?>[] params = ctor.getParameterTypes();
                if (params.length == 0) continue;

                resolvedCtor = ctor;
                templateArgs = new Object[params.length];
                for (int i = 1; i < params.length; i++) {
                    if (params[i] == double.class) templateArgs[i] = 1.25;
                    else if (params[i] == float.class) templateArgs[i] = 32.0F;
                    else if (params[i] == int.class) templateArgs[i] = 20;
                    else if (params[i] == boolean.class) templateArgs[i] = false;
                    else templateArgs[i] = null;
                }
                break;
            }
        } catch (ClassNotFoundException ignored) {
        }
        SPEAR_GOAL_CTOR = resolvedCtor;
        SPEAR_GOAL_TEMPLATE_ARGS = templateArgs;
    }

    private double health;
    private double horseHealth;

    private final LargeRaids plugin;
    private final NamespacedKey adjudicatorKey;
    private final NamespacedKey horseKey;
    private final NamespacedKey speedKey;
    private final NamespacedKey necromancerLightningKey;
    private final AttributeModifier chargeSpeedModifier;
    private final ItemStack bannerTemplate;
    private final ItemStack shieldTemplate;
    private final ItemStack spearTemplate;

    private final Set<Vindicator> activeAdjudicators = ConcurrentHashMap.newKeySet();
    private final WeakHashMap<Vindicator, Horse> linkedHorses = new WeakHashMap<>();
    private final WeakHashMap<Vindicator, Boolean> lineOfSightCache = new WeakHashMap<>();
    private final WeakHashMap<Vindicator, Long> attackCooldowns = new WeakHashMap<>();
    private final WeakHashMap<Vindicator, Boolean> hasHitInCurrentCycle = new WeakHashMap<>();

    public AdjudicatorManager() {
        this.plugin = JavaPlugin.getPlugin(LargeRaids.class);
        this.adjudicatorKey = new NamespacedKey(plugin, "adjudicator");
        this.horseKey = new NamespacedKey(plugin, "adjudicator_horse_uuid");
        this.speedKey = new NamespacedKey(plugin, "adjudicator_charge_speed");
        this.necromancerLightningKey = new NamespacedKey(plugin, "necromancer_lightning");
        this.chargeSpeedModifier = new AttributeModifier(speedKey, 0.20, AttributeModifier.Operation.ADD_NUMBER);
        this.bannerTemplate = createAdjudicatorBanner();
        this.shieldTemplate = createAdjudicatorShield();
        this.spearTemplate = new ItemStack(Material.IRON_SPEAR);
        startTickTask();
    }

    private void startTickTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activeAdjudicators.isEmpty())
                return;

            Iterator<Vindicator> iterator = activeAdjudicators.iterator();
            while (iterator.hasNext()) {
                Vindicator vindicator = iterator.next();
                if (vindicator == null || vindicator.isDead() || !vindicator.isValid()) {
                    if (vindicator != null && vindicator.isDead()) {
                        removeLinkedHorse(vindicator);
                    }
                    iterator.remove();
                    continue;
                }

                if (!vindicator.isInsideVehicle()) {
                    Horse horse = getLinkedHorse(vindicator);
                    if (horse != null && !horse.isDead() && horse.isValid()) {
                        horse.addPassenger(vindicator);
                    }
                }
                handleAdjudicatorAttack(vindicator);
            }
        }, 0L, 2L);

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (World world : Bukkit.getWorlds()) {
                for (Vindicator vindicator : world.getEntitiesByClass(Vindicator.class)) {
                    if (isAdjudicator(vindicator)) {
                        restoreAdjudicator(vindicator);
                    }
                }
            }
        });
    }

    public void restoreAdjudicator(Vindicator vindicator) {
        if (vindicator == null || vindicator.isDead() || !vindicator.isValid()) return;
        activeAdjudicators.remove(vindicator);
        activeAdjudicators.add(vindicator);
        injectSpearUseGoal(vindicator);
    }

    private Horse getLinkedHorse(Vindicator vindicator) {
        if (vindicator.getVehicle() instanceof Horse h) {
            linkedHorses.put(vindicator, h);
            return h;
        }
        Horse cached = linkedHorses.get(vindicator);
        if (cached != null) {
            if (!cached.isDead() && cached.isValid()) {
                return cached;
            }
            linkedHorses.remove(vindicator);
        }

        PersistentDataContainer pdc = vindicator.getPersistentDataContainer();
        String uuidStr = pdc.get(horseKey, PersistentDataType.STRING);
        if (uuidStr != null) {
            try {
                UUID horseUuid = UUID.fromString(uuidStr);
                Entity entity = vindicator.getWorld().getEntity(horseUuid);
                if (entity instanceof Horse horse && !horse.isDead() && horse.isValid()) {
                    linkedHorses.put(vindicator, horse);
                    return horse;
                }
            } catch (IllegalArgumentException ignored) {}
        }
        return null;
    }

    private void removeLinkedHorse(Vindicator vindicator) {
        Horse horse = getLinkedHorse(vindicator);
        linkedHorses.remove(vindicator);
        if (horse != null && !horse.isDead()) {
            cleanupGhostArmorStands(horse);
            horse.remove();
        }
    }

    private void handleAdjudicatorAttack(Vindicator vindicator) {
        LivingEntity target = vindicator.getTarget();
        if (target == null || target.isDead()) {
            stopUsingSpear(vindicator);
            hasHitInCurrentCycle.put(vindicator, false);
            return;
        }

        if (target instanceof Player p) {
            if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) {
                stopUsingSpear(vindicator);
                hasHitInCurrentCycle.put(vindicator, false);
                return;
            }
        }

        boolean hasLos;
        if (vindicator.getTicksLived() % 4 == 0) {
            hasLos = vindicator.hasLineOfSight(target);
            lineOfSightCache.put(vindicator, hasLos);
        } else {
            hasLos = lineOfSightCache.getOrDefault(vindicator, true);
        }

        if (!hasLos) {
            stopUsingSpear(vindicator);
            return;
        }

        double distanceSq = vindicator.getLocation().distanceSquared(target.getLocation());

        boolean riding = vindicator.isInsideVehicle();
        double maxReach = riding ? 6.0 : 5.2;
        double maxReachSq = maxReach * maxReach;

        boolean hit = hasHitInCurrentCycle.getOrDefault(vindicator, false);
        long now = System.currentTimeMillis();
        long lastAttack = attackCooldowns.getOrDefault(vindicator, 0L);

        if (hit && (distanceSq > 42.25 || (now - lastAttack) >= 2000L)) {
            hit = false;
            hasHitInCurrentCycle.put(vindicator, false);
        }

        if (!hit) {
            LivingEntity activeMover = (riding && vindicator.getVehicle() instanceof LivingEntity le)
                    ? le
                    : vindicator;

            double chargeSpeedMultiplier = riding ? 1.45 : 1.12;
            moveTowardsTarget(activeMover, target.getLocation(), chargeSpeedMultiplier);

            if (distanceSq > maxReachSq) {
                startUsingSpear(vindicator);
            } else {
                if ((now - lastAttack) >= 1200L) {
                    performSpearAttack(vindicator, target);
                    attackCooldowns.put(vindicator, now);
                    hasHitInCurrentCycle.put(vindicator, true);
                    stopUsingSpear(vindicator);
                }
            }
        } else {
            stopUsingSpear(vindicator);

            Location targetLoc = target.getLocation();
            Location currentLoc = vindicator.getLocation();
            Vector direction = currentLoc.toVector().subtract(targetLoc.toVector());
            if (direction.lengthSquared() == 0) {
                direction = new Vector(1, 0, 0);
            } else {
                direction.normalize();
            }
            Location retreatLoc = targetLoc.clone().add(direction.multiply(8.0));

            LivingEntity activeMover = (riding && vindicator.getVehicle() instanceof LivingEntity le)
                    ? le
                    : vindicator;

            double retreatSpeedMultiplier = riding ? 1.55 : 0.60;
            moveTowardsTarget(activeMover, retreatLoc, retreatSpeedMultiplier);
        }
    }

    private void performSpearAttack(Vindicator vindicator, LivingEntity target) {
        vindicator.swingMainHand();

        AttributeInstance attackAttr = vindicator.getAttribute(Attribute.ATTACK_DAMAGE);
        double baseDamage = (attackAttr != null) ? attackAttr.getValue() : 6.0;

        boolean riding = vindicator.isInsideVehicle();
        double finalDamage = baseDamage;

        if (riding) {
            finalDamage *= 2.0;
            playSpearSound(vindicator, SPEAR_LUNGE_SOUND, "minecraft:item.spear.lunge.1", 1.2F, 0.9F);
            target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 8, 0.3, 0.3, 0.3, 0.1);
        } else {
            finalDamage *= 1.5;
            playSpearSound(vindicator, SPEAR_LUNGE_SOUND, "minecraft:item.spear.lunge.1", 1.0F, 1.05F);
            target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 4, 0.2, 0.2, 0.2, 0.05);
        }

        target.damage(finalDamage, vindicator);

        Location startLoc = vindicator.getLocation();
        Vector direction = startLoc.getDirection().normalize();
        double pierceRange = riding ? 6.0 : 5.2;

        int piercedCount = 0;
        for (Entity entity : target.getNearbyEntities(pierceRange, 3.0, pierceRange)) {
            if (piercedCount >= 3) break;
            if (entity == target || entity == vindicator || entity == vindicator.getVehicle()) continue;

            if (entity instanceof LivingEntity secondaryTarget) {
                if (secondaryTarget instanceof Player || secondaryTarget instanceof IronGolem || secondaryTarget instanceof Villager) {
                    Vector toTarget = secondaryTarget.getLocation().toVector().subtract(startLoc.toVector());
                    double projection = toTarget.dot(direction);

                    if (projection > 0 && projection <= pierceRange) {
                        Vector closestPoint = startLoc.toVector().add(direction.clone().multiply(projection));
                        double distSq = secondaryTarget.getLocation().toVector().distanceSquared(closestPoint);

                        if (distSq <= 2.25) {
                            secondaryTarget.damage(finalDamage * 0.75, vindicator);
                            secondaryTarget.getWorld().spawnParticle(Particle.SWEEP_ATTACK, secondaryTarget.getLocation().add(0, 1, 0), 1);
                            piercedCount++;
                        }
                    }
                }
            }
        }
    }

    private void moveTowardsTarget(LivingEntity entity, Location targetLoc, double speed) {
        if (entity instanceof Mob mob) {
            mob.getPathfinder().moveTo(targetLoc, speed);
        }
    }

    private void startUsingSpear(Vindicator vindicator) {
        net.minecraft.world.entity.raid.Raider nms = ((CraftRaider) vindicator).getHandle();
        if (!nms.isUsingItem()) {
            nms.startUsingItem(InteractionHand.MAIN_HAND);
            playSpearSound(vindicator, SPEAR_USE_SOUND, "minecraft:item.spear.use", 1.0F, 1.0F);

            if (!vindicator.isInsideVehicle()) {
                AttributeInstance speedAttribute = vindicator.getAttribute(Attribute.MOVEMENT_SPEED);
                if (speedAttribute != null) {
                    speedAttribute.removeModifier(speedKey);
                    speedAttribute.addModifier(chargeSpeedModifier);
                }
            }
        }
    }

    private void stopUsingSpear(Vindicator vindicator) {
        try {
            net.minecraft.world.entity.raid.Raider nms = ((CraftRaider) vindicator).getHandle();
            if (nms.isUsingItem()) {
                nms.stopUsingItem();
            }
        } finally {
            AttributeInstance speedAttribute = vindicator.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speedAttribute != null) {
                speedAttribute.removeModifier(speedKey);
            }
        }
    }

    private static Sound resolveSound(String keyName) {
        try {
            NamespacedKey key = NamespacedKey.minecraft(keyName);
            return Registry.SOUNDS.get(key);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void playSpearSound(Entity entity, Sound cachedSound, String fallbackKey, float volume, float pitch) {
        if (cachedSound != null) {
            entity.getWorld().playSound(entity.getLocation(), cachedSound, volume, pitch);
        } else {
            entity.getWorld().playSound(entity.getLocation(), fallbackKey, volume, pitch);
        }
    }

    private boolean isAdjudicator(Vindicator vindicator) {
        return vindicator.getPersistentDataContainer().has(adjudicatorKey, PersistentDataType.BYTE);
    }

    private boolean isAdjudicatorHorse(Entity entity) {
        return entity instanceof Horse && entity.getPersistentDataContainer().has(adjudicatorKey, PersistentDataType.BYTE);
    }

    @Override
    public void loadSettings(CustomMobsConfig config) {
        if (config != null && config.getAdjudicatorConfig() != null) {
            health = config.getAdjudicatorConfig().getHealth();
            horseHealth = config.getAdjudicatorConfig().getHorseHealth();
        } else {
            health = 24.0;
            horseHealth = 30.0;
        }
    }

    @Override
    public Adjudicator spawn(Location location) {
        Vindicator vindicator = (Vindicator) location.getWorld().spawnEntity(location, EntityType.VINDICATOR);

        AttributeInstance followRange = vindicator.getAttribute(Attribute.FOLLOW_RANGE);
        if (followRange != null) {
            followRange.setBaseValue(64.0);
        }

        vindicator.getAttribute(Attribute.MAX_HEALTH).setBaseValue(health);
        vindicator.setHealth(health);

        vindicator.getAttribute(Attribute.ARMOR).setBaseValue(8.0);
        vindicator.setRemoveWhenFarAway(false);

        EntityEquipment equipment = vindicator.getEquipment();
        if (equipment != null) {
            equipment.setItemInMainHand(spearTemplate.clone());
            equipment.setItemInOffHand(shieldTemplate.clone());
            equipment.setHelmet(bannerTemplate.clone());
            equipment.setHelmetDropChance(1.0f);
        }

        vindicator.setPatrolLeader(true);

        PersistentDataContainer pdc = vindicator.getPersistentDataContainer();
        pdc.set(adjudicatorKey, PersistentDataType.BYTE, (byte) 0);
        vindicator.customName(ADJUDICATOR_NAME);

        activeAdjudicators.add(vindicator);
        injectSpearUseGoal(vindicator);

        return new Adjudicator(vindicator);
    }

    public AdjudicatorRider spawnRider(Location location) {
        Horse horse = (Horse) location.getWorld().spawnEntity(location, EntityType.HORSE);
        horse.getAttribute(Attribute.MAX_HEALTH).setBaseValue(horseHealth);
        horse.setHealth(horseHealth);

        AttributeInstance speedAttribute = horse.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttribute != null) {
            speedAttribute.setBaseValue(0.42);
        }

        horse.getInventory().setSaddle(new ItemStack(Material.SADDLE));
        horse.getInventory().setArmor(new ItemStack(Material.IRON_HORSE_ARMOR));

        horse.setTamed(true);
        horse.customName(STEED_NAME);
        horse.setRemoveWhenFarAway(false);
        horse.getPersistentDataContainer().set(adjudicatorKey, PersistentDataType.BYTE, (byte) 0);

        Vindicator vindicator = (Vindicator) location.getWorld().spawnEntity(location, EntityType.VINDICATOR);

        AttributeInstance followRange = vindicator.getAttribute(Attribute.FOLLOW_RANGE);
        if (followRange != null) {
            followRange.setBaseValue(32.0);
        }

        vindicator.getAttribute(Attribute.MAX_HEALTH).setBaseValue(health);
        vindicator.setHealth(health);

        vindicator.getAttribute(Attribute.ARMOR).setBaseValue(8.0);
        vindicator.setRemoveWhenFarAway(false);

        EntityEquipment equipment = vindicator.getEquipment();
        if (equipment != null) {
            equipment.setItemInMainHand(spearTemplate.clone());
            equipment.setItemInOffHand(shieldTemplate.clone());
            equipment.setHelmet(bannerTemplate.clone());
            equipment.setHelmetDropChance(1.0f);
        }

        vindicator.setPatrolLeader(true);

        PersistentDataContainer pdc = vindicator.getPersistentDataContainer();
        pdc.set(adjudicatorKey, PersistentDataType.BYTE, (byte) 0);
        pdc.set(horseKey, PersistentDataType.STRING, horse.getUniqueId().toString());
        vindicator.customName(ADJUDICATOR_NAME);

        linkedHorses.put(vindicator, horse);
        activeAdjudicators.add(vindicator);

        horse.addPassenger(vindicator);
        injectSpearUseGoal(vindicator);

        return new AdjudicatorRider(vindicator, horse);
    }

    @EventHandler
    private void onDeath(EntityDeathEvent evt) {
        LivingEntity entity = evt.getEntity();
        if (entity instanceof Vindicator vindicator && isAdjudicator(vindicator)) {
            evt.setDroppedExp(DEATH_XP_POINTS);
            removeLinkedHorse(vindicator);
            cleanupGhostArmorStands(entity);
            activeAdjudicators.remove(vindicator);
        } else if (isAdjudicatorHorse(entity)) {
            evt.getDrops().clear();
            cleanupGhostArmorStands(entity);
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

    private void injectSpearUseGoal(Vindicator vindicator) {
        try {
            net.minecraft.world.entity.raid.Raider nmsVindicator = ((CraftRaider) vindicator).getHandle();
            GoalSelector goalSelector = EvokerGoalUtil.getGoalSelector(nmsVindicator);
            if (goalSelector == null) return;

            List<Goal> toRemove = new ArrayList<>();
            for (WrappedGoal wrapped : goalSelector.getAvailableGoals()) {
                Goal goal = wrapped.getGoal();
                String className = goal.getClass().getSimpleName();
                if (className.contains("MeleeAttack") || className.contains("JohnnyAttack") || className.contains("SpearUseGoal")) {
                    toRemove.add(goal);
                }
            }
            for (Goal goal : toRemove) {
                goalSelector.removeGoal(goal);
            }

            Goal spearGoal = createSpearUseGoal(nmsVindicator);
            if (spearGoal != null) {
                goalSelector.addGoal(2, spearGoal);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to inject SpearUseGoal into Adjudicator: " + e.getMessage());
        }
    }

    private Goal createSpearUseGoal(net.minecraft.world.entity.raid.Raider nmsVindicator) {
        if (SPEAR_GOAL_CTOR == null || SPEAR_GOAL_TEMPLATE_ARGS == null) {
            return null;
        }
        try {
            Object[] args = SPEAR_GOAL_TEMPLATE_ARGS.clone();
            args[0] = nmsVindicator;
            return (Goal) SPEAR_GOAL_CTOR.newInstance(args);
        } catch (Exception ignored) {
            return null;
        }
    }

    @EventHandler
    private void onAdjudicatorDamage(EntityDamageEvent evt) {
        if (evt.getCause() == DamageCause.KILL || evt.getCause() == DamageCause.VOID)
            return;
        if (!(evt.getEntity() instanceof Vindicator vindicator) || !isAdjudicator(vindicator))
            return;

        Horse horse = getLinkedHorse(vindicator);
        if (horse != null && !horse.isDead() && horse.isValid()) {
            evt.setCancelled(true);
        }
    }

    @EventHandler
    private void onHorseDamage(EntityDamageByEntityEvent evt) {
        if (!(evt.getEntity() instanceof Horse horse) || !isAdjudicatorHorse(horse))
            return;

        Entity damager = evt.getDamager();
        if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Entity shooter) {
                damager = shooter;
            }
        } else if (damager instanceof EvokerFangs fangs) {
            damager = fangs.getOwner();
        } else if (damager instanceof LightningStrike lightning
                && lightning.getPersistentDataContainer().has(necromancerLightningKey, PersistentDataType.BYTE)) {
            evt.setCancelled(true);
            return;
        }

        if (damager instanceof Raider || damager instanceof Witch) {
            evt.setCancelled(true);
        }
    }

    @EventHandler
    private void onEntityDismount(EntityDismountEvent evt) {
        if (!(evt.getEntity() instanceof Vindicator vindicator) || !isAdjudicator(vindicator))
            return;

        Entity dismounted = evt.getDismounted();
        if (!(dismounted instanceof Horse horse) || !isAdjudicatorHorse(horse))
            return;

        Horse linked = getLinkedHorse(vindicator);
        if (linked != null && linked.getUniqueId().equals(horse.getUniqueId())) {
            if (vindicator.isValid() && !vindicator.isDead() && horse.isValid() && !horse.isDead()) {
                if (evt.isCancellable()) {
                    evt.setCancelled(true);
                } else {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (vindicator.isValid() && !vindicator.isDead() && horse.isValid() && !horse.isDead()) {
                            horse.addPassenger(vindicator);
                        }
                    });
                }
            } else {
                cleanupGhostArmorStands(vindicator);
                cleanupGhostArmorStands(horse);
            }
        }
    }

    private void cleanupGhostArmorStands(Entity entity) {
        if (entity == null || entity.getWorld() == null) return;

        Location loc = entity.getLocation();

        for (Entity passenger : entity.getPassengers()) {
            if (passenger instanceof ArmorStand as && as.isInvisible()) {
                passenger.remove();
            }
        }
        if (entity.getVehicle() instanceof ArmorStand as && as.isInvisible()) {
            entity.getVehicle().remove();
        }

        for (ArmorStand as : loc.getWorld().getNearbyEntitiesByType(ArmorStand.class, loc, 2.0, 2.0, 2.0)) {
            if (as.isInvisible() && as.getPassengers().isEmpty()) {
                as.remove();
            }
        }
    }

    private static ItemStack createAdjudicatorBanner() {
        ItemStack banner = new ItemStack(Material.BLACK_BANNER);
        BannerMeta meta = (BannerMeta) banner.getItemMeta();
        if (meta != null) {
            meta.addPattern(new Pattern(DyeColor.RED, PatternType.RHOMBUS));
            meta.addPattern(new Pattern(DyeColor.GRAY, PatternType.STRIPE_BOTTOM));
            meta.addPattern(new Pattern(DyeColor.LIGHT_GRAY, PatternType.STRIPE_CENTER));
            meta.addPattern(new Pattern(DyeColor.GRAY, PatternType.BORDER));
            meta.addPattern(new Pattern(DyeColor.BLACK, PatternType.STRIPE_MIDDLE));
            meta.addPattern(new Pattern(DyeColor.GRAY, PatternType.HALF_HORIZONTAL));
            meta.addPattern(new Pattern(DyeColor.GRAY, PatternType.CIRCLE));
            meta.addPattern(new Pattern(DyeColor.WHITE, PatternType.BORDER));
            meta.displayName(ChatColorUtil.parseItem("<gold>Adjudicator Banner"));
            banner.setItemMeta(meta);
        }
        banner.setData(DataComponentTypes.TOOLTIP_DISPLAY,
                TooltipDisplay.tooltipDisplay().addHiddenComponents(DataComponentTypes.BANNER_PATTERNS).build());
        return banner;
    }

    private static ItemStack createAdjudicatorShield() {
        ItemStack shield = new ItemStack(Material.SHIELD);
        ShieldMeta meta = (ShieldMeta) shield.getItemMeta();
        if (meta != null) {
            meta.setBaseColor(DyeColor.BLACK);
            meta.addPattern(new Pattern(DyeColor.RED, PatternType.RHOMBUS));
            meta.addPattern(new Pattern(DyeColor.GRAY, PatternType.STRIPE_BOTTOM));
            meta.addPattern(new Pattern(DyeColor.LIGHT_GRAY, PatternType.STRIPE_CENTER));
            meta.addPattern(new Pattern(DyeColor.GRAY, PatternType.BORDER));
            meta.addPattern(new Pattern(DyeColor.BLACK, PatternType.STRIPE_MIDDLE));
            meta.addPattern(new Pattern(DyeColor.GRAY, PatternType.HALF_HORIZONTAL));
            meta.addPattern(new Pattern(DyeColor.GRAY, PatternType.CIRCLE));
            meta.addPattern(new Pattern(DyeColor.WHITE, PatternType.BORDER));
            shield.setItemMeta(meta);
        }
        return shield;
    }
}