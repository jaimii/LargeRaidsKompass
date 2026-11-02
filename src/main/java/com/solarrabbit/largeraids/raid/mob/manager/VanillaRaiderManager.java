package com.solarrabbit.largeraids.raid.mob.manager;

import com.solarrabbit.largeraids.LargeRaids;
import com.solarrabbit.largeraids.config.custommobs.CustomMobsConfig;
import com.solarrabbit.largeraids.nms.AbstractRaidWrapper;
import com.solarrabbit.largeraids.raid.LargeRaid;
import com.solarrabbit.largeraids.raid.mob.VanillaRaider;
import com.solarrabbit.largeraids.raid.mob.VanillaRiderRaider;
import com.solarrabbit.largeraids.util.VersionUtil;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Raider;
import org.bukkit.entity.Ravager;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Witch;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.LingeringPotionSplashEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.raid.RaidSpawnWaveEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

public class VanillaRaiderManager implements MobManager, Listener {
    private static final long SUPPORT_THROW_COOLDOWN_MS = 3500L;

    private final LargeRaids plugin;
    private final NamespacedKey raidWitchKey;
    private final NamespacedKey witchCloudKey;
    private final NamespacedKey beneficialCloudKey;
    private final NamespacedKey memberKey;

    private final Set<Witch> activeRaidWitches = ConcurrentHashMap.newKeySet();
    private final Map<Witch, Long> lastSupportThrow = new WeakHashMap<>();

    public VanillaRaiderManager() {
        this.plugin = JavaPlugin.getPlugin(LargeRaids.class);
        this.raidWitchKey = new NamespacedKey(plugin, "raid_witch");
        this.witchCloudKey = new NamespacedKey(plugin, "raid_witch_cloud");
        this.beneficialCloudKey = new NamespacedKey(plugin, "raid_witch_beneficial");
        this.memberKey = new NamespacedKey(plugin, LargeRaid.RAID_MEMBER_KEY);
        startWitchTask();
    }

    @Override
    public void loadSettings(CustomMobsConfig config) {
    }

    public VanillaRaider spawn(Location location, EntityType type) {
        Raider raider = (Raider) location.getWorld().spawnEntity(location, type);
        if (raider instanceof Witch witch) {
            registerRaidWitch(witch);
        }
        return new VanillaRaider(raider);
    }

    public VanillaRiderRaider spawnRider(Location location, EntityType type) {
        Raider rider = (Raider) location.getWorld().spawnEntity(location, type);
        if (rider instanceof Witch witch) {
            registerRaidWitch(witch);
        }
        Ravager ravager = (Ravager) location.getWorld().spawnEntity(location, EntityType.RAVAGER);
        ravager.addPassenger(rider);
        return new VanillaRiderRaider(rider, ravager);
    }

    public void registerRaidWitch(Witch witch) {
        if (witch == null || witch.isDead() || !witch.isValid()) return;
        witch.setCanJoinRaid(true);
        witch.getPersistentDataContainer().set(raidWitchKey, PersistentDataType.BYTE, (byte) 1);
        activeRaidWitches.remove(witch);
        activeRaidWitches.add(witch);
    }

    private boolean isRaidWitch(Witch witch) {
        if (witch == null) return false;
        PersistentDataContainer pdc = witch.getPersistentDataContainer();
        if (pdc.has(raidWitchKey, PersistentDataType.BYTE) || pdc.has(memberKey, PersistentDataType.INTEGER)) {
            return true;
        }
        AbstractRaidWrapper nmsRaid = VersionUtil.getCraftRaiderWrapper(witch).getHandle().getCurrentRaid();
        return nmsRaid != null && !nmsRaid.isEmpty();
    }

    private void startWitchTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activeRaidWitches.isEmpty())
                return;

            Iterator<Witch> itr = activeRaidWitches.iterator();
            while (itr.hasNext()) {
                Witch witch = itr.next();
                if (witch == null || witch.isDead() || !witch.isValid()) {
                    itr.remove();
                    continue;
                }
                tickRaidWitch(witch);
            }
        }, 10L, 10L);

        // One-time startup scan for any Witches already loaded in the world
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (World world : Bukkit.getWorlds()) {
                for (Witch witch : world.getEntitiesByClass(Witch.class)) {
                    if (isRaidWitch(witch)) {
                        registerRaidWitch(witch);
                    }
                }
            }
        });
    }

    private void tickRaidWitch(Witch witch) {
        Location witchLoc = witch.getLocation();
        World world = witchLoc.getWorld();
        if (world == null) return;

        // 1. Scan nearby fellow Raiders for support (healing/buffing) and squad following
        Raider mostInjuredRaider = null;
        double lowestHealthRatio = 0.85; // Support raiders below 85% health
        Raider buffCandidate = null;
        Raider nearestFellowRaider = null;
        double nearestRaiderDistSq = Double.MAX_VALUE;

        Collection<Raider> nearbyRaiders = world.getNearbyEntitiesByType(Raider.class, witchLoc, 28.0, 16.0, 28.0);
        for (Raider raider : nearbyRaiders) {
            if (raider == witch || raider instanceof Witch || raider.isDead() || !raider.isValid())
                continue;

            double distSq = witchLoc.distanceSquared(raider.getLocation());
            if (distSq < nearestRaiderDistSq) {
                nearestRaiderDistSq = distSq;
                nearestFellowRaider = raider;
            }

            if (distSq <= 256.0) { // Within 16 blocks for support
                AttributeInstance maxAttr = raider.getAttribute(Attribute.MAX_HEALTH);
                double maxHealth = maxAttr != null ? maxAttr.getValue() : 20.0;
                double ratio = raider.getHealth() / maxHealth;
                if (ratio < lowestHealthRatio) {
                    lowestHealthRatio = ratio;
                    mostInjuredRaider = raider;
                } else if (buffCandidate == null && raider.getTarget() != null
                        && !raider.hasPotionEffect(PotionEffectType.REGENERATION)
                        && !raider.hasPotionEffect(PotionEffectType.SPEED)) {
                    buffCandidate = raider;
                }
            }
        }

        // 2. Throw Supportive Lingering Potion at Injured or Fighting Fellow Raider
        Raider supportTarget = (mostInjuredRaider != null) ? mostInjuredRaider : buffCandidate;
        long now = System.currentTimeMillis();
        if (supportTarget != null) {
            double supportDistSq = witchLoc.distanceSquared(supportTarget.getLocation());
            if (supportDistSq > 100.0) { // Move closer if > 10 blocks away
                witch.getPathfinder().moveTo(supportTarget.getLocation(), 1.1);
            } else if (now - lastSupportThrow.getOrDefault(witch, 0L) >= SUPPORT_THROW_COOLDOWN_MS) {
                lastSupportThrow.put(witch, now);
                boolean needsDirectHeal = (mostInjuredRaider != null && lowestHealthRatio < 0.65);
                throwSupportLingeringPotion(witch, supportTarget, needsDirectHeal);
                return;
            }
        }

        // 3. Check or Acquire Hostile Target (Player / IronGolem / Villager)
        LivingEntity currentTarget = witch.getTarget();
        if (currentTarget instanceof Raider) {
            // Clear fellow raider from attack target so vanilla AI doesn't get stuck
            witch.setTarget(null);
            currentTarget = null;
        }
        if (currentTarget == null || currentTarget.isDead() || !currentTarget.isValid() || !isValidHostile(currentTarget)) {
            currentTarget = findNearestHostile(witch, witchLoc);
            if (currentTarget != null) {
                witch.setTarget(currentTarget);
            }
        }

        // 4. Constantly Follow Fellow Raiders unless actively in attack range of a hostile
        boolean activelyAttackingHostile = (currentTarget != null
                && witchLoc.distanceSquared(currentTarget.getLocation()) <= 144.0); // Within 12 blocks

        if (nearestFellowRaider != null) {
            if (!activelyAttackingHostile && nearestRaiderDistSq > 16.0) {
                // Stay within 4 blocks of the nearest fellow raider
                witch.getPathfinder().moveTo(nearestFellowRaider.getLocation(), 1.05);
            } else if (activelyAttackingHostile && nearestRaiderDistSq > 324.0) {
                // If drifted > 18 blocks away from the squad, regroup toward fellow raiders
                witch.getPathfinder().moveTo(nearestFellowRaider.getLocation(), 1.1);
            }
        }
    }

    private void throwSupportLingeringPotion(Witch witch, Raider target, boolean instantHeal) {
        Location eyeLoc = witch.getEyeLocation();
        Location targetLoc = target.getLocation().add(0, 0.5, 0);
        Vector dir = targetLoc.toVector().subtract(eyeLoc.toVector());

        ItemStack item = new ItemStack(Material.LINGERING_POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        if (meta != null) {
            if (instantHeal) {
                meta.setBasePotionType(PotionType.STRONG_HEALING);
                meta.addCustomEffect(new PotionEffect(PotionEffectType.REGENERATION, 160, 1), true);
            } else {
                meta.setBasePotionType(PotionType.SWIFTNESS);
                meta.addCustomEffect(new PotionEffect(PotionEffectType.REGENERATION, 160, 0), true);
                meta.addCustomEffect(new PotionEffect(PotionEffectType.SPEED, 300, 1), true);
            }
            item.setItemMeta(meta);
        }

        ThrownPotion potion = witch.launchProjectile(ThrownPotion.class);
        potion.setItem(item);
        potion.getPersistentDataContainer().set(witchCloudKey, PersistentDataType.BYTE, (byte) 1);
        potion.getPersistentDataContainer().set(beneficialCloudKey, PersistentDataType.BYTE, (byte) 1);

        double dist = Math.sqrt(dir.getX() * dir.getX() + dir.getZ() * dir.getZ());
        Vector velocity = new Vector(dir.getX(), dir.getY() + (dist * 0.2), dir.getZ()).normalize().multiply(0.75);
        potion.setVelocity(velocity);

        witch.getWorld().playSound(witch.getLocation(), Sound.ENTITY_WITCH_THROW, 1.0f, 0.9f);
    }

    private LivingEntity findNearestHostile(Witch witch, Location witchLoc) {
        Player nearestPlayer = null;
        double nearestDistSq = 256.0; // 16 blocks
        for (Player p : witch.getWorld().getPlayers()) {
            if (isValidHostile(p)) {
                double d = p.getLocation().distanceSquared(witchLoc);
                if (d < nearestDistSq && witch.hasLineOfSight(p)) {
                    nearestDistSq = d;
                    nearestPlayer = p;
                }
            }
        }
        if (nearestPlayer != null) return nearestPlayer;

        for (Entity e : witch.getNearbyEntities(16.0, 10.0, 16.0)) {
            if (e instanceof LivingEntity le && isValidHostile(le) && witch.hasLineOfSight(le)) {
                return le;
            }
        }
        return null;
    }

    private boolean isValidHostile(LivingEntity entity) {
        if (entity == null || entity.isDead() || !entity.isValid()) return false;
        if (entity instanceof Player p) {
            return p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR;
        }
        return entity instanceof IronGolem || entity instanceof Villager;
    }

    // --- Event Listeners ---

    @EventHandler
    private void onRaidSpawnWave(RaidSpawnWaveEvent evt) {
        for (Raider raider : evt.getRaiders()) {
            if (raider instanceof Witch witch) {
                registerRaidWitch(witch);
            }
        }
    }

    @EventHandler
    private void onEntitiesLoad(EntitiesLoadEvent evt) {
        for (Entity entity : evt.getEntities()) {
            if (entity instanceof Witch witch && isRaidWitch(witch)) {
                registerRaidWitch(witch);
            }
        }
    }

    @EventHandler
    private void onProjectileLaunch(ProjectileLaunchEvent evt) {
        if (!(evt.getEntity() instanceof ThrownPotion potion))
            return;
        if (!(potion.getShooter() instanceof Witch witch) || !isRaidWitch(witch))
            return;

        ItemStack current = potion.getItem();
        if (current.getType() != Material.LINGERING_POTION) {
            ItemStack lingering = new ItemStack(Material.LINGERING_POTION);
            lingering.setItemMeta(current.getItemMeta());
            potion.setItem(lingering);
        }

        potion.getPersistentDataContainer().set(witchCloudKey, PersistentDataType.BYTE, (byte) 1);
        if (isBeneficialPotion(potion.getItem())) {
            potion.getPersistentDataContainer().set(beneficialCloudKey, PersistentDataType.BYTE, (byte) 1);
        }
    }

    @EventHandler
    private void onLingeringSplash(LingeringPotionSplashEvent evt) {
        ThrownPotion potion = evt.getEntity();
        if (!potion.getPersistentDataContainer().has(witchCloudKey, PersistentDataType.BYTE))
            return;

        AreaEffectCloud cloud = evt.getAreaEffectCloud();
        cloud.getPersistentDataContainer().set(witchCloudKey, PersistentDataType.BYTE, (byte) 1);
        if (potion.getPersistentDataContainer().has(beneficialCloudKey, PersistentDataType.BYTE)) {
            cloud.getPersistentDataContainer().set(beneficialCloudKey, PersistentDataType.BYTE, (byte) 1);
        }
    }

    @EventHandler
    private void onPotionSplash(PotionSplashEvent evt) {
        ThrownPotion potion = evt.getEntity();
        if (!potion.getPersistentDataContainer().has(witchCloudKey, PersistentDataType.BYTE))
            return;

        // If the server fired PotionSplashEvent (e.g. from ThrownSplashPotion entity type),
        // cancel the normal splash and spawn a true lingering AreaEffectCloud instead.
        evt.setCancelled(true);

        Location loc = potion.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        AreaEffectCloud cloud = (AreaEffectCloud) world.spawnEntity(loc, EntityType.AREA_EFFECT_CLOUD);
        cloud.setRadius(3.0f);
        cloud.setRadiusOnUse(-0.5f);
        cloud.setWaitTime(10);
        cloud.setDuration(160);
        cloud.setRadiusPerTick(-cloud.getRadius() / cloud.getDuration());
        if (potion.getShooter() instanceof Witch witch) {
            cloud.setSource(witch);
        }

        ItemStack item = potion.getItem();
        if (item.getItemMeta() instanceof PotionMeta meta) {
            if (meta.getBasePotionType() != null) {
                cloud.setBasePotionType(meta.getBasePotionType());
            }
            for (PotionEffect effect : meta.getCustomEffects()) {
                cloud.addCustomEffect(effect, true);
            }
            if (meta.hasColor() && meta.getColor() != null) {
                cloud.setColor(meta.getColor());
            }
        }

        cloud.getPersistentDataContainer().set(witchCloudKey, PersistentDataType.BYTE, (byte) 1);
        if (potion.getPersistentDataContainer().has(beneficialCloudKey, PersistentDataType.BYTE)
                || isBeneficialPotion(item)) {
            cloud.getPersistentDataContainer().set(beneficialCloudKey, PersistentDataType.BYTE, (byte) 1);
            if (cloud.getColor().equals(Color.fromRGB(0))) {
                cloud.setColor(Color.FUCHSIA);
            }
        }
    }

    @EventHandler
    private void onAreaEffectCloudApply(AreaEffectCloudApplyEvent evt) {
        AreaEffectCloud cloud = evt.getEntity();
        boolean isWitchCloud = cloud.getPersistentDataContainer().has(witchCloudKey, PersistentDataType.BYTE)
                || (cloud.getSource() instanceof Witch witch && isRaidWitch(witch));
        if (!isWitchCloud)
            return;

        boolean beneficial = cloud.getPersistentDataContainer().has(beneficialCloudKey, PersistentDataType.BYTE);
        if (beneficial) {
            // Beneficial raid Witch clouds only heal/buff fellow Raiders
            evt.getAffectedEntities().removeIf(entity -> !(entity instanceof Raider));
        } else {
            // Harmful raid Witch clouds never damage fellow Raiders
            evt.getAffectedEntities().removeIf(entity -> entity instanceof Raider);
        }
    }

    private boolean isBeneficialPotion(ItemStack item) {
        if (item == null || !(item.getItemMeta() instanceof PotionMeta meta)) return false;
        PotionType base = meta.getBasePotionType();
        if (base != null) {
            String name = base.name();
            if (name.contains("HEALING") || name.contains("REGENERATION")
                    || name.contains("SWIFTNESS") || name.contains("STRENGTH")
                    || name.contains("FIRE_RESISTANCE")) {
                return true;
            }
        }
        for (PotionEffect effect : meta.getCustomEffects()) {
            PotionEffectType type = effect.getType();
            if (type.equals(PotionEffectType.INSTANT_HEALTH)
                    || type.equals(PotionEffectType.REGENERATION)
                    || type.equals(PotionEffectType.SPEED)
                    || type.equals(PotionEffectType.STRENGTH)
                    || type.equals(PotionEffectType.FIRE_RESISTANCE)) {
                return true;
            }
        }
        return false;
    }
}