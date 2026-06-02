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
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vindicator;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.ShieldMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class AdjudicatorManager implements CustomRaiderManager, Listener {
    private double health;
    private double horseHealth;
    private final java.util.WeakHashMap<Vindicator, Long> attackCooldowns = new java.util.WeakHashMap<>();

    public AdjudicatorManager() {
        LargeRaids plugin = JavaPlugin.getPlugin(LargeRaids.class);
        startTickTask(plugin);
    }

    private void startTickTask(JavaPlugin plugin) {
        // Runs a lightweight proximity check task every 2 ticks (10 times a second)
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (org.bukkit.World world : Bukkit.getWorlds()) {
                for (Vindicator vindicator : world.getEntitiesByClass(Vindicator.class)) {
                    if (vindicator.isDead()) continue;
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
            return;
        }

        // Skip creative/spectator players
        if (target instanceof Player) {
            Player p = (Player) target;
            if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) {
                stopUsingSpear(vindicator);
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
        double maxReach = vindicator.isInsideVehicle() ? 6.0 : 5.2;
        double maxReachSq = maxReach * maxReach;

        if (distanceSq > maxReachSq && distanceSq <= 144.0) {
            // Further than combat reach but within distance: hold & point spear forward (charge stance)
            startUsingSpear(vindicator);
        } else if (distanceSq <= maxReachSq) {
            // Close enough: stop holding/using the spear to execute the manual sweep hit
            stopUsingSpear(vindicator);

            long now = System.currentTimeMillis();
            long lastAttack = attackCooldowns.getOrDefault(vindicator, 0L);
            if ((now - lastAttack) >= 1200) { // Cooldown of 1.2 seconds
                performSpearAttack(vindicator, target);
                attackCooldowns.put(vindicator, now);
            }
        } else {
            stopUsingSpear(vindicator);
        }
    }

    private void performSpearAttack(Vindicator vindicator, LivingEntity target) {
        // Swing hand with spear
        vindicator.swingMainHand();
        vindicator.getWorld().spawnParticle(org.bukkit.Particle.SWEEP_ATTACK, target.getLocation().add(0, 1, 0), 1);

        double damage = 6.0;
        if (vindicator.getAttribute(Attribute.ATTACK_DAMAGE) != null) {
            damage = vindicator.getAttribute(Attribute.ATTACK_DAMAGE).getValue();
        }

        // Deal 1.5x damage on horseback to simulate jousting momentum
        if (vindicator.isInsideVehicle()) {
            damage *= 1.5;
            vindicator.getWorld().playSound(vindicator.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0F, 1.0F);
            vindicator.getWorld().spawnParticle(org.bukkit.Particle.CRIT, target.getLocation().add(0, 1, 0), 5, 0.2, 0.2, 0.2, 0.1);
        }

        target.damage(damage, vindicator);
    }

    private void startUsingSpear(Vindicator vindicator) {
        try {
            java.lang.reflect.Method getHandleMethod = vindicator.getClass().getMethod("getHandle");
            Object nmsVindicator = getHandleMethod.invoke(vindicator);

            // Check if they are already using an active item to avoid packet flooding
            java.lang.reflect.Method isUsingItemMethod = nmsVindicator.getClass().getMethod("isUsingItem");
            boolean isUsing = (boolean) isUsingItemMethod.invoke(nmsVindicator);

            if (!isUsing) {
                Class<?> handClass = Class.forName("net.minecraft.world.InteractionHand");
                Object mainHand = handClass.getField("MAIN_HAND").get(null);

                java.lang.reflect.Method startUsingMethod = null;
                for (java.lang.reflect.Method m : nmsVindicator.getClass().getMethods()) {
                    if (m.getName().equals("startUsingItem") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == handClass) {
                        startUsingMethod = m;
                        break;
                    }
                }
                if (startUsingMethod != null) {
                    startUsingMethod.invoke(nmsVindicator, mainHand);
                }
            }
        } catch (Exception e) {
            // Ignore silently
        }
    }

    private void stopUsingSpear(Vindicator vindicator) {
        try {
            java.lang.reflect.Method getHandleMethod = vindicator.getClass().getMethod("getHandle");
            Object nmsVindicator = getHandleMethod.invoke(vindicator);

            java.lang.reflect.Method isUsingItemMethod = nmsVindicator.getClass().getMethod("isUsingItem");
            boolean isUsing = (boolean) isUsingItemMethod.invoke(nmsVindicator);

            if (isUsing) {
                java.lang.reflect.Method stopUsingMethod = nmsVindicator.getClass().getMethod("stopUsingItem");
                stopUsingMethod.invoke(nmsVindicator);
            }
        } catch (Exception e) {
            // Ignore silently
        }
    }

    private boolean isAdjudicator(Vindicator vindicator) {
        return vindicator.getPersistentDataContainer().has(getAdjudicatorNamespacedKey(), PersistentDataType.BYTE);
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
        vindicator.getAttribute(Attribute.MAX_HEALTH).setBaseValue(health);
        vindicator.setHealth(health);

        vindicator.getAttribute(Attribute.ARMOR).setBaseValue(8.0);

        EntityEquipment equipment = vindicator.getEquipment();
        if (equipment != null) {
            equipment.setItemInMainHand(new ItemStack(Material.IRON_SPEAR));
            equipment.setItemInOffHand(getAdjudicatorShield());
            equipment.setHelmet(getAdjudicatorBanner());
            equipment.setHelmetDropChance(1.0f);
        }

        PersistentDataContainer pdc = vindicator.getPersistentDataContainer();
        pdc.set(getAdjudicatorNamespacedKey(), PersistentDataType.BYTE, (byte) 0);
        vindicator.setCustomName("§6Adjudicator");

        // Inject the spear charge attack behavior (SpearUseGoal)
        injectSpearUseGoal(vindicator);

        return new Adjudicator(vindicator);
    }

    public AdjudicatorRider spawnRider(Location location) {
        Horse horse = (Horse) location.getWorld().spawnEntity(location, EntityType.HORSE);
        horse.getAttribute(Attribute.MAX_HEALTH).setBaseValue(horseHealth);
        horse.setHealth(horseHealth);
        horse.getInventory().setSaddle(new ItemStack(Material.SADDLE));
        horse.setTamed(true);
        horse.setCustomName("§6Adjudicator's Steed");

        Vindicator vindicator = (Vindicator) location.getWorld().spawnEntity(location, EntityType.VINDICATOR);
        vindicator.getAttribute(Attribute.MAX_HEALTH).setBaseValue(health);
        vindicator.setHealth(health);

        vindicator.getAttribute(Attribute.ARMOR).setBaseValue(8.0);

        EntityEquipment equipment = vindicator.getEquipment();
        if (equipment != null) {
            equipment.setItemInMainHand(new ItemStack(Material.IRON_SPEAR));
            equipment.setItemInOffHand(getAdjudicatorShield());
            equipment.setHelmet(getAdjudicatorBanner());
            equipment.setHelmetDropChance(1.0f);
        }

        PersistentDataContainer pdc = vindicator.getPersistentDataContainer();
        pdc.set(getAdjudicatorNamespacedKey(), PersistentDataType.BYTE, (byte) 0);

        pdc.set(getAdjudicatorHorseKey(), PersistentDataType.STRING, horse.getUniqueId().toString());
        vindicator.setCustomName("§6Adjudicator Rider");

        horse.addPassenger(vindicator);

        // Inject the spear charge attack behavior (SpearUseGoal)
        injectSpearUseGoal(vindicator);

        return new AdjudicatorRider(vindicator, horse);
    }

    private void injectSpearUseGoal(Vindicator vindicator) {
        try {
            java.lang.reflect.Method getHandleMethod = vindicator.getClass().getMethod("getHandle");
            Object nmsVindicator = getHandleMethod.invoke(vindicator);

            java.lang.reflect.Field goalSelectorField = null;
            Class<?> currentClass = nmsVindicator.getClass();
            while (currentClass != null && goalSelectorField == null) {
                try {
                    goalSelectorField = currentClass.getDeclaredField("goalSelector");
                } catch (NoSuchFieldException e) {
                    currentClass = currentClass.getSuperclass();
                }
            }

            if (goalSelectorField != null) {
                goalSelectorField.setAccessible(true);
                Object goalSelector = goalSelectorField.get(nmsVindicator);

                removeMeleeAttackGoals(goalSelector);

                Object spearGoal = createSpearUseGoal(nmsVindicator);
                if (spearGoal != null) {
                    java.lang.reflect.Method addGoalMethod = null;
                    for (java.lang.reflect.Method m : goalSelector.getClass().getMethods()) {
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
            JavaPlugin.getPlugin(LargeRaids.class).getLogger().warning("Failed to inject SpearUseGoal into Adjudicator: " + e.getMessage());
        }
    }

    private void removeMeleeAttackGoals(Object goalSelector) {
        try {
            java.lang.reflect.Field availableGoalsField = null;
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
                java.util.Set<?> availableGoals = (java.util.Set<?>) availableGoalsField.get(goalSelector);

                java.util.List<Object> toRemove = new java.util.ArrayList<>();
                for (Object prioritizedGoal : availableGoals) {
                    java.lang.reflect.Method getGoalMethod = prioritizedGoal.getClass().getMethod("getGoal");
                    Object underlyingGoal = getGoalMethod.invoke(prioritizedGoal);
                    String className = underlyingGoal.getClass().getName();

                    if (className.contains("MeleeAttack") || className.contains("JohnnyAttack")) {
                        toRemove.add(underlyingGoal);
                    }
                }

                java.lang.reflect.Method removeGoalMethod = goalSelector.getClass().getMethod("removeGoal", Class.forName("net.minecraft.world.entity.ai.goal.Goal"));
                for (Object goal : toRemove) {
                    removeGoalMethod.invoke(goalSelector, goal);
                }
            }
        } catch (Exception e) {
            JavaPlugin.getPlugin(LargeRaids.class).getLogger().warning("Failed to remove native MeleeAttackGoals: " + e.getMessage());
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
                        Object radius = params[3] == float.class ? 15.0F : 15.0;
                        return ctor.newInstance(nmsVindicator, 1.25, 20, radius);
                    } else {
                        Object[] args = new Object[params.length];
                        args[0] = nmsVindicator;
                        for (int i = 1; i < params.length; i++) {
                            if (params[i] == double.class) {
                                args[i] = 1.25;
                            } else if (params[i] == float.class) {
                                args[i] = 15.0F;
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
            JavaPlugin.getPlugin(LargeRaids.class).getLogger().warning("Could not dynamically load SpearUseGoal: " + e.getMessage());
        }
        return null;
    }

    @EventHandler
    private void onAdjudicatorDamage(EntityDamageEvent evt) {
        if (!(evt.getEntity() instanceof Vindicator))
            return;

        Vindicator vindicator = (Vindicator) evt.getEntity();
        PersistentDataContainer pdc = vindicator.getPersistentDataContainer();
        NamespacedKey horseKey = getAdjudicatorHorseKey();

        if (pdc.has(horseKey, PersistentDataType.STRING)) {
            String uuidStr = pdc.get(horseKey, PersistentDataType.STRING);
            if (uuidStr != null) {
                try {
                    java.util.UUID horseUuid = java.util.UUID.fromString(uuidStr);
                    org.bukkit.entity.Entity horse = org.bukkit.Bukkit.getEntity(horseUuid);
                    if (horse != null && !horse.isDead()) {
                        evt.setCancelled(true);
                    }
                } catch (IllegalArgumentException e) {
                    // Ignore malformed UUID exceptions
                }
            }
        }
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

    private NamespacedKey getAdjudicatorNamespacedKey() {
        return new NamespacedKey(JavaPlugin.getPlugin(LargeRaids.class), "adjudicator");
    }

    private NamespacedKey getAdjudicatorHorseKey() {
        return new NamespacedKey(JavaPlugin.getPlugin(LargeRaids.class), "adjudicator_horse_uuid");
    }
}