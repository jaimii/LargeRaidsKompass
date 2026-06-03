package com.solarrabbit.largeraids.raid.mob.manager;

import com.solarrabbit.largeraids.LargeRaids;
import com.solarrabbit.largeraids.config.custommobs.CustomMobsConfig;
import com.solarrabbit.largeraids.raid.mob.Adjudicator;
import com.solarrabbit.largeraids.raid.mob.AdjudicatorRider;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Raider;
import org.bukkit.entity.Vindicator;
import org.bukkit.entity.Witch;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.ShieldMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public class AdjudicatorManager implements CustomRaiderManager, Listener {
    private double health;
    private double horseHealth;

    private final LargeRaids plugin;
    private final NamespacedKey adjudicatorKey;
    private final NamespacedKey horseKey;
    private final NamespacedKey speedKey;

    private final WeakHashMap<Vindicator, Long> attackCooldowns = new WeakHashMap<>();
    private final WeakHashMap<Vindicator, Boolean> hasHitInCurrentCycle = new WeakHashMap<>();

    // Lazy Reflection Cache
    private static Method getPathfinderMethod;
    private static Method moveToPathfinderMethod;
    private static Method getHandleMethod;
    private static Method getNavigationMethod;
    private static Method moveToNavigationMethod;
    private static Method isUsingItemMethod;
    private static Method stopUsingItemMethod;
    private static Method startUsingItemMethod;
    private static Field goalSelectorField;
    private static Field availableGoalsField;
    private static Method getGoalMethod;
    private static Method removeGoalMethod;
    private static Object mainHandEnum;

    public AdjudicatorManager() {
        this.plugin = JavaPlugin.getPlugin(LargeRaids.class);
        this.adjudicatorKey = new NamespacedKey(plugin, "adjudicator");
        this.horseKey = new NamespacedKey(plugin, "adjudicator_horse_uuid");
        this.speedKey = new NamespacedKey(plugin, "adjudicator_charge_speed");
        startTickTask();
    }

    private void startTickTask() {
        // Runs a lightweight proximity check task every 2 ticks (10 times a second)
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (org.bukkit.World world : Bukkit.getWorlds()) {
                for (Vindicator vindicator : world.getEntitiesByClass(Vindicator.class)) {
                    if (vindicator.isDead() || !vindicator.isValid()) continue;
                    if (isAdjudicator(vindicator)) {
                        handleAdjudicatorAttack(vindicator);
                    }
                }
            }
        }, 0, 2);
    }

    private void handleAdjudicatorAttack(Vindicator vindicator) {
        LivingEntity target = vindicator.getTarget();
        if (target == null || target.isDead()) {
            stopUsingSpear(vindicator);
            hasHitInCurrentCycle.put(vindicator, false);
            return;
        }

        // Skip creative/spectator players
        if (target instanceof Player) {
            Player p = (Player) target;
            if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) {
                stopUsingSpear(vindicator);
                hasHitInCurrentCycle.put(vindicator, false);
                return;
            }
        }

        // Prevent attacking through solid walls
        if (!vindicator.hasLineOfSight(target)) {
            stopUsingSpear(vindicator);
            return;
        }

        double distanceSq = vindicator.getLocation().distanceSquared(target.getLocation());

        // Spear has an extended combat reach (~5.2 blocks on foot).
        // If riding a horse, we extend this to 6.0 blocks to offset the horse's wider collision box.
        boolean riding = vindicator.isInsideVehicle();
        double maxReach = riding ? 6.0 : 5.2;
        double maxReachSq = maxReach * maxReach;

        boolean hit = hasHitInCurrentCycle.getOrDefault(vindicator, false);
        long now = System.currentTimeMillis();
        long lastAttack = attackCooldowns.getOrDefault(vindicator, 0L);

        // Reset the hit cycle once they retreat far enough (further than 6.5 blocks) OR if a 2.0s timeout is reached
        if (hit && (distanceSq > 42.25 || (now - lastAttack) >= 2000)) {
            hit = false;
            hasHitInCurrentCycle.put(vindicator, false);
        }

        if (!hit) {
            // Force the Adjudicator (or their mount) to charge directly at the target until they land a hit
            LivingEntity activeMover = (riding && vindicator.getVehicle() instanceof LivingEntity)
                    ? (LivingEntity) vindicator.getVehicle()
                    : vindicator;

            // Adjusted speed multiplier for horse riders (1.45 vs 1.25 on foot)
            double chargeSpeedMultiplier = riding ? 1.45 : 1.25;
            moveTowardsTarget(activeMover, target.getLocation(), chargeSpeedMultiplier);

            if (distanceSq > maxReachSq) {
                // Further than combat reach: raise and point spear forward (charge stance)
                startUsingSpear(vindicator);
            } else {
                // Close enough: Trigger charge attack (keep spear raised during strike)
                if ((now - lastAttack) >= 1200) { // Cooldown of 1.2 seconds
                    performSpearAttack(vindicator, target);
                    attackCooldowns.put(vindicator, now);
                    hasHitInCurrentCycle.put(vindicator, true); // Transition to retreat phase

                    // Lower spear immediately after successful charge impact
                    stopUsingSpear(vindicator);
                }
            }
        } else {
            // They have successfully landed their hit in this cycle. Lower the spear during retreat.
            stopUsingSpear(vindicator);

            // Calculate retreat location (opposite direction of the target, 8 blocks away)
            Location targetLoc = target.getLocation();
            Location currentLoc = vindicator.getLocation();
            Vector direction = currentLoc.toVector().subtract(targetLoc.toVector());
            if (direction.lengthSquared() == 0) {
                direction = new Vector(1, 0, 0);
            } else {
                direction.normalize();
            }
            Location retreatLoc = targetLoc.clone().add(direction.multiply(8.0));

            LivingEntity activeMover = (riding && vindicator.getVehicle() instanceof LivingEntity)
                    ? (LivingEntity) vindicator.getVehicle()
                    : vindicator;

            // Active manual retreat pathing
            double retreatSpeedMultiplier = riding ? 1.55 : 1.35;
            moveTowardsTarget(activeMover, retreatLoc, retreatSpeedMultiplier);
        }
    }

    private void performSpearAttack(Vindicator vindicator, LivingEntity target) {
        vindicator.swingMainHand();

        double baseDamage = 6.0;
        if (vindicator.getAttribute(Attribute.ATTACK_DAMAGE) != null) {
            baseDamage = vindicator.getAttribute(Attribute.ATTACK_DAMAGE).getValue();
        }

        boolean riding = vindicator.isInsideVehicle();
        double finalDamage = baseDamage;

        // Apply scaling (1.5x on foot, 2.0x on horse due to momentum)
        if (riding) {
            finalDamage *= 2.0;
            playSpearSound(vindicator, "ITEM_SPEAR_LUNGE_1", 1.2F, 0.9F);
            target.getWorld().spawnParticle(org.bukkit.Particle.CRIT, target.getLocation().add(0, 1, 0), 8, 0.3, 0.3, 0.3, 0.1);
        } else {
            finalDamage *= 1.5;
            playSpearSound(vindicator, "ITEM_SPEAR_LUNGE_1", 1.0F, 1.05F);
            target.getWorld().spawnParticle(org.bukkit.Particle.CRIT, target.getLocation().add(0, 1, 0), 4, 0.2, 0.2, 0.2, 0.05);
        }

        // Apply primary target damage
        target.damage(finalDamage, vindicator);

        // Spear Piercing/AOE Damage: Hits up to 3 additional secondary targets in a line
        Location startLoc = vindicator.getLocation();
        Vector direction = startLoc.getDirection().normalize();
        double pierceRange = riding ? 6.0 : 5.2;

        int piercedCount = 0;
        for (org.bukkit.entity.Entity entity : target.getNearbyEntities(pierceRange, 3.0, pierceRange)) {
            if (piercedCount >= 3) break;
            if (entity == target || entity == vindicator || entity == vindicator.getVehicle()) continue;

            if (entity instanceof LivingEntity) {
                LivingEntity secondaryTarget = (LivingEntity) entity;

                // Ensure we only target typical raid hostile targets
                if (secondaryTarget instanceof Player || secondaryTarget instanceof org.bukkit.entity.IronGolem || secondaryTarget instanceof org.bukkit.entity.Villager) {
                    // Vector projection check along charge line
                    Vector toTarget = secondaryTarget.getLocation().toVector().subtract(startLoc.toVector());
                    double projection = toTarget.dot(direction);

                    if (projection > 0 && projection <= pierceRange) {
                        Vector closestPoint = startLoc.toVector().add(direction.clone().multiply(projection));
                        double distSq = secondaryTarget.getLocation().toVector().distanceSquared(closestPoint);

                        if (distSq <= 2.25) { // Within 1.5 blocks of the linear attack path
                            secondaryTarget.damage(finalDamage * 0.75, vindicator); // Pierced targets take 75% damage
                            secondaryTarget.getWorld().spawnParticle(org.bukkit.Particle.SWEEP_ATTACK, secondaryTarget.getLocation().add(0, 1, 0), 1);
                            piercedCount++;
                        }
                    }
                }
            }
        }
    }

    private void moveTowardsTarget(LivingEntity entity, Location targetLoc, double speed) {
        try {
            initReflection(entity, null);
            if (getPathfinderMethod != null) {
                Object pathfinder = getPathfinderMethod.invoke(entity);
                if (pathfinder != null) {
                    if (moveToPathfinderMethod == null) {
                        moveToPathfinderMethod = pathfinder.getClass().getMethod("moveTo", org.bukkit.Location.class, double.class);
                    }
                    moveToPathfinderMethod.invoke(pathfinder, targetLoc, speed);
                    return;
                }
            }
        } catch (Exception ignored) {}

        try {
            // NMS Navigation Fallback
            if (getHandleMethod != null) {
                Object nmsEntity = getHandleMethod.invoke(entity);
                if (getNavigationMethod == null) {
                    getNavigationMethod = nmsEntity.getClass().getMethod("getNavigation");
                }
                Object navigation = getNavigationMethod.invoke(nmsEntity);
                if (navigation != null) {
                    if (moveToNavigationMethod == null) {
                        moveToNavigationMethod = navigation.getClass().getMethod("moveTo", double.class, double.class, double.class, double.class);
                    }
                    moveToNavigationMethod.invoke(navigation, targetLoc.getX(), targetLoc.getY(), targetLoc.getZ(), speed);
                }
            }
        } catch (Exception ignored) {}
    }

    private void startUsingSpear(Vindicator vindicator) {
        try {
            initReflection(vindicator, null);
            if (getHandleMethod == null) return;
            Object nmsVindicator = getHandleMethod.invoke(vindicator);
            initReflection(vindicator, nmsVindicator);

            if (isUsingItemMethod != null && startUsingItemMethod != null) {
                boolean isUsing = (boolean) isUsingItemMethod.invoke(nmsVindicator);
                if (!isUsing) {
                    startUsingItemMethod.invoke(nmsVindicator, mainHandEnum);

                    // Play spear charge sound
                    playSpearSound(vindicator, "ITEM_SPEAR_USE", 1.0F, 1.0F);

                    // Smoothly offset 0.2x vanilla use penalty on foot (1.75 modifier is natural)
                    if (!vindicator.isInsideVehicle()) {
                        AttributeInstance speedAttribute = vindicator.getAttribute(Attribute.MOVEMENT_SPEED);
                        if (speedAttribute != null) {
                            speedAttribute.removeModifier(speedKey); // Clear existing
                            AttributeModifier modifier = new AttributeModifier(
                                    speedKey, 1.75, AttributeModifier.Operation.ADD_NUMBER
                            );
                            speedAttribute.addModifier(modifier);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private void stopUsingSpear(Vindicator vindicator) {
        try {
            initReflection(vindicator, null);
            if (getHandleMethod == null) return;
            Object nmsVindicator = getHandleMethod.invoke(vindicator);
            initReflection(vindicator, nmsVindicator);

            if (isUsingItemMethod != null && stopUsingItemMethod != null) {
                boolean isUsing = (boolean) isUsingItemMethod.invoke(nmsVindicator);
                if (isUsing) {
                    stopUsingItemMethod.invoke(nmsVindicator);
                }
            }
        } catch (Exception ignored) {
        } finally {
            // Always clean up speed modifier within finally block to avoid leaks
            AttributeInstance speedAttribute = vindicator.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speedAttribute != null) {
                speedAttribute.removeModifier(speedKey);
            }
        }
    }

    private static void initReflection(Object entity, Object nmsVindicator) {
        try {
            if (getPathfinderMethod == null) {
                try {
                    getPathfinderMethod = entity.getClass().getMethod("getPathfinder");
                } catch (NoSuchMethodException ignored) {}
            }
            if (getHandleMethod == null) {
                getHandleMethod = entity.getClass().getMethod("getHandle");
            }
            if (nmsVindicator != null) {
                Class<?> nmsClass = nmsVindicator.getClass();
                if (isUsingItemMethod == null) {
                    isUsingItemMethod = nmsClass.getMethod("isUsingItem");
                }
                if (stopUsingItemMethod == null) {
                    stopUsingItemMethod = nmsClass.getMethod("stopUsingItem");
                }
                if (startUsingItemMethod == null) {
                    Class<?> handClass = Class.forName("net.minecraft.world.InteractionHand");
                    mainHandEnum = handClass.getField("MAIN_HAND").get(null);
                    for (Method m : nmsClass.getMethods()) {
                        if (m.getName().equals("startUsingItem") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == handClass) {
                            startUsingItemMethod = m;
                            break;
                        }
                    }
                }
                if (goalSelectorField == null) {
                    Class<?> currentClass = nmsClass;
                    while (currentClass != null && goalSelectorField == null) {
                        try {
                            goalSelectorField = currentClass.getDeclaredField("goalSelector");
                        } catch (NoSuchFieldException e) {
                            currentClass = currentClass.getSuperclass();
                        }
                    }
                    if (goalSelectorField != null) {
                        goalSelectorField.setAccessible(true);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private void playSpearSound(org.bukkit.entity.Entity entity, String soundName, float volume, float pitch) {
        try {
            org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.minecraft(soundName.toLowerCase().replace('_', '.'));
            org.bukkit.Sound sound = org.bukkit.Registry.SOUNDS.get(key);
            if (sound != null) {
                entity.getWorld().playSound(entity.getLocation(), sound, volume, pitch);
            } else {
                entity.getWorld().playSound(entity.getLocation(), "minecraft:" + key.getKey(), volume, pitch);
            }
        } catch (Exception e) {
            try {
                String minecraftSoundName = "minecraft:" + soundName.toLowerCase().replace('_', '.');
                entity.getWorld().playSound(entity.getLocation(), minecraftSoundName, volume, pitch);
            } catch (Exception ignored) {}
        }
    }

    private boolean isAdjudicator(Vindicator vindicator) {
        return vindicator.getPersistentDataContainer().has(adjudicatorKey, PersistentDataType.BYTE);
    }

    private boolean isAdjudicatorHorse(org.bukkit.entity.Entity entity) {
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

        // Match standard Vindicator tracking distance
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
            equipment.setItemInMainHand(new ItemStack(Material.IRON_SPEAR));
            equipment.setItemInOffHand(getAdjudicatorShield());
            equipment.setHelmet(getAdjudicatorBanner());
            equipment.setHelmetDropChance(1.0f);
        }

        PersistentDataContainer pdc = vindicator.getPersistentDataContainer();
        pdc.set(adjudicatorKey, PersistentDataType.BYTE, (byte) 0);
        vindicator.setCustomName("§6Adjudicator");

        injectSpearUseGoal(vindicator);

        return new Adjudicator(vindicator);
    }

    public AdjudicatorRider spawnRider(Location location) {
        Horse horse = (Horse) location.getWorld().spawnEntity(location, EntityType.HORSE);
        horse.getAttribute(Attribute.MAX_HEALTH).setBaseValue(horseHealth);
        horse.setHealth(horseHealth);

        AttributeInstance speedAttribute = horse.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttribute != null) {
            speedAttribute.setBaseValue(0.42); // Slightly increased from 0.36
        }

        horse.getInventory().setSaddle(new ItemStack(Material.SADDLE));
        horse.getInventory().setArmor(new ItemStack(Material.IRON_HORSE_ARMOR));

        horse.setTamed(true);
        horse.setCustomName("§6Adjudicator's Steed");
        horse.setRemoveWhenFarAway(false);

        // Tag the horse to identify it safely for friendly fire checks
        horse.getPersistentDataContainer().set(adjudicatorKey, PersistentDataType.BYTE, (byte) 0);

        Vindicator vindicator = (Vindicator) location.getWorld().spawnEntity(location, EntityType.VINDICATOR);

        // Match standard Vindicator tracking distance
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
            equipment.setItemInMainHand(new ItemStack(Material.IRON_SPEAR));
            equipment.setItemInOffHand(getAdjudicatorShield());
            equipment.setHelmet(getAdjudicatorBanner());
            equipment.setHelmetDropChance(1.0f);
        }

        PersistentDataContainer pdc = vindicator.getPersistentDataContainer();
        pdc.set(adjudicatorKey, PersistentDataType.BYTE, (byte) 0);
        pdc.set(horseKey, PersistentDataType.STRING, horse.getUniqueId().toString());
        vindicator.setCustomName("§6Adjudicator");

        horse.addPassenger(vindicator);

        injectSpearUseGoal(vindicator);

        return new AdjudicatorRider(vindicator, horse);
    }

    private void injectSpearUseGoal(Vindicator vindicator) {
        try {
            initReflection(vindicator, null);
            if (getHandleMethod == null) return;
            Object nmsVindicator = getHandleMethod.invoke(vindicator);
            initReflection(vindicator, nmsVindicator);

            if (goalSelectorField != null) {
                Object goalSelector = goalSelectorField.get(nmsVindicator);

                removeMeleeAttackGoals(goalSelector);

                Object spearGoal = createSpearUseGoal(nmsVindicator);
                if (spearGoal != null) {
                    Method addGoalMethod = null;
                    for (Method m : goalSelector.getClass().getMethods()) {
                        if (m.getName().equals("addGoal") && m.getParameterCount() == 2) {
                            addGoalMethod = m;
                            break;
                        }
                    }
                    if (addGoalMethod != null) {
                        addGoalMethod.invoke(goalSelector, 2, spearGoal);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to inject SpearUseGoal into Adjudicator: " + e.getMessage());
        }
    }

    private void removeMeleeAttackGoals(Object goalSelector) {
        try {
            if (availableGoalsField == null) {
                Class<?> selectorClass = goalSelector.getClass();
                while (selectorClass != null && availableGoalsField == null) {
                    try {
                        availableGoalsField = selectorClass.getDeclaredField("availableGoals");
                    } catch (NoSuchFieldException e) {
                        selectorClass = selectorClass.getSuperclass();
                    }
                }
                if (availableGoalsField != null) {
                    availableGoalsField.setAccessible(true);
                }
            }

            if (availableGoalsField != null) {
                Set<?> availableGoals = (Set<?>) availableGoalsField.get(goalSelector);

                List<Object> toRemove = new ArrayList<>();
                for (Object prioritizedGoal : availableGoals) {
                    if (getGoalMethod == null) {
                        getGoalMethod = prioritizedGoal.getClass().getMethod("getGoal");
                    }
                    Object underlyingGoal = getGoalMethod.invoke(prioritizedGoal);
                    String className = underlyingGoal.getClass().getName();

                    if (className.contains("MeleeAttack") || className.contains("JohnnyAttack")) {
                        toRemove.add(underlyingGoal);
                    }
                }

                if (removeGoalMethod == null) {
                    removeGoalMethod = goalSelector.getClass().getMethod("removeGoal", Class.forName("net.minecraft.world.entity.ai.goal.Goal"));
                }
                for (Object goal : toRemove) {
                    removeGoalMethod.invoke(goalSelector, goal);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to remove native MeleeAttackGoals: " + e.getMessage());
        }
    }

    private Object createSpearUseGoal(Object nmsVindicator) {
        try {
            Class<?> goalClass = Class.forName("net.minecraft.world.entity.ai.goal.SpearUseGoal");
            java.lang.reflect.Constructor<?>[] ctors = goalClass.getConstructors();
            for (java.lang.reflect.Constructor<?> ctor : ctors) {
                Class<?>[] params = ctor.getParameterTypes();
                if (params.length == 0) continue;

                if (params[0].isAssignableFrom(nmsVindicator.getClass())) {
                    if (params.length == 1) {
                        return ctor.newInstance(nmsVindicator);
                    } else if (params.length == 2 && params[1] == double.class) {
                        return ctor.newInstance(nmsVindicator, 1.25);
                    } else if (params.length == 4 && params[1] == double.class && params[2] == int.class && (params[3] == float.class || params[3] == double.class)) {
                        // Increase search range radius parameter to 32.0 to expand search/attack range bounds
                        Object radius = params[3] == float.class ? 32.0F : 32.0;
                        return ctor.newInstance(nmsVindicator, 1.25, 20, radius);
                    } else {
                        Object[] args = new Object[params.length];
                        args[0] = nmsVindicator;
                        for (int i = 1; i < params.length; i++) {
                            if (params[i] == double.class) {
                                args[i] = 1.25;
                            } else if (params[i] == float.class) {
                                args[i] = 32.0F; // Expanded search/attack range bounds
                            } else if (params[i] == int.class) {
                                args[i] = 20;
                            } else if (params[i] == boolean.class) {
                                args[i] = false;
                            } else {
                                args[i] = null;
                            }
                        }
                        return ctor.newInstance(args);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not dynamically load SpearUseGoal: " + e.getMessage());
        }
        return null;
    }

    @EventHandler
    private void onAdjudicatorDamage(EntityDamageEvent evt) {
        if (!(evt.getEntity() instanceof Vindicator))
            return;

        Vindicator vindicator = (Vindicator) evt.getEntity();
        PersistentDataContainer pdc = vindicator.getPersistentDataContainer();

        if (pdc.has(horseKey, PersistentDataType.STRING)) {
            String uuidStr = pdc.get(horseKey, PersistentDataType.STRING);
            if (uuidStr != null) {
                try {
                    UUID horseUuid = UUID.fromString(uuidStr);
                    org.bukkit.entity.Entity horse = getEntityByUUID(vindicator.getWorld(), horseUuid);
                    if (horse != null && !horse.isDead() && horse.isValid()) {
                        evt.setCancelled(true);
                    }
                } catch (IllegalArgumentException e) {
                    // Ignore malformed UUID exceptions
                }
            }
        }
    }

    @EventHandler
    private void onHorseDamage(EntityDamageByEntityEvent evt) {
        if (!(evt.getEntity() instanceof Horse)) {
            return;
        }

        Horse horse = (Horse) evt.getEntity();
        if (!isAdjudicatorHorse(horse)) {
            return;
        }

        org.bukkit.entity.Entity damager = evt.getDamager();

        // Resolve shooter if damage is dealt by a projectile (arrows, splash potions, etc.)
        if (damager instanceof Projectile) {
            ProjectileSource source = ((Projectile) damager).getShooter();
            if (source instanceof org.bukkit.entity.Entity) {
                damager = (org.bukkit.entity.Entity) source;
            }
        }

        // Resolve summoner if damage is dealt by Evoker Fangs
        if (damager instanceof EvokerFangs) {
            damager = ((EvokerFangs) damager).getOwner();
        }

        // Cancel damage if it originated from a Raider or a Witch
        if (damager instanceof Raider || damager instanceof Witch) {
            evt.setCancelled(true);
        }
    }

    private org.bukkit.entity.Entity getEntityByUUID(org.bukkit.World world, UUID uuid) {
        if (world != null) {
            org.bukkit.entity.Entity entity = world.getEntity(uuid);
            if (entity != null) {
                return entity;
            }
        }
        return Bukkit.getEntity(uuid);
    }

    private ItemStack getAdjudicatorBanner() {
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
            meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            meta.setDisplayName(ChatColor.GOLD.toString() + "Adjudicator Banner");
            banner.setItemMeta(meta);
        }
        return banner;
    }

    private ItemStack getAdjudicatorShield() {
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