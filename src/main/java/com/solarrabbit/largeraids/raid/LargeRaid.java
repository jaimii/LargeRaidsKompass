package com.solarrabbit.largeraids.raid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import com.mojang.authlib.GameProfile;
import com.solarrabbit.largeraids.LargeRaids;
import com.solarrabbit.largeraids.config.RaidConfig;
import com.solarrabbit.largeraids.config.RewardsConfig;
import com.solarrabbit.largeraids.nms.AbstractBlockPositionWrapper;
import com.solarrabbit.largeraids.nms.AbstractMinecraftServerWrapper;
import com.solarrabbit.largeraids.nms.AbstractPlayerEntityWrapper;
import com.solarrabbit.largeraids.nms.AbstractRaidWrapper;
import com.solarrabbit.largeraids.nms.AbstractRaiderWrapper;
import com.solarrabbit.largeraids.nms.AbstractRaidsWrapper;
import com.solarrabbit.largeraids.nms.AbstractWorldServerWrapper;
import com.solarrabbit.largeraids.raid.mob.RiderRaider;
import com.solarrabbit.largeraids.util.VersionUtil;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Raid;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.Raid.RaidStatus;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Raider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class LargeRaid {
    private static final int RADIUS = 144;
    private static final int VANILLA_RAID_OMEN_LEVEL = 2;
    private static final int INVULNERABLE_TICKS = 20;
    private final RaidConfig raidConfig;
    private final RewardsConfig rewardsConfig;
    private final int maxTotalWaves;
    private final Location startLoc;
    private final Map<UUID, Integer> playerKills;
    private final Map<UUID, Double> playerDamage;
    private int totalWaves;
    private int omenLevel;
    private Raid currentRaid;
    private int currentWave;
    private boolean raidersOutlined;
    private Location raidSpawn;
    private Location raidTarget;
    private double raidTargetRadius;
    private double raidTargetNavSpeed;

    LargeRaid(RaidConfig raidConfig, RewardsConfig rewardsConfig, Location location, int omenLevel) {
        this.raidConfig = raidConfig;
        this.rewardsConfig = rewardsConfig;
        startLoc = location;
        maxTotalWaves = raidConfig.getMaximumWaves();
        currentWave = 1;
        playerKills = new HashMap<>();
        playerDamage = new HashMap<>();
        this.omenLevel = Math.min(this.maxTotalWaves, omenLevel);
        totalWaves = Math.max(5, this.omenLevel);
    }

    boolean startRaid() {
        if (!getNMSRaidAtCenter().isEmpty())
            return false;

        AbstractRaidWrapper raid = createRaid(startLoc);
        if (raid.isEmpty())
            return false;

        setRaid(VersionUtil.getCraftRaidWrapper(raid).getRaid());

        Bukkit.getScheduler().runTaskLater(JavaPlugin.getPlugin(LargeRaids.class), () -> {
            if (currentRaid.getStatus() == RaidStatus.STOPPED)
                return;
            broadcastWave();
            Sound sound = raidConfig.getSounds().getSummonSound();
            if (sound != null)
                playSoundToPlayersInRadius(sound);
        }, 2);

        return true;
    }

    void triggerNextWave() {
        currentWave++;
        broadcastWave();

        AbstractRaidWrapper nmsRaid = getCurrentNMSRaid();
        if (nmsRaid != null) {
            Object nmsRaidObj = getNMSRaidObject(nmsRaid);
            int raidId = getRaidId(nmsRaidObj);

            nmsRaid.stop();

            if (raidId != -1) {
                removeRaidFromNMS(getCenter(), raidId);
            }
        }

        AbstractRaidWrapper raid = createRaid(getCenter());
        if (raid.isEmpty())
            return;
        setRaid(VersionUtil.getCraftRaidWrapper(raid).getRaid());

        if (isLastWave())
            prepareLastWave();
    }

    void spawnWave() {
        spawnWave(null);
    }

    void spawnWave(Location vanillaSpawnLoc) {
        List<Raider> raiders = currentRaid.getRaiders();
        Location loc = getWaveSpawnLocation(vanillaSpawnLoc);

        if (loc == null) {
            loc = getRandomSpawnLocation(getCenter());
        }

        // Calculate 2D horizontal distance to accurately detect center spawns regardless of height (e.g. sky breeder bases)
        double locX = loc.getX();
        double locZ = loc.getZ();
        double centerX = getCenter().getX();
        double centerZ = getCenter().getZ();
        double horizontalDistSq = (locX - centerX) * (locX - centerX) + (locZ - centerZ) * (locZ - centerZ);

        // Define bounds: out of bounds, or too close horizontally (indicating a center-spawn fallback glitch)
        boolean outOfBounds = horizontalDistSq >= Math.pow(RADIUS + 16, 2);
        boolean glitchedAtCenter = horizontalDistSq <= 256.0; // Within 16 blocks horizontally

        if (outOfBounds || glitchedAtCenter) {
            // Cancel the spawn immediately by removing the raiders
            for (Raider raider : raiders) {
                removeRaiderAndMount(raider);
            }
            if (!isLastWave()) {
                // Force-advance to the next wave of the raid
                triggerNextWave();
            }
            return;
        }

        AbstractRaidWrapper nmsRaid = getCurrentNMSRaid();

        List<Raider> newRaiders = new ArrayList<>();
        List<Entity> nonRaiderVehicles = new ArrayList<>();

        for (Map.Entry<Function<Location, ? extends com.solarrabbit.largeraids.raid.mob.Raider>, Integer> kv : raidConfig
                .getRaiders().getWaveMobs(this.currentWave).entrySet()) {
            for (int i = 0; i < kv.getValue(); i++) {
                com.solarrabbit.largeraids.raid.mob.Raider entity = kv.getKey().apply(loc);
                Raider bukkitEntity = (Raider) entity.getBukkitEntity();
                nmsRaid.joinRaid(2, VersionUtil.getCraftRaiderWrapper(bukkitEntity).getHandle(), null, true);
                bukkitEntity.setInvulnerable(true);
                newRaiders.add(bukkitEntity);
                if (entity instanceof RiderRaider) {
                    Entity vehicle = ((RiderRaider) entity).getVehicle();
                    if (vehicle instanceof Raider) {
                        Raider ravager = (Raider) vehicle;
                        nmsRaid.joinRaid(2, VersionUtil.getCraftRaiderWrapper(ravager).getHandle(), null, true);
                        ravager.setInvulnerable(true);
                        newRaiders.add(ravager);
                    } else if (vehicle != null) {
                        vehicle.setInvulnerable(true);
                        nonRaiderVehicles.add(vehicle);
                    }
                }
            }
        }
        if (raidTarget != null) {
            setRaiderTarget();
        }
        Bukkit.getScheduler().runTaskLater(JavaPlugin.getPlugin(LargeRaids.class), () -> {
            for (Raider raider : newRaiders)
                raider.setInvulnerable(false);
            for (Entity vehicle : nonRaiderVehicles)
                vehicle.setInvulnerable(false);
        }, INVULNERABLE_TICKS);

        raiders.forEach(raider -> {
            nmsRaid.removeFromRaid(VersionUtil.getCraftRaiderWrapper(raider).getHandle(), true);
            removeRaiderAndMount(raider);
        });
    }

    private void removeRaiderAndMount(Raider raider) {
        Entity vehicle = raider.getVehicle();
        if (vehicle != null && vehicle.getType() == EntityType.HORSE) {
            vehicle.remove();
        }
        raider.remove();
    }

    public void skipWave() {
        if (isLastWave())
            return;
        else if (!isLoading()) {
            for (Raider raider : currentRaid.getRaiders()) {
                removeRaiderAndMount(raider);
            }
        }
        triggerNextWave();
    }

    public void stopRaid() {
        getCurrentNMSRaid().stop();
        for (Raider raider : currentRaid.getRaiders()) {
            removeRaiderAndMount(raider);
        }
    }

    void announceVictory() {
        Sound sound = raidConfig.getSounds().getVictorySound();
        if (sound != null)
            playSoundToPlayersInRadius(sound);
        getCurrentNMSRaid().getHeroesOfTheVillage().clear();
        for (UUID uuid : playerDamage.keySet())
            Optional.ofNullable(Bukkit.getPlayer(uuid)).filter(this::shouldAwardPlayer).ifPresent(this::awardPlayer);
    }

    void announceDefeat() {
        Sound sound = raidConfig.getSounds().getDefeatSound();
        if (sound != null)
            playSoundToPlayersInRadius(sound);
    }

    public void applyGlowing() {
        PotionEffect effect = new PotionEffect(PotionEffectType.GLOWING, 5, 0);
        currentRaid.getRaiders().forEach(raider -> raider.addPotionEffect(effect));
    }

    public Location getCenter() {
        return currentRaid == null ? startLoc : currentRaid.getLocation();
    }

    public int getRaidOmenLevel() {
        return omenLevel;
    }

    public int getCurrentWave() {
        return currentWave;
    }

    public int getTotalWaves() {
        return totalWaves;
    }

    public boolean isLastWave() {
        return currentWave == totalWaves;
    }

    public boolean isActive() {
        return getCurrentNMSRaid().isActive();
    }

    public boolean areRaidersOutlined() {
        return raidersOutlined;
    }

    public void setRaidersOutlined(boolean raidersOutlined) {
        this.raidersOutlined = raidersOutlined;
    }

    public boolean isLoading() {
        AbstractRaidWrapper nmsRaid = getCurrentNMSRaid();
        return !nmsRaid.hasFirstWaveSpawned() || nmsRaid.isBetweenWaves();
    }

    public boolean firstWaveSpawned() {
        AbstractRaidWrapper nmsRaid = getCurrentNMSRaid();
        return nmsRaid.hasFirstWaveSpawned();
    }

    public int getTotalRaidersAlive() {
        try {
            return currentRaid == null ? 0 : currentRaid.getRaiders().size();
        } catch (ConcurrentModificationException evt) {
            return 0;
        }
    }

    void absorbOmenLevel(int level) {
        omenLevel = Math.min(this.maxTotalWaves, this.omenLevel + level);
        totalWaves = Math.max(5, omenLevel);
    }

    public Set<Player> getPlayersInRadius() {
        Collection<Entity> collection = getCenter().getWorld().getNearbyEntities(getCenter(), RADIUS, RADIUS, RADIUS,
                entity -> entity instanceof Player
                        && getCenter().distanceSquared(entity.getLocation()) <= Math.pow(RADIUS, 2));
        Set<Player> set = new HashSet<>();
        collection.forEach(player -> set.add((Player) player));
        return set;
    }

    public Map<UUID, Integer> getPlayerKills() {
        return playerKills;
    }

    public boolean releaseOmen() {
        if (currentRaid.getBadOmenLevel() <= VANILLA_RAID_OMEN_LEVEL)
            return false;
        currentRaid.setBadOmenLevel(VANILLA_RAID_OMEN_LEVEL);
        return true;
    }

    public boolean isSimilar(Raid raid) {
        Objects.requireNonNull(currentRaid);
        return this.currentRaid.getLocation().getWorld().equals(raid.getLocation().getWorld())
                && this.currentRaid.getLocation().distanceSquared(raid.getLocation()) <= 1024.0;
    }

    void incrementPlayerKill(Player player) {
        playerKills.merge(player.getUniqueId(), 1, Integer::sum);
    }

    void incrementPlayerDamage(Player player, double damage) {
        playerDamage.merge(player.getUniqueId(), damage, Double::sum);
    }

    private void broadcastWave() {
        for (Player player : getPlayersInRadius()) {
            if (raidConfig.isTitleEnabled()) {
                String defaultStr = raidConfig.getDefaultWaveTitle(currentWave);
                String finalStr = raidConfig.getFinalWaveTitle();
                player.sendTitle(isLastWave() ? finalStr : defaultStr, null, 10, 70, 20);
            }
            if (raidConfig.isMessageEnabled()) {
                String defaultStr = raidConfig.getDefaultWaveMessage(currentWave);
                String finalStr = raidConfig.getFinalWaveMessage();
                player.sendMessage(isLastWave() ? finalStr : defaultStr);
            }
        }
    }

    private boolean shouldAwardPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        boolean hasMinKills = Optional.ofNullable(playerKills.get(uuid)).orElse(0).intValue() >= rewardsConfig
                .getMinRaiderKills();
        boolean hasMinDamage = Optional.ofNullable(playerDamage.get(uuid)).orElse(0.0).doubleValue() >= rewardsConfig
                .getMinDamageDeal();
        return hasMinKills && hasMinDamage;
    }

    private void awardPlayer(Player player) {
        int level = Math.min(rewardsConfig.getHeroLevel(), omenLevel);
        int duration = rewardsConfig.getHeroDuration() * 60 * 20;
        if (level > 0)
            player.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, duration, level - 1));

        player.sendMessage(rewardsConfig.getMessage());
        player.getInventory().addItem(rewardsConfig.getItems())
                .forEach((i, item) -> player.getWorld().dropItem(player.getLocation(), item));

        for (String command : rewardsConfig.getCommands())
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("<player>", player.getName())
                    .replace("<omen>", String.valueOf(getRaidOmenLevel())));
    }

    private void setRaid(Raid raid) {
        currentRaid = raid;
    }

    private void playSoundToPlayersInRadius(Sound sound) {
        getPlayersInRadius().forEach(player -> player.playSound(player.getLocation(), sound, 50, 1));
    }

    private Location getWaveSpawnLocation() {
        return getWaveSpawnLocation(null);
    }

    private Location getWaveSpawnLocation(Location vanillaSpawnLoc) {
        if (raidSpawn != null)
            return raidSpawn;

        if (vanillaSpawnLoc != null) {
            return vanillaSpawnLoc;
        }

        List<Raider> list = this.currentRaid.getRaiders();
        if (list.isEmpty()) {
            return getRandomSpawnLocation(getCenter());
        }
        return list.get(0).getLocation();
    }

    private Location getRandomSpawnLocation(Location center) {
        World world = center.getWorld();
        if (world == null) return center;

        java.util.Random random = new java.util.Random();
        for (int i = 0; i < 50; i++) { // Increased search bounds to 50 attempts
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = 48 + random.nextDouble() * 16; // Spawn 48 to 64 blocks away from village center

            int x = (int) (center.getX() + Math.cos(angle) * distance);
            int z = (int) (center.getZ() + Math.sin(angle) * distance);

            int y = world.getHighestBlockYAt(x, z);
            org.bukkit.block.Block block = world.getBlockAt(x, y, z);
            org.bukkit.block.Block below = world.getBlockAt(x, y - 1, z);
            org.bukkit.block.Block above = world.getBlockAt(x, y + 1, z);

            // Resilient check verifying block below is solid, while blocks at Y and Y+1 are passable (handles snow cover, tall grass, flowers, etc.)
            if (below.getType().isSolid() && isBlockPassable(block) && isBlockPassable(above)) {
                return new Location(world, x + 0.5, y, z + 0.5);
            }
        }
        return center; // Fallback if no safe blocks are found
    }

    private boolean isBlockPassable(org.bukkit.block.Block block) {
        if (block == null) return true;
        try {
            return block.isPassable();
        } catch (NoSuchMethodError e) {
            Material type = block.getType();
            return type.isAir() || type == Material.SNOW || type.name().contains("GRASS") || type.name().contains("FLOWER");
        }
    }

    private void prepareLastWave() {
        int withoutBonus = getDefaultWaveNumber(getCenter().getWorld());
        getCurrentNMSRaid().setGroupsSpawned(withoutBonus);
    }

    private AbstractRaidWrapper getCurrentNMSRaid() {
        return currentRaid == null ? null : VersionUtil.getCraftRaidWrapper(currentRaid).getHandle();
    }

    private AbstractRaidWrapper getNMSRaidAtCenter() {
        AbstractBlockPositionWrapper blkPos = VersionUtil.getBlockPositionWrapper(getCenter());
        AbstractWorldServerWrapper level = VersionUtil.getCraftWorldWrapper(getCenter().getWorld()).getHandle();
        return level.getRaidAt(blkPos);
    }

    public Location getSpawnLocation() {
        return raidSpawn;
    }

    public void setSpawnLocation(Location raidSpawn) {
        this.raidSpawn = raidSpawn;
    }

    public Location getRaidTarget() {
        return raidTarget;
    }

    public double getRaidTargetRadius() {
        return raidTargetRadius;
    }

    public double getRaidTargetNavSpeed() {
        return raidTargetNavSpeed;
    }

    public void setRaidTarget(Location target, double radius, double navSpeed) {
        this.raidTarget = target;
        this.raidTargetRadius = radius;
        this.raidTargetNavSpeed = navSpeed;
        if (currentRaid != null) {
            setRaiderTarget();
        }
    }

    private void setRaiderTarget() {
        AbstractBlockPositionWrapper target = raidTarget == null ? null
                : VersionUtil.getBlockPositionWrapper(raidTarget);
        for (Raider raider : currentRaid.getRaiders()) {
            AbstractRaiderWrapper wrapper = VersionUtil.getCraftRaiderWrapper(raider).getHandle();
            wrapper.setRaiderTarget(target, raidTargetRadius, raidTargetNavSpeed);
        }
    }

    public boolean addAttackGoal(int prio, boolean mustSee, Class<?> entityClass) {
        for (Raider raider : currentRaid.getRaiders()) {
            AbstractRaiderWrapper wrapper = VersionUtil.getCraftRaiderWrapper(raider).getHandle();
            if (!wrapper.addAttackGoal(prio, mustSee, entityClass))
                return false;
        }
        return true;
    }

    private AbstractRaidWrapper createRaid(Location location) {
        AbstractPlayerEntityWrapper abstractPlayer = createEntityPlayer(location);
        AbstractWorldServerWrapper level = VersionUtil.getCraftWorldWrapper(location.getWorld()).getHandle();
        AbstractRaidsWrapper raids = level.getRaids();
        try {
            raids.createOrExtendRaid(abstractPlayer);
        } catch (NullPointerException e) {
        }
        raids.setDirty();

        AbstractBlockPositionWrapper blkPos = VersionUtil.getBlockPositionWrapper(location.getX(), location.getY(),
                location.getZ());
        AbstractRaidWrapper raid = level.getRaidAt(blkPos);
        if (!raid.isEmpty())
            raid.setRaidOmenLevel(VANILLA_RAID_OMEN_LEVEL);
        return raid;
    }

    private AbstractPlayerEntityWrapper createEntityPlayer(Location location) {
        AbstractMinecraftServerWrapper nmsServer = VersionUtil.getCraftServerWrapper(Bukkit.getServer()).getServer();
        AbstractWorldServerWrapper nmsWorld = VersionUtil.getCraftWorldWrapper(location.getWorld()).getHandle();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "LargeRaids");
        AbstractPlayerEntityWrapper abstractPlayer = VersionUtil.getPlayerEntityWrapper(nmsServer,
                nmsWorld, profile);
        abstractPlayer.setPosition(location.getX(), location.getY(), location.getZ());
        return abstractPlayer;
    }

    private int getDefaultWaveNumber(World world) {
        Difficulty difficulty = world.getDifficulty();
        switch (difficulty) {
            case EASY:
                return 3;
            case NORMAL:
                return 5;
            case HARD:
                return 7;
            default:
                return 0;
        }
    }

    // --- Resilient Reflection Helpers to bypass same-tick map conflicts ---

    private Object getNMSRaidObject(AbstractRaidWrapper wrapper) {
        if (wrapper == null) return null;
        try {
            try {
                java.lang.reflect.Field f = wrapper.getClass().getDeclaredField("raid");
                f.setAccessible(true);
                return f.get(wrapper);
            } catch (NoSuchFieldException ignored) {}

            for (java.lang.reflect.Field f : wrapper.getClass().getDeclaredFields()) {
                if (f.getType().getName().equals("net.minecraft.world.entity.raid.Raid")) {
                    f.setAccessible(true);
                    return f.get(wrapper);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private int getRaidId(Object nmsRaid) {
        if (nmsRaid == null) return -1;
        try {
            try {
                java.lang.reflect.Field f = nmsRaid.getClass().getDeclaredField("id");
                f.setAccessible(true);
                return (int) f.get(nmsRaid);
            } catch (NoSuchFieldException ignored) {}

            for (java.lang.reflect.Field f : nmsRaid.getClass().getDeclaredFields()) {
                if (f.getType() == int.class && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    f.setAccessible(true);
                    return (int) f.get(nmsRaid);
                }
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private Object getNMSRaidsObject(AbstractRaidsWrapper wrapper) {
        if (wrapper == null) return null;
        try {
            try {
                java.lang.reflect.Field f = wrapper.getClass().getDeclaredField("raids");
                f.setAccessible(true);
                return f.get(wrapper);
            } catch (NoSuchFieldException ignored) {}

            for (java.lang.reflect.Field f : wrapper.getClass().getDeclaredFields()) {
                if (f.getType().getName().equals("net.minecraft.world.entity.raid.Raids")) {
                    f.setAccessible(true);
                    return f.get(wrapper);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private java.util.Map<Integer, ?> getRaidMap(Object nmsRaids) {
        if (nmsRaids == null) return null;
        try {
            try {
                java.lang.reflect.Field f = nmsRaids.getClass().getDeclaredField("raidMap");
                f.setAccessible(true);
                return (java.util.Map<Integer, ?>) f.get(nmsRaids);
            } catch (NoSuchFieldException ignored) {}

            try {
                java.lang.reflect.Field f = nmsRaids.getClass().getDeclaredField("raids");
                f.setAccessible(true);
                return (java.util.Map<Integer, ?>) f.get(nmsRaids);
            } catch (NoSuchFieldException ignored) {}

            for (java.lang.reflect.Field f : nmsRaids.getClass().getDeclaredFields()) {
                if (java.util.Map.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    return (java.util.Map<Integer, ?>) f.get(nmsRaids);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void removeRaidFromNMS(Location location, int raidId) {
        try {
            AbstractWorldServerWrapper level = VersionUtil.getCraftWorldWrapper(location.getWorld()).getHandle();
            AbstractRaidsWrapper raidsWrapper = level.getRaids();
            Object nmsRaids = getNMSRaidsObject(raidsWrapper);
            if (nmsRaids != null) {
                java.util.Map<Integer, ?> raidMap = getRaidMap(nmsRaids);
                if (raidMap != null) {
                    raidMap.remove(raidId);
                }
            }
        } catch (Exception ignored) {}
    }
}