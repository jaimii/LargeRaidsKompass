package com.solarrabbit.largeraids.raid.mob.manager;

import com.solarrabbit.largeraids.LargeRaids;
import com.solarrabbit.largeraids.config.custommobs.CustomMobsConfig;
import com.solarrabbit.largeraids.nms.AbstractRaidWrapper;
import com.solarrabbit.largeraids.raid.LargeRaid;
import com.solarrabbit.largeraids.raid.mob.Adjudicator;
import com.solarrabbit.largeraids.raid.mob.ArchIllager;
import com.solarrabbit.largeraids.util.BossBarCreator;
import com.solarrabbit.largeraids.util.ChatColorUtil;
import com.solarrabbit.largeraids.util.VersionUtil;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.util.TriState;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;

import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BossBar;
import org.bukkit.craftbukkit.entity.CraftRaider;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Evoker;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.Illusioner;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Pillager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Raider;
import org.bukkit.entity.Ravager;
import org.bukkit.entity.Spellcaster;
import org.bukkit.entity.Vex;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Vindicator;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class ArchIllagerManager implements BossRaiderManager, Listener {
    private static final double PHASE_MAX_HEALTH = 500.0;
    private static final double BASE_MOVEMENT_SPEED = 0.32;
    private static final double PHASE_2_FANG_DAMAGE = 4.0;   // 2 hearts (ignores armor)
    private static final double PHASE_3_4_FANG_DAMAGE = 8.0; // 4 hearts (ignores armor)
    private static final double PHASE_3_EXPLOSION_DAMAGE = 5.0;  // First transition armor-ignoring damage
    private static final double PHASE_4_EXPLOSION_DAMAGE = 10.0; // Last transition armor-ignoring damage
    private static final double EXPLOSION_RADIUS = 10.0;
    private static final int FANG_FIRE_TICKS = 100;
    private static final int DEATH_XP_POINTS = getXpForLevel(100);
    private static final Component ARCH_ILLAGER_NAME = ChatColorUtil.parse("<blue><bold>Arch-Illager");
    private static final Component JUGGERNAUT_NAME = ChatColorUtil.parse("<blue>Juggernaut <dark_blue>Ravager");
    private static volatile Field spellCastingTickCountField;

    private final LargeRaids plugin;
    private final NamespacedKey archKey;
    private final NamespacedKey cloneKey;
    private final NamespacedKey cloneOwnerKey;
    private final NamespacedKey phaseKey;
    private final NamespacedKey next50Key;
    private final NamespacedKey next100Key;
    private final NamespacedKey transitioningKey;
    private final NamespacedKey stunUntilKey;
    private final NamespacedKey ravagerUuidKey;
    private final NamespacedKey archFangsKey;
    private final NamespacedKey juggernautKey;
    private final NamespacedKey juggernautThresholdKey;
    private final NamespacedKey memberKey;
    private final ItemStack bannerTemplate;
    private final ItemStack flameBowTemplate;
    private final ItemStack axeTemplate;

    private double ravagerHealth = 300.0;
    private double ravagerDamage = 14.0;
    private int ravagerRegenLevel = 1;

    private final Set<Illusioner> activeArchIllagers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> processingFangDamage = ConcurrentHashMap.newKeySet();
    private final WeakHashMap<Illusioner, Ravager> linkedRavagers = new WeakHashMap<>();
    private final WeakHashMap<Illusioner, Illusioner> cloneOwners = new WeakHashMap<>();
    private final Map<Illusioner, List<Illusioner>> activeClones = new WeakHashMap<>();
    private final Map<Illusioner, Long> lastMeleeAttack = new WeakHashMap<>();
    private final Map<Illusioner, Long> lastSpecialCast = new WeakHashMap<>();
    private final Map<Illusioner, Long> lastCloneLineCast = new WeakHashMap<>();
    private final Map<Illusioner, Boolean> castingSonicBlast = new WeakHashMap<>();

    public ArchIllagerManager() {
        this.plugin = JavaPlugin.getPlugin(LargeRaids.class);
        this.archKey = new NamespacedKey(plugin, "arch_illager");
        this.cloneKey = new NamespacedKey(plugin, "arch_illager_clone");
        this.cloneOwnerKey = new NamespacedKey(plugin, "arch_illager_clone_owner");
        this.phaseKey = new NamespacedKey(plugin, "arch_illager_phase");
        this.next50Key = new NamespacedKey(plugin, "arch_illager_next_50");
        this.next100Key = new NamespacedKey(plugin, "arch_illager_next_100");
        this.transitioningKey = new NamespacedKey(plugin, "arch_illager_transitioning");
        this.stunUntilKey = new NamespacedKey(plugin, "arch_illager_stun_until");
        this.ravagerUuidKey = new NamespacedKey(plugin, "arch_illager_ravager_uuid");
        this.archFangsKey = new NamespacedKey(plugin, "arch_illager_fangs");
        this.juggernautKey = new NamespacedKey(plugin, "juggernaut");
        this.juggernautThresholdKey = new NamespacedKey(plugin, "juggernaut_next_trigger");
        this.memberKey = new NamespacedKey(plugin, LargeRaid.RAID_MEMBER_KEY);
        this.bannerTemplate = createArchIllagerBanner();

        this.flameBowTemplate = new ItemStack(Material.BOW);
        this.flameBowTemplate.addUnsafeEnchantment(Enchantment.FLAME, 1);
        this.flameBowTemplate.addUnsafeEnchantment(Enchantment.POWER, 4);

        this.axeTemplate = new ItemStack(Material.NETHERITE_AXE);
        this.axeTemplate.addUnsafeEnchantment(Enchantment.SHARPNESS, 3);

        startTickTask();
    }

    @Override
    public void loadSettings(CustomMobsConfig config) {
        if (config != null && config.getKingRaiderConfig() != null) {
            ravagerHealth = Math.max(100.0, config.getKingRaiderConfig().getRavagerHealth());
            ravagerDamage = Math.max(10.0, config.getKingRaiderConfig().getRavagerDamage());
            ravagerRegenLevel = config.getKingRaiderConfig().getRegenLevel();
        }
    }

    @Override
    public ArchIllager spawn(Location location) {
        World world = location.getWorld();
        Ravager ravager = (Ravager) world.spawnEntity(location, EntityType.RAVAGER);
        ravager.customName(JUGGERNAUT_NAME);
        ravager.getAttribute(Attribute.MAX_HEALTH).setBaseValue(ravagerHealth);
        ravager.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(ravagerDamage);
        ravager.setHealth(ravagerHealth);
        ravager.setRemoveWhenFarAway(false);

        PersistentDataContainer ravagerPdc = ravager.getPersistentDataContainer();
        ravagerPdc.set(juggernautKey, PersistentDataType.BYTE, (byte) 0);
        ravagerPdc.set(juggernautThresholdKey, PersistentDataType.DOUBLE, ravagerHealth - 100.0);

        if (ravagerRegenLevel >= 0) {
            ravager.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, ravagerRegenLevel));
        }

        Illusioner arch = (Illusioner) world.spawnEntity(location, EntityType.ILLUSIONER);
        arch.customName(ARCH_ILLAGER_NAME);
        arch.setCustomNameVisible(true);
        arch.setRemoveWhenFarAway(false);

        AttributeInstance maxHealthAttr = arch.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(PHASE_MAX_HEALTH);
        }
        arch.setHealth(PHASE_MAX_HEALTH);

        AttributeInstance speedAttr = arch.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.setBaseValue(BASE_MOVEMENT_SPEED);
        }

        AttributeInstance followRange = arch.getAttribute(Attribute.FOLLOW_RANGE);
        if (followRange != null) {
            followRange.setBaseValue(64.0);
        }

        EntityEquipment equip = arch.getEquipment();
        if (equip != null) {
            equip.setItemInMainHand(flameBowTemplate.clone());
            equip.setHelmet(bannerTemplate.clone());
            equip.setHelmetDropChance(1.0f);
        }

        arch.setPatrolLeader(true);

        PersistentDataContainer pdc = arch.getPersistentDataContainer();
        pdc.set(archKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(phaseKey, PersistentDataType.INTEGER, 1);
        pdc.set(next50Key, PersistentDataType.DOUBLE, 450.0);
        pdc.set(next100Key, PersistentDataType.DOUBLE, 400.0);
        pdc.set(transitioningKey, PersistentDataType.BYTE, (byte) 0);
        pdc.set(ravagerUuidKey, PersistentDataType.STRING, ravager.getUniqueId().toString());

        removeVanillaIllusionerSpells(arch, false);

        BossBar bossBar = BossBarCreator.createRaidBossBar(arch, BarColor.BLUE);
        BossBarCreator.createRaidBossBar(ravager, BarColor.BLUE);

        linkedRavagers.put(arch, ravager);
        ravager.addPassenger(arch);
        activeArchIllagers.add(arch);

        return new ArchIllager(arch, ravager, bossBar);
    }

    public void restoreArchIllager(Illusioner arch) {
        if (arch == null || arch.isDead() || !arch.isValid() || isClone(arch)) return;
        activeArchIllagers.remove(arch);
        activeArchIllagers.add(arch);
        arch.setGravity(true);
        arch.setAI(true);
        arch.setSpell(Spellcaster.Spell.NONE);
        AttributeInstance speedAttr = arch.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.setBaseValue(BASE_MOVEMENT_SPEED);
        }
        arch.getPersistentDataContainer().set(transitioningKey, PersistentDataType.BYTE, (byte) 0);
        BossBarCreator.ensureRaidBossBar(arch, BarColor.BLUE);
        int phase = getPhase(arch);
        removeVanillaIllusionerSpells(arch, phase >= 2);
    }

    private void startTickTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activeArchIllagers.isEmpty())
                return;

            Iterator<Illusioner> itr = activeArchIllagers.iterator();
            while (itr.hasNext()) {
                Illusioner arch = itr.next();
                if (arch == null || arch.isDead() || !arch.isValid()) {
                    if (arch != null && arch.isDead()) {
                        clearClones(arch, false);
                    }
                    itr.remove();
                    continue;
                }
                tickArchIllager(arch);
            }
        }, 0L, 2L);

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (World world : Bukkit.getWorlds()) {
                for (Illusioner illusioner : world.getEntitiesByClass(Illusioner.class)) {
                    if (isArchIllager(illusioner)) {
                        restoreArchIllager(illusioner);
                    }
                }
            }
        });
    }

    private void tickArchIllager(Illusioner arch) {
        if (isTransitioning(arch)) {
            return;
        }

        int phase = getPhase(arch);

        if (phase == 1) {
            Ravager ravager = getLinkedRavager(arch);
            if (ravager != null && !ravager.isDead() && ravager.isValid()) {
                if (!arch.isInsideVehicle()) {
                    ravager.addPassenger(arch);
                }
                return;
            } else {
                startPhase2(arch);
                phase = 2;
            }
        }

        long now = System.currentTimeMillis();
        if (now < getStunUntil(arch)) {
            arch.setSpell(Spellcaster.Spell.NONE);
            arch.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, arch.getEyeLocation().add(0, 0.4, 0), 2, 0.2, 0.2, 0.2, 0.0);
            arch.getWorld().spawnParticle(Particle.CRIT, arch.getLocation().add(0, 1.0, 0), 4, 0.3, 0.3, 0.3, 0.05);
            return;
        }

        if (phase == 4) {
            leaveSculkTrail(arch.getLocation(), 1);
        }

        LivingEntity target = findTarget(arch);
        if (target == null) return;
        arch.setTarget(target);

        List<Illusioner> clones = activeClones.get(arch);
        if (clones != null) {
            clones.removeIf(c -> c == null || c.isDead() || !c.isValid());
            if (clones.isEmpty()) {
                activeClones.remove(arch);
                clones = null;
            }
        }

        if (clones != null && !clones.isEmpty()) {
            arch.setGlowing(false);
            arch.removePotionEffect(PotionEffectType.GLOWING);
            for (Illusioner clone : clones) {
                clone.setGlowing(true);
                clone.setTarget(target);
            }

            long lastLine = lastCloneLineCast.getOrDefault(arch, 0L);
            if (now - lastLine >= 1800L) {
                lastCloneLineCast.put(arch, now);
                playSpellCastAnimation(arch, Spellcaster.Spell.FANGS, 25);
                castStraightLineFangs(arch, target.getLocation(), phase);
                for (Illusioner clone : clones) {
                    playSpellCastAnimation(clone, Spellcaster.Spell.FANGS, 25);
                    castStraightLineFangs(clone, target.getLocation(), phase);
                }
            }
            return;
        }

        if (Boolean.TRUE.equals(castingSonicBlast.get(arch))) {
            return;
        }

        long lastCast = lastSpecialCast.getOrDefault(arch, 0L);
        if (now - lastCast >= 4200L) {
            lastSpecialCast.put(arch, now);
            playSpellCastAnimation(arch, Spellcaster.Spell.FANGS, 32);
            arch.getWorld().playSound(arch.getLocation(), Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1.5f, 0.9f);

            boolean coin = ThreadLocalRandom.current().nextBoolean();
            if (phase == 2 || phase == 3) {
                if (coin) {
                    castSixStarFangs(arch, target, phase);
                } else {
                    castExpandingCircleFangs(arch, phase);
                }
            } else if (phase == 4) {
                if (coin) {
                    castCrisscrossFangs(arch, target, phase);
                } else {
                    castInnerOuterCircleFangs(arch, phase);
                }
            }
            return;
        }

        moveTowardsTarget(arch, target.getLocation(), phase == 4 ? 0.98 : 0.90);
        double distSq = arch.getLocation().distanceSquared(target.getLocation());
        if (distSq <= 9.0) {
            long lastHit = lastMeleeAttack.getOrDefault(arch, 0L);
            if (now - lastHit >= 1000L) {
                lastMeleeAttack.put(arch, now);
                arch.swingMainHand();
                target.damage(phase == 4 ? 12.0 : 9.0, arch);
            }
        }
    }

    // --- Spell-Casting Animation Helper ---

    private void playSpellCastAnimation(Illusioner illusioner, Spellcaster.Spell spell, int durationTicks) {
        if (illusioner == null || illusioner.isDead() || !illusioner.isValid()) return;
        illusioner.setSpell(spell);
        setNmsSpellCastingTicks(illusioner, durationTicks);
        illusioner.swingOffHand();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (illusioner.isValid() && !illusioner.isDead() && !isTransitioning(illusioner)) {
                illusioner.setSpell(Spellcaster.Spell.NONE);
            }
        }, durationTicks);
    }

    private void setNmsSpellCastingTicks(Illusioner illusioner, int ticks) {
        try {
            net.minecraft.world.entity.raid.Raider nms = ((CraftRaider) illusioner).getHandle();
            if (spellCastingTickCountField == null) {
                Class<?> c = nms.getClass();
                while (c != null && spellCastingTickCountField == null) {
                    try {
                        Field f = c.getDeclaredField("spellCastingTickCount");
                        f.setAccessible(true);
                        spellCastingTickCountField = f;
                    } catch (NoSuchFieldException e) {
                        c = c.getSuperclass();
                    }
                }
            }
            if (spellCastingTickCountField != null) {
                spellCastingTickCountField.setInt(nms, ticks);
            }
        } catch (Exception ignored) {}
    }

    private void startPhase2(Illusioner arch) {
        PersistentDataContainer pdc = arch.getPersistentDataContainer();
        pdc.set(phaseKey, PersistentDataType.INTEGER, 2);
        pdc.set(next50Key, PersistentDataType.DOUBLE, 450.0);
        pdc.set(next100Key, PersistentDataType.DOUBLE, 400.0);
        pdc.set(transitioningKey, PersistentDataType.BYTE, (byte) 0);

        AttributeInstance maxHealth = arch.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(PHASE_MAX_HEALTH);
        }
        arch.setHealth(PHASE_MAX_HEALTH);

        AttributeInstance speedAttr = arch.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.setBaseValue(BASE_MOVEMENT_SPEED);
        }

        EntityEquipment equip = arch.getEquipment();
        if (equip != null) {
            equip.setItemInMainHand(axeTemplate.clone());
        }

        removeVanillaIllusionerSpells(arch, true);
        playSpellCastAnimation(arch, Spellcaster.Spell.BLINDNESS, 35);
        arch.getWorld().playSound(arch.getLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 2.0f, 0.8f);
        arch.getWorld().spawnParticle(Particle.LARGE_SMOKE, arch.getLocation().add(0, 1, 0), 30, 0.5, 0.8, 0.5, 0.05);

        summonPhaseMinions(arch, 2);
    }

    // --- Wither-Style Floating Totem Transition + Spellcasting Pose + Circular Ground Slam ---

    private void triggerTotemTransition(Illusioner arch, int nextPhase) {
        PersistentDataContainer pdc = arch.getPersistentDataContainer();
        pdc.set(transitioningKey, PersistentDataType.BYTE, (byte) 1);
        clearClones(arch, false);
        arch.setGlowing(false);
        arch.setAI(false);
        arch.setGravity(false);

        final Spellcaster.Spell transitionSpell = (nextPhase == 4)
                ? Spellcaster.Spell.BLINDNESS
                : Spellcaster.Spell.SUMMON_VEX;
        arch.setSpell(transitionSpell);
        setNmsSpellCastingTicks(arch, 120);

        final Location baseLoc = arch.getLocation().clone();
        final World world = baseLoc.getWorld();

        if (world != null) {
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, baseLoc.clone().add(0, 1, 0), 60, 0.6, 0.8, 0.6, 0.25);
            world.playSound(baseLoc, Sound.ITEM_TOTEM_USE, 2.0f, 0.85f);
            world.playSound(baseLoc, Sound.ENTITY_WITHER_SPAWN, 1.5f, 1.2f);
            world.playSound(baseLoc, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 2.0f, 0.8f);
        }

        final int totalRegenSteps = 25;

        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                if (arch.isDead() || !arch.isValid() || world == null) {
                    arch.setGravity(true);
                    arch.setAI(true);
                    arch.setSpell(Spellcaster.Spell.NONE);
                    cancel();
                    return;
                }
                step++;

                if (step <= totalRegenSteps) {
                    arch.setSpell(transitionSpell);
                    setNmsSpellCastingTicks(arch, 20);

                    double hoverHeight = Math.min(3.8, (3.8 * step) / totalRegenSteps);
                    Location hoverLoc = baseLoc.clone().add(0, hoverHeight, 0);
                    hoverLoc.setYaw(baseLoc.getYaw() + (step * 18.0f));
                    arch.teleport(hoverLoc);

                    double newHealth = Math.min(PHASE_MAX_HEALTH, (PHASE_MAX_HEALTH * step) / totalRegenSteps);
                    arch.setHealth(Math.max(1.0, newHealth));

                    spawnTransitionCircleParticles(hoverLoc, baseLoc, step, nextPhase == 4);
                    return;
                }

                cancel();
                arch.setGravity(true);
                arch.setAI(true);

                int groundY = world.getHighestBlockYAt(baseLoc.getBlockX(), baseLoc.getBlockZ(), HeightMap.MOTION_BLOCKING_NO_LEAVES);
                Location slamLoc = new Location(world, baseLoc.getX(), groundY + 1.0, baseLoc.getZ(), arch.getLocation().getYaw(), 0f);
                arch.teleport(slamLoc);
                arch.setHealth(PHASE_MAX_HEALTH);

                pdc.set(phaseKey, PersistentDataType.INTEGER, nextPhase);
                pdc.set(next50Key, PersistentDataType.DOUBLE, 450.0);
                pdc.set(next100Key, PersistentDataType.DOUBLE, 400.0);
                pdc.set(transitioningKey, PersistentDataType.BYTE, (byte) 0);

                playSpellCastAnimation(arch, Spellcaster.Spell.FANGS, 20);

                if (nextPhase == 3) {
                    world.strikeLightningEffect(slamLoc);
                    corruptTerrainOnSlam(slamLoc, 4, false);
                    spawnCircularShockwave(slamLoc, Particle.TOTEM_OF_UNDYING, 6.0);
                    triggerRecoveryExplosion(arch, PHASE_3_EXPLOSION_DAMAGE);
                } else if (nextPhase == 4) {
                    world.strikeLightningEffect(slamLoc);
                    world.strikeLightningEffect(slamLoc.clone().add(3, 0, 0));
                    world.strikeLightningEffect(slamLoc.clone().add(-3, 0, 0));
                    world.strikeLightningEffect(slamLoc.clone().add(0, 0, 3));
                    world.strikeLightningEffect(slamLoc.clone().add(0, 0, -3));
                    world.playSound(slamLoc, Sound.ENTITY_WARDEN_SONIC_BOOM, 2.5f, 0.75f);
                    corruptTerrainOnSlam(slamLoc, 4, true);
                    spawnCircularShockwave(slamLoc, Particle.SCULK_SOUL, 7.5);
                    triggerRecoveryExplosion(arch, PHASE_4_EXPLOSION_DAMAGE);

                    summonFootAdjudicators(arch, 2);
                }
            }
        }.runTaskTimer(plugin, 4L, 4L);
    }

    private void summonFootAdjudicators(Illusioner arch, int count) {
        Location loc = arch.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        AdjudicatorManager adjManager = (AdjudicatorManager) plugin.getMobManagers().getMobManager(Adjudicator.class);
        if (adjManager == null) return;

        AbstractRaidWrapper nmsRaid = VersionUtil.getCraftRaiderWrapper(arch).getHandle().getCurrentRaid();
        LivingEntity target = findTarget(arch);

        for (int i = 0; i < count; i++) {
            double angle = Math.PI * i;
            Location spawnLoc = loc.clone().add(Math.cos(angle) * 2.5, 0, Math.sin(angle) * 2.5);
            Adjudicator adj = adjManager.spawn(spawnLoc);
            registerSummonedMinion(adj.getBukkitEntity(), nmsRaid, target);
        }
    }

    private void spawnTransitionCircleParticles(Location hoverLoc, Location groundLoc, int step, boolean sculkTheme) {
        World world = hoverLoc.getWorld();
        if (world == null) return;

        Particle primary = sculkTheme ? Particle.SCULK_SOUL : Particle.TOTEM_OF_UNDYING;
        Particle secondary = sculkTheme ? Particle.SCULK_CHARGE_POP : Particle.END_ROD;

        double angleOffset = step * 0.35;
        int points = 16;
        double upperRadius = 2.2;
        double lowerRadius = 4.0;

        for (int i = 0; i < points; i++) {
            double angle1 = angleOffset + (2 * Math.PI * i / points);
            Location ring1 = hoverLoc.clone().add(Math.cos(angle1) * upperRadius, 0.5, Math.sin(angle1) * upperRadius);
            world.spawnParticle(primary, ring1, 1, 0.0, 0.0, 0.0, 0.02);

            double angle2 = -angleOffset + (2 * Math.PI * i / points);
            Location ring2 = groundLoc.clone().add(Math.cos(angle2) * lowerRadius, 0.2, Math.sin(angle2) * lowerRadius);
            world.spawnParticle(secondary, ring2, 1, 0.0, 0.0, 0.0, 0.01);
        }
    }

    private void spawnCircularShockwave(Location center, Particle particle, double maxRadius) {
        World world = center.getWorld();
        if (world == null) return;
        for (double r = 1.5; r <= maxRadius; r += 1.5) {
            int points = (int) (r * 10);
            for (int i = 0; i < points; i++) {
                double angle = (2 * Math.PI * i) / points;
                Location pt = center.clone().add(Math.cos(angle) * r, 0.3, Math.sin(angle) * r);
                world.spawnParticle(particle, pt, 2, 0.05, 0.05, 0.05, 0.05);
            }
        }
    }

    private void corruptTerrainOnSlam(Location center, int radius, boolean turnToSculk) {
        World world = center.getWorld();
        if (world == null) return;

        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        int radiusSq = radius * radius;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radiusSq)
                    continue;

                int x = cx + dx;
                int z = cz + dz;

                for (int dy = -2; dy <= 6; dy++) {
                    Block b = world.getBlockAt(x, cy + dy, z);
                    Material type = b.getType();
                    if (type == Material.AIR) continue;
                    if (Tag.LEAVES.isTagged(type) || Tag.FLOWERS.isTagged(type)
                            || Tag.SAPLINGS.isTagged(type) || Tag.CROPS.isTagged(type)
                            || type == Material.SHORT_GRASS || type == Material.TALL_GRASS
                            || type == Material.FERN || type == Material.LARGE_FERN
                            || type == Material.DEAD_BUSH || type == Material.SNOW
                            || type == Material.VINE || type == Material.SWEET_BERRY_BUSH) {
                        b.breakNaturally();
                    }
                }

                int surfY = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                if (Math.abs(surfY - cy) <= 6) {
                    Block surface = world.getBlockAt(x, surfY, z);
                    Material surfType = surface.getType();
                    if (!surface.isLiquid() && surfType.isSolid()
                            && surfType != Material.BEDROCK && surfType != Material.OBSIDIAN
                            && !(surface.getState() instanceof org.bukkit.block.TileState)) {
                        if (turnToSculk) {
                            surface.setType(Material.SCULK, false);
                        } else if (surfType == Material.GRASS_BLOCK || surfType == Material.PODZOL
                                || surfType == Material.MYCELIUM || surfType == Material.DIRT_PATH
                                || surfType == Material.FARMLAND || surfType == Material.ROOTED_DIRT) {
                            surface.setType(Material.DIRT, false);
                        }
                    }
                }
            }
        }
    }

    private void triggerRecoveryExplosion(Illusioner arch, double armorBypassingDamage) {
        Location loc = arch.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        world.spawnParticle(Particle.EXPLOSION_EMITTER, loc.clone().add(0, 1, 0), 2, 0.0, 0.0, 0.0, 0.0);
        spawnCircularShockwave(loc, Particle.EXPLOSION, 5.0);
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2.5f, 0.8f);

        DamageSource magicSource = DamageSource.builder(DamageType.MAGIC)
                .withCausingEntity(arch)
                .withDirectEntity(arch)
                .build();

        double radiusSq = EXPLOSION_RADIUS * EXPLOSION_RADIUS;

        for (Entity entity : world.getNearbyEntities(loc, EXPLOSION_RADIUS, 8.0, EXPLOSION_RADIUS)) {
            if (entity instanceof LivingEntity living && isValidHostileTarget(living)) {
                double dx = living.getLocation().getX() - loc.getX();
                double dz = living.getLocation().getZ() - loc.getZ();
                if (dx * dx + dz * dz > radiusSq)
                    continue;

                // Deal 100 damage (ignoring armor) to Iron Golems, and standard transition damage to other targets
                double finalDamage = (living instanceof IronGolem) ? 100.0 : armorBypassingDamage;
                living.damage(finalDamage, magicSource);

                Vector kb = new Vector(dx, 0, dz);
                if (kb.lengthSquared() == 0) {
                    kb = new Vector(5, 2, 0);
                } else {
                    kb.normalize();
                }
                living.setVelocity(kb.multiply(1.15).setY(0.42));
            }
        }
    }

    // --- Bedrock Wither-Style Floating Death Animation ---

    private void triggerBedrockWitherDeath(Illusioner arch) {
        PersistentDataContainer pdc = arch.getPersistentDataContainer();
        pdc.set(transitioningKey, PersistentDataType.BYTE, (byte) 2);
        clearClones(arch, true);
        arch.setAI(false);
        arch.setGravity(false);
        arch.setGlowing(true);
        arch.setSpell(Spellcaster.Spell.SUMMON_VEX);
        setNmsSpellCastingTicks(arch, 100);

        final Location baseLoc = arch.getLocation().clone();
        final World world = baseLoc.getWorld();
        if (world != null) {
            world.playSound(baseLoc, Sound.ENTITY_WITHER_SPAWN, 2.0f, 0.6f);
            world.playSound(baseLoc, Sound.ENTITY_WARDEN_DEATH, 2.0f, 0.8f);
        }

        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                if (arch.isDead() || !arch.isValid() || world == null) {
                    arch.setGravity(true);
                    cancel();
                    return;
                }
                step++;

                if (step <= 20) {
                    arch.setSpell(Spellcaster.Spell.SUMMON_VEX);
                    setNmsSpellCastingTicks(arch, 20);

                    double hoverHeight = Math.min(3.5, step * 0.2);
                    Location hoverLoc = baseLoc.clone().add(0, hoverHeight, 0);
                    hoverLoc.setYaw(baseLoc.getYaw() + (step * 28.0f));
                    arch.teleport(hoverLoc);

                    double radius = 1.5 + (step % 5) * 0.8;
                    int points = 20;
                    for (int i = 0; i < points; i++) {
                        double angle = (step * 0.4) + (2 * Math.PI * i / points);
                        Location pt = hoverLoc.clone().add(Math.cos(angle) * radius, 0.5, Math.sin(angle) * radius);
                        world.spawnParticle(Particle.FIREWORK, pt, 1, 0.05, 0.05, 0.05, 0.02);
                        world.spawnParticle(Particle.END_ROD, pt, 1, 0.0, 0.0, 0.0, 0.01);
                    }
                    world.spawnParticle(Particle.SCULK_SOUL, hoverLoc.clone().add(0, 1, 0), 8, 0.5, 0.5, 0.5, 0.05);
                    if (step % 4 == 0) {
                        world.playSound(hoverLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 1.4f);
                    }
                    return;
                }

                cancel();
                arch.setGravity(true);
                arch.setSpell(Spellcaster.Spell.NONE);
                Location deathLoc = arch.getLocation();

                spawnCircularShockwave(deathLoc, Particle.FIREWORK, 8.0);
                spawnCircularShockwave(deathLoc, Particle.END_ROD, 6.0);
                world.spawnParticle(Particle.EXPLOSION_EMITTER, deathLoc.clone().add(0, 1, 0), 4, 0.6, 0.6, 0.6, 0.0);
                world.spawnParticle(Particle.SONIC_BOOM, deathLoc.clone().add(0, 1, 0), 5, 0.5, 0.5, 0.5, 0.0);
                world.spawnParticle(Particle.SCULK_SOUL, deathLoc.clone().add(0, 1, 0), 100, 1.5, 1.5, 1.5, 0.12);
                world.playSound(deathLoc, Sound.ENTITY_WITHER_DEATH, 2.5f, 0.8f);
                world.playSound(deathLoc, Sound.ENTITY_GENERIC_EXPLODE, 3.0f, 0.6f);

                corruptTerrainOnSlam(baseLoc, 4, true);
                arch.setHealth(0.0);
            }
        }.runTaskTimer(plugin, 4L, 4L);
    }

    // --- Illusion Split (3 in Phase 2, 5 in Phase 3, 9 in Phase 4 around the player) ---

    private void spawnIllusionSplit(Illusioner arch, int totalCount) {
        clearClones(arch, false);

        LivingEntity target = findTarget(arch);
        Location center = (target != null) ? target.getLocation() : arch.getLocation();
        World world = center.getWorld();
        if (world == null) return;

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double radius = (totalCount >= 9) ? 7.0 : 5.5;
        List<Location> positions = new ArrayList<>(totalCount);
        double startAngle = rng.nextDouble(2 * Math.PI);

        for (int i = 0; i < totalCount; i++) {
            double angle = startAngle + (2 * Math.PI * i / totalCount);
            double x = center.getX() + Math.cos(angle) * radius;
            double z = center.getZ() + Math.sin(angle) * radius;
            int y = world.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z), HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Location pos = new Location(world, x, y + 1.0, z);
            Vector dir = center.toVector().subtract(pos.toVector());
            if (dir.lengthSquared() > 0) {
                pos.setDirection(dir);
            }
            positions.add(pos);
        }

        int realIndex = rng.nextInt(totalCount);
        List<Illusioner> clones = new ArrayList<>(totalCount - 1);

        for (int i = 0; i < totalCount; i++) {
            Location pos = positions.get(i);
            if (i == realIndex) {
                arch.teleport(pos);
                arch.setGlowing(false);
                arch.removePotionEffect(PotionEffectType.GLOWING);
                playSpellCastAnimation(arch, Spellcaster.Spell.BLINDNESS, 30);
                world.spawnParticle(Particle.CLOUD, pos.clone().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.05);
            } else {
                Illusioner clone = (Illusioner) world.spawnEntity(pos, EntityType.ILLUSIONER);
                clone.customName(ARCH_ILLAGER_NAME);
                clone.setCustomNameVisible(true);
                clone.setGlowing(true);
                clone.setPatrolLeader(true);
                clone.setRemoveWhenFarAway(false);

                EntityEquipment equip = clone.getEquipment();
                if (equip != null) {
                    equip.setItemInMainHand(axeTemplate.clone());
                    equip.setHelmet(bannerTemplate.clone());
                    equip.setHelmetDropChance(0.0f);
                }

                removeVanillaIllusionerSpells(clone, true);
                playSpellCastAnimation(clone, Spellcaster.Spell.BLINDNESS, 30);
                PersistentDataContainer clonePdc = clone.getPersistentDataContainer();
                clonePdc.set(cloneKey, PersistentDataType.BYTE, (byte) 1);
                clonePdc.set(cloneOwnerKey, PersistentDataType.STRING, arch.getUniqueId().toString());
                cloneOwners.put(clone, arch);

                if (target != null) {
                    clone.setTarget(target);
                }

                clones.add(clone);
                world.spawnParticle(Particle.CLOUD, pos.clone().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.05);
            }
        }

        activeClones.put(arch, clones);
        world.playSound(center, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 2.0f, 1.0f);
        world.playSound(center, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 2.0f, 1.0f);
    }

    private void clearClones(Illusioner arch, boolean withSmoke) {
        List<Illusioner> clones = activeClones.remove(arch);
        if (clones != null) {
            for (Illusioner clone : clones) {
                cloneOwners.remove(clone);
                if (clone != null && !clone.isDead()) {
                    if (withSmoke) {
                        clone.getWorld().spawnParticle(Particle.LARGE_SMOKE, clone.getLocation().add(0, 1, 0), 25, 0.3, 0.5, 0.3, 0.05);
                    }
                    clone.remove();
                }
            }
        }
    }

    // --- Phase 2, 3 & 4 Minion Summons ---

    private void summonPhaseMinions(Illusioner arch, int phase) {
        Location loc = arch.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        playSpellCastAnimation(arch, Spellcaster.Spell.SUMMON_VEX, 35);
        world.playSound(loc, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 2.0f, 0.9f);
        AbstractRaidWrapper nmsRaid = VersionUtil.getCraftRaiderWrapper(arch).getHandle().getCurrentRaid();
        LivingEntity target = findTarget(arch);

        for (int i = 0; i < 3; i++) {
            double angleP = (2 * Math.PI * i) / 3 + (Math.PI / 3);
            Location pLoc = loc.clone().add(Math.cos(angleP) * 3.0, 0, Math.sin(angleP) * 3.0);
            Pillager pillager = (Pillager) world.spawnEntity(pLoc, EntityType.PILLAGER);
            registerSummonedMinion(pillager, nmsRaid, target);
        }

        if (phase >= 3) {
            for (int i = 0; i < 3; i++) {
                double angleV = (2 * Math.PI * i) / 3;
                Location vLoc = loc.clone().add(Math.cos(angleV) * 2.5, 0, Math.sin(angleV) * 2.5);
                Vindicator vindicator = (Vindicator) world.spawnEntity(vLoc, EntityType.VINDICATOR);
                registerSummonedMinion(vindicator, nmsRaid, target);
            }
        }

        if (phase >= 4) {
            for (int i = 0; i < 2; i++) {
                double angleE = Math.PI * i;
                Location eLoc = loc.clone().add(Math.cos(angleE) * 3.5, 0, Math.sin(angleE) * 3.5);
                Evoker evoker = (Evoker) world.spawnEntity(eLoc, EntityType.EVOKER);
                EvokerGoalUtil.injectRaidFleeGoal(evoker, loc);
                registerSummonedMinion(evoker, nmsRaid, target);
            }
        }
    }

    private void registerSummonedMinion(Raider minion, AbstractRaidWrapper nmsRaid, LivingEntity target) {
        minion.getPersistentDataContainer().set(memberKey, PersistentDataType.INTEGER, 1);
        if (nmsRaid != null && !nmsRaid.isEmpty()) {
            nmsRaid.joinRaid(2, VersionUtil.getCraftRaiderWrapper(minion).getHandle(), null, true);
        }
        if (target != null) {
            minion.setTarget(target);
        }
        minion.getWorld().spawnParticle(Particle.POOF, minion.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.05);
    }

    // --- Condensed & Lengthened Evoker Fang (+8 in Phase 3, +16 in Phase 4) & Sonic Blast Patterns ---

    private int getExtraFangCount(int phase) {
        if (phase == 3) return 8;
        if (phase >= 4) return 16;
        return 0;
    }

    private void spawnCustomFang(Illusioner owner, Location loc) {
        World world = loc.getWorld();
        if (world == null) return;
        int groundY = world.getHighestBlockYAt(loc.getBlockX(), loc.getBlockZ(), HeightMap.MOTION_BLOCKING_NO_LEAVES);
        double y = Math.abs(loc.getY() - (groundY + 1)) <= 6 ? groundY + 1.0 : loc.getY();
        Location fangLoc = new Location(world, loc.getX(), y, loc.getZ());

        int phase = getPhase(owner);
        double damage = (phase >= 3) ? PHASE_3_4_FANG_DAMAGE : PHASE_2_FANG_DAMAGE;

        EvokerFangs fangs = (EvokerFangs) world.spawnEntity(fangLoc, EntityType.EVOKER_FANGS);
        fangs.setOwner(owner);
        fangs.setVisualFire(TriState.TRUE);
        fangs.getPersistentDataContainer().set(archFangsKey, PersistentDataType.DOUBLE, damage);
    }

    private void castStraightLineFangs(Illusioner caster, Location targetLoc, int phase) {
        Location start = caster.getLocation();
        Vector dir = targetLoc.toVector().subtract(start.toVector()).setY(0);
        if (dir.lengthSquared() == 0) {
            dir = new Vector(1, 0, 0);
        } else {
            dir.normalize();
        }
        int totalFangs = 14 + getExtraFangCount(phase);
        for (int i = 1; i <= totalFangs; i++) {
            Location point = start.clone().add(dir.clone().multiply(i * 0.75));
            spawnCustomFang(caster, point);
        }
    }

    private void castSixStarFangs(Illusioner arch, LivingEntity target, int phase) {
        Location center = arch.getLocation();
        int stepsPerRay = 9 + getExtraFangCount(phase);
        for (int ray = 0; ray < 6; ray++) {
            double angle = Math.toRadians(ray * 60.0);
            Vector dir = new Vector(Math.cos(angle), 0, Math.sin(angle));
            for (int step = 1; step <= stepsPerRay; step++) {
                Location point = center.clone().add(dir.clone().multiply(step * 0.75));
                spawnCustomFang(arch, point);
            }
        }
        if (target != null) {
            Location tCenter = target.getLocation();
            for (int ray = 0; ray < 6; ray++) {
                double angle = Math.toRadians(ray * 60.0);
                Location point = tCenter.clone().add(Math.cos(angle) * 1.25, 0, Math.sin(angle) * 1.25);
                spawnCustomFang(arch, point);
            }
        }
    }

    private void castExpandingCircleFangs(Illusioner arch, int phase) {
        Location center = arch.getLocation().clone();
        int extra = getExtraFangCount(phase);
        double[] radii = { 1.5, 3.0, 4.5 + (extra * 0.15) };
        int[] counts = { 10 + (extra / 2), 16 + extra, 22 + extra };

        for (int wave = 0; wave < 3; wave++) {
            final double r = radii[wave];
            final int count = counts[wave];
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (arch.isDead() || !arch.isValid()) return;
                for (int i = 0; i < count; i++) {
                    double angle = (2 * Math.PI * i) / count;
                    Location point = center.clone().add(Math.cos(angle) * r, 0, Math.sin(angle) * r);
                    spawnCustomFang(arch, point);
                }
            }, wave * 8L);
        }
    }

    private void castCrisscrossFangs(Illusioner arch, LivingEntity target, int phase) {
        Location center = (target != null) ? target.getLocation().clone() : arch.getLocation().clone();
        double[] angles = { 0, Math.PI / 4, Math.PI / 2, (3 * Math.PI) / 4 };
        int halfLength = 8 + (getExtraFangCount(phase) / 2);
        for (double angle : angles) {
            Vector dir = new Vector(Math.cos(angle), 0, Math.sin(angle));
            for (int step = -halfLength; step <= halfLength; step++) {
                Location point = center.clone().add(dir.clone().multiply(step * 0.70));
                spawnCustomFang(arch, point);
            }
        }
    }

    private void castInnerOuterCircleFangs(Illusioner arch, int phase) {
        Location center = arch.getLocation().clone();
        int extra = getExtraFangCount(phase);
        int innerCount = 10 + (extra / 2);
        int outerCount = 24 + extra;
        int midCount = 16 + extra;

        for (int i = 0; i < innerCount; i++) {
            double angle = (2 * Math.PI * i) / innerCount;
            spawnCustomFang(arch, center.clone().add(Math.cos(angle) * 1.8, 0, Math.sin(angle) * 1.8));
        }
        for (int i = 0; i < outerCount; i++) {
            double angle = (2 * Math.PI * i) / outerCount;
            spawnCustomFang(arch, center.clone().add(Math.cos(angle) * 5.5, 0, Math.sin(angle) * 5.5));
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (arch.isDead() || !arch.isValid()) return;
            for (int i = 0; i < midCount; i++) {
                double angle = (2 * Math.PI * i) / midCount;
                spawnCustomFang(arch, center.clone().add(Math.cos(angle) * 3.6, 0, Math.sin(angle) * 3.6));
            }
        }, 10L);
    }

    private void castSonicBlast(Illusioner arch, LivingEntity target) {
        castingSonicBlast.put(arch, true);
        playSpellCastAnimation(arch, Spellcaster.Spell.BLINDNESS, 40);
        arch.getWorld().playSound(arch.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 2.0f, 1.0f);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (arch.isDead() || !arch.isValid()) {
                    castingSonicBlast.remove(arch);
                    cancel();
                    return;
                }
                ticks += 5;
                arch.getWorld().spawnParticle(Particle.SCULK_SOUL, arch.getEyeLocation(), 6, 0.3, 0.3, 0.3, 0.02);

                if (ticks >= 35) {
                    castingSonicBlast.remove(arch);
                    arch.setSpell(Spellcaster.Spell.NONE);
                    cancel();
                    if (target == null || target.isDead() || !target.isValid()) return;

                    Location start = arch.getEyeLocation();
                    Location end = target.getEyeLocation();
                    Vector dir = end.toVector().subtract(start.toVector());
                    if (dir.lengthSquared() == 0) {
                        dir = new Vector(1, 0, 0);
                    } else {
                        dir.normalize();
                    }

                    arch.getWorld().playSound(start, Sound.ENTITY_WARDEN_SONIC_BOOM, 2.5f, 1.0f);
                    DamageSource sonicSource = DamageSource.builder(DamageType.SONIC_BOOM)
                            .withCausingEntity(arch)
                            .withDirectEntity(arch)
                            .build();

                    Set<LivingEntity> hit = new HashSet<>();
                    for (double d = 0.0; d < 22.0; d += 1.5) {
                        Location point = start.clone().add(dir.clone().multiply(d));
                        point.getWorld().spawnParticle(Particle.SONIC_BOOM, point, 1, 0.0, 0.0, 0.0, 0.0);
                        for (Entity entity : point.getWorld().getNearbyEntities(point, 1.5, 1.5, 1.5)) {
                            if (entity instanceof LivingEntity living && isValidHostileTarget(living) && hit.add(living)) {
                                living.damage(10.0, sonicSource);
                                living.setVelocity(living.getVelocity().add(dir.clone().multiply(0.6).setY(0.3)));
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private void leaveSculkTrail(Location center, int radius) {
        World world = center.getWorld();
        if (world == null) return;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                Block ground = center.getBlock().getRelative(dx, -1, dz);
                Material type = ground.getType();
                if (type.isSolid() && !ground.isLiquid() && type != Material.SCULK
                        && type != Material.BEDROCK && type != Material.OBSIDIAN
                        && !(ground.getState() instanceof org.bukkit.block.TileState)) {
                    ground.setType(Material.SCULK, false);
                }
            }
        }
    }

    // --- Event Handlers ---

    @EventHandler
    private void onShootBow(EntityShootBowEvent evt) {
        if (evt.getEntity() instanceof Illusioner arch && isArchIllager(arch)) {
            if (evt.getProjectile() instanceof AbstractArrow arrow) {
                arrow.setFireTicks(120);
            }
        }
    }

    @EventHandler
    private void onFangsSpawn(EntitySpawnEvent evt) {
        if (evt.getEntityType() != EntityType.EVOKER_FANGS) return;
        EvokerFangs fangs = (EvokerFangs) evt.getEntity();
        LivingEntity owner = fangs.getOwner();
        if (owner instanceof Illusioner illusioner && (isArchIllager(illusioner) || isClone(illusioner))) {
            fangs.setVisualFire(TriState.TRUE);
            if (!fangs.getPersistentDataContainer().has(archFangsKey, PersistentDataType.DOUBLE)) {
                int phase = getPhase(illusioner);
                double dmg = (phase >= 3) ? PHASE_3_4_FANG_DAMAGE : PHASE_2_FANG_DAMAGE;
                fangs.getPersistentDataContainer().set(archFangsKey, PersistentDataType.DOUBLE, dmg);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    private void onFangsDamage(EntityDamageByEntityEvent evt) {
        if (!(evt.getDamager() instanceof EvokerFangs fangs)) return;
        if (!fangs.getPersistentDataContainer().has(archFangsKey, PersistentDataType.DOUBLE)) return;

        Entity victim = evt.getEntity();
        if (victim instanceof Raider || victim instanceof Vex) {
            evt.setCancelled(true);
            return;
        }

        if (!(victim instanceof LivingEntity living)) return;
        if (!processingFangDamage.add(living.getUniqueId())) {
            return;
        }

        try {
            double armorIgnoringDamage = fangs.getPersistentDataContainer().getOrDefault(
                    archFangsKey, PersistentDataType.DOUBLE, PHASE_2_FANG_DAMAGE);
            evt.setCancelled(true);

            DamageSource magicSource = DamageSource.builder(DamageType.MAGIC)
                    .withCausingEntity(fangs.getOwner() != null ? fangs.getOwner() : fangs)
                    .withDirectEntity(fangs)
                    .build();
            living.damage(armorIgnoringDamage, magicSource);
            living.setFireTicks(FANG_FIRE_TICKS);
        } finally {
            processingFangDamage.remove(living.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    private void onArchIllagerDamageByEntity(EntityDamageByEntityEvent evt) {
        if (!(evt.getEntity() instanceof Illusioner illusioner)) return;

        Entity damager = evt.getDamager();
        if (damager instanceof Raider || damager instanceof EvokerFangs || damager instanceof Vex) {
            if (isArchIllager(illusioner) || isClone(illusioner)) {
                evt.setCancelled(true);
                return;
            }
        }

        if (isClone(illusioner)) {
            evt.setCancelled(true);
            Location cloneLoc = illusioner.getLocation();
            World world = cloneLoc.getWorld();
            Illusioner owner = getCloneOwner(illusioner);
            int ownerPhase = (owner != null) ? getPhase(owner) : 2;

            if (owner != null) {
                clearClones(owner, true);
            } else {
                illusioner.remove();
            }

            LivingEntity target = null;
            if (damager instanceof Player p) {
                target = p;
            } else if (damager instanceof Projectile proj && proj.getShooter() instanceof LivingEntity shooter) {
                target = shooter;
            } else if (owner != null) {
                target = findTarget(owner);
            }

            if (world != null) {
                world.spawnParticle(Particle.LARGE_SMOKE, cloneLoc.clone().add(0, 1, 0), 40, 0.4, 0.6, 0.4, 0.08);
                world.playSound(cloneLoc, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.5f, 0.7f);
                for (int i = 0; i < 3; i++) {
                    Vex vex = (Vex) world.spawnEntity(cloneLoc.clone().add(0, 1, 0), EntityType.VEX);
                    if (target != null) {
                        vex.setTarget(target);
                    }
                }
            }

            if (owner != null && owner.isValid() && !owner.isDead() && ownerPhase == 4 && target != null) {
                castSonicBlast(owner, target);
            }
            return;
        }

        if (!isArchIllager(illusioner)) return;

        int phase = getPhase(illusioner);
        if (phase >= 2 && (damager instanceof Projectile || evt.getCause() == DamageCause.PROJECTILE)) {
            evt.setCancelled(true);
            illusioner.getWorld().playSound(illusioner.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.2f);
            return;
        }

        List<Illusioner> clones = activeClones.get(illusioner);
        if (phase >= 2 && clones != null && !clones.isEmpty()) {
            clearClones(illusioner, true);
            evt.setDamage(evt.getDamage() + 20.0);
            illusioner.setSpell(Spellcaster.Spell.NONE);
            illusioner.getPersistentDataContainer().set(stunUntilKey, PersistentDataType.LONG, System.currentTimeMillis() + 3000L);
            illusioner.getWorld().playSound(illusioner.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.2f, 1.5f);
            illusioner.getWorld().spawnParticle(Particle.CRIT, illusioner.getLocation().add(0, 1, 0), 30, 0.4, 0.5, 0.4, 0.2);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onArchIllagerDamage(EntityDamageEvent evt) {
        if (evt.getCause() == DamageCause.KILL || evt.getCause() == DamageCause.VOID) return;
        if (!(evt.getEntity() instanceof Illusioner arch) || !isArchIllager(arch)) return;

        int phase = getPhase(arch);

        if (phase == 1) {
            Ravager ravager = getLinkedRavager(arch);
            if (ravager != null && !ravager.isDead() && ravager.isValid()) {
                evt.setCancelled(true);
                return;
            } else {
                startPhase2(arch);
                phase = 2;
            }
        }

        if (isTransitioning(arch) || evt.getCause() == DamageCause.LIGHTNING
                || evt.getCause() == DamageCause.PROJECTILE || evt.getCause() == DamageCause.FALL) {
            evt.setCancelled(true);
            return;
        }

        double currentHealth = arch.getHealth();
        double finalDamage = evt.getFinalDamage();
        double nextHealth = currentHealth - finalDamage;

        if (nextHealth <= 0) {
            evt.setDamage(0);
            arch.setHealth(1.0);
            if (phase == 2) {
                triggerTotemTransition(arch, 3);
            } else if (phase == 3) {
                triggerTotemTransition(arch, 4);
            } else if (phase >= 4) {
                triggerBedrockWitherDeath(arch);
            }
            return;
        }

        PersistentDataContainer pdc = arch.getPersistentDataContainer();

        if (phase >= 2) {
            double next100 = pdc.getOrDefault(next100Key, PersistentDataType.DOUBLE, 400.0);
            if (nextHealth <= next100) {
                while (nextHealth <= next100) {
                    next100 -= 100.0;
                }
                pdc.set(next100Key, PersistentDataType.DOUBLE, next100);
                summonPhaseMinions(arch, phase);
            }
        }

        double next50 = pdc.getOrDefault(next50Key, PersistentDataType.DOUBLE, 450.0);
        if (nextHealth <= next50) {
            while (nextHealth <= next50) {
                next50 -= 50.0;
            }
            pdc.set(next50Key, PersistentDataType.DOUBLE, next50);
            int illusionCount = (phase == 2) ? 3 : (phase == 3 ? 5 : 9);
            spawnIllusionSplit(arch, illusionCount);
        }
    }

    @EventHandler
    private void onEntityDismount(EntityDismountEvent evt) {
        if (!(evt.getEntity() instanceof Illusioner arch) || !isArchIllager(arch)) return;
        if (getPhase(arch) == 1 && evt.getDismounted() instanceof Ravager ravager) {
            if (arch.isValid() && !arch.isDead() && ravager.isValid() && !ravager.isDead()) {
                if (evt.isCancellable()) {
                    evt.setCancelled(true);
                } else {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (arch.isValid() && !arch.isDead() && ravager.isValid() && !ravager.isDead()) {
                            ravager.addPassenger(arch);
                        }
                    });
                }
            } else {
                startPhase2(arch);
            }
        }
    }

    @EventHandler
    private void onDeath(EntityDeathEvent evt) {
        LivingEntity entity = evt.getEntity();

        if (entity instanceof Ravager ravager && ravager.getPersistentDataContainer().has(juggernautKey, PersistentDataType.BYTE)) {
            for (Entity passenger : ravager.getPassengers()) {
                if (passenger instanceof Illusioner arch && isArchIllager(arch) && getPhase(arch) == 1) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (arch.isValid() && !arch.isDead() && getPhase(arch) == 1) {
                            startPhase2(arch);
                        }
                    });
                }
            }
            return;
        }

        if (!(entity instanceof Illusioner illusioner)) return;

        if (isClone(illusioner)) {
            cloneOwners.remove(illusioner);
            evt.getDrops().clear();
            evt.setDroppedExp(0);
            return;
        }

        if (!isArchIllager(illusioner)) return;

        illusioner.setGravity(true);
        illusioner.setSpell(Spellcaster.Spell.NONE);
        clearClones(illusioner, true);
        linkedRavagers.remove(illusioner);
        activeArchIllagers.remove(illusioner);

        evt.getDrops().clear();
        evt.getDrops().add(new ItemStack(Material.ECHO_SHARD, 5));
        evt.getDrops().add(new ItemStack(Material.NETHERITE_INGOT, 1));
        evt.getDrops().add(bannerTemplate.clone());
        evt.setDroppedExp(DEATH_XP_POINTS);
    }

    // --- Helpers ---

    public boolean isArchIllager(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(archKey, PersistentDataType.BYTE);
    }

    public boolean isClone(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(cloneKey, PersistentDataType.BYTE);
    }

    public boolean hasActiveClones(Illusioner arch) {
        List<Illusioner> clones = activeClones.get(arch);
        return clones != null && !clones.isEmpty();
    }

    private int getPhase(Illusioner arch) {
        return arch.getPersistentDataContainer().getOrDefault(phaseKey, PersistentDataType.INTEGER, 1);
    }

    private boolean isTransitioning(Illusioner arch) {
        return arch.getPersistentDataContainer().getOrDefault(transitioningKey, PersistentDataType.BYTE, (byte) 0) != (byte) 0;
    }

    private long getStunUntil(Illusioner arch) {
        return arch.getPersistentDataContainer().getOrDefault(stunUntilKey, PersistentDataType.LONG, 0L);
    }

    private Ravager getLinkedRavager(Illusioner arch) {
        if (arch.getVehicle() instanceof Ravager r) {
            linkedRavagers.put(arch, r);
            return r;
        }
        Ravager cached = linkedRavagers.get(arch);
        if (cached != null) {
            if (!cached.isDead() && cached.isValid()) {
                return cached;
            }
            linkedRavagers.remove(arch);
        }

        String uuidStr = arch.getPersistentDataContainer().get(ravagerUuidKey, PersistentDataType.STRING);
        if (uuidStr != null) {
            try {
                Entity e = arch.getWorld().getEntity(UUID.fromString(uuidStr));
                if (e instanceof Ravager r && !r.isDead() && r.isValid()) {
                    linkedRavagers.put(arch, r);
                    return r;
                }
            } catch (IllegalArgumentException ignored) {}
        }
        return null;
    }

    private Illusioner getCloneOwner(Illusioner clone) {
        Illusioner cached = cloneOwners.get(clone);
        if (cached != null && !cached.isDead() && cached.isValid()) {
            return cached;
        }
        String uuidStr = clone.getPersistentDataContainer().get(cloneOwnerKey, PersistentDataType.STRING);
        if (uuidStr != null) {
            try {
                Entity e = clone.getWorld().getEntity(UUID.fromString(uuidStr));
                if (e instanceof Illusioner arch) {
                    cloneOwners.put(clone, arch);
                    return arch;
                }
            } catch (IllegalArgumentException ignored) {}
        }
        return null;
    }

    private LivingEntity findTarget(Illusioner arch) {
        LivingEntity current = arch.getTarget();
        if (current != null && isValidHostileTarget(current)) {
            return current;
        }
        Player nearestPlayer = null;
        double nearestDistSq = Double.MAX_VALUE;
        for (Player p : arch.getWorld().getPlayers()) {
            if (isValidHostileTarget(p)) {
                double d = p.getLocation().distanceSquared(arch.getLocation());
                if (d <= 4096.0 && d < nearestDistSq) {
                    nearestDistSq = d;
                    nearestPlayer = p;
                }
            }
        }
        if (nearestPlayer != null) return nearestPlayer;

        for (Entity e : arch.getNearbyEntities(32.0, 16.0, 32.0)) {
            if (e instanceof LivingEntity le && isValidHostileTarget(le)) {
                return le;
            }
        }
        return null;
    }

    private boolean isValidHostileTarget(LivingEntity entity) {
        if (entity == null || entity.isDead() || !entity.isValid()) return false;
        if (entity instanceof Player p) {
            return p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR;
        }
        return entity instanceof IronGolem || entity instanceof Villager;
    }

    private void moveTowardsTarget(LivingEntity entity, Location targetLoc, double speed) {
        if (entity instanceof Mob mob) {
            mob.getPathfinder().moveTo(targetLoc, speed);
        }
    }

    private void removeVanillaIllusionerSpells(Illusioner illusioner, boolean removeBowAttackGoal) {
        try {
            net.minecraft.world.entity.raid.Raider nmsIllusioner = ((CraftRaider) illusioner).getHandle();
            GoalSelector goalSelector = EvokerGoalUtil.getGoalSelector(nmsIllusioner);
            if (goalSelector == null) return;

            List<Goal> toRemove = new ArrayList<>();
            for (WrappedGoal wrappedGoal : goalSelector.getAvailableGoals()) {
                Goal goal = wrappedGoal.getGoal();
                String name = goal.getClass().getSimpleName();
                if (name.contains("MirrorSpell") || name.contains("BlindnessSpell")
                        || (removeBowAttackGoal && name.contains("RangedBowAttack"))) {
                    toRemove.add(goal);
                }
            }
            for (Goal goal : toRemove) {
                goalSelector.removeGoal(goal);
            }
        } catch (Exception ignored) {}
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

    private static ItemStack createArchIllagerBanner() {
        ItemStack banner = new ItemStack(Material.BLUE_BANNER);
        BannerMeta meta = (BannerMeta) banner.getItemMeta();
        if (meta != null) {
            meta.addPattern(new Pattern(DyeColor.BLACK, PatternType.CURLY_BORDER));
            meta.addPattern(new Pattern(DyeColor.LIGHT_BLUE, PatternType.RHOMBUS));
            meta.addPattern(new Pattern(DyeColor.CYAN, PatternType.SKULL));
            meta.addPattern(new Pattern(DyeColor.WHITE, PatternType.TRIANGLE_TOP));
            meta.addPattern(new Pattern(DyeColor.BLUE, PatternType.BORDER));
            meta.displayName(ChatColorUtil.parseItem("<blue><bold>The Arch-Illager Banner"));
            banner.setItemMeta(meta);
        }
        banner.setData(DataComponentTypes.TOOLTIP_DISPLAY,
                TooltipDisplay.tooltipDisplay().addHiddenComponents(DataComponentTypes.BANNER_PATTERNS).build());
        return banner;
    }
}