package com.solarrabbit.largeraids.raid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
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
import com.solarrabbit.largeraids.raid.mob.Adjudicator;
import com.solarrabbit.largeraids.raid.mob.ArchIllager;
import com.solarrabbit.largeraids.raid.mob.RiderRaider;
import com.solarrabbit.largeraids.raid.mob.VanillaRaider;
import com.solarrabbit.largeraids.raid.mob.VanillaRiderRaider;
import com.solarrabbit.largeraids.raid.mob.manager.AdjudicatorManager;
import com.solarrabbit.largeraids.raid.mob.manager.ArchIllagerManager;
import com.solarrabbit.largeraids.raid.mob.manager.VanillaRaiderManager;
import com.solarrabbit.largeraids.util.ChatColorUtil;
import com.solarrabbit.largeraids.util.VersionUtil;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.util.Ticks;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Difficulty;
import org.bukkit.DyeColor;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Raid;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.Raid.RaidStatus;
import org.bukkit.block.Block;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Illusioner;
import org.bukkit.entity.Player;
import org.bukkit.entity.Raider;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class LargeRaid {
    public static final String RAID_MEMBER_KEY = "largeraid_spawned";
    private static final int RADIUS = 144;
    private static final double RADIUS_SQ = RADIUS * RADIUS;
    private static final double OUT_OF_BOUNDS_SQ = (RADIUS + 16) * (RADIUS + 16);
    private static final double CENTER_GLITCH_SQ = 256.0;
    private static final int VANILLA_RAID_OMEN_LEVEL = 2;
    private static final int INVULNERABLE_TICKS = 20;
    private static final PotionEffect GLOW_EFFECT = new PotionEffect(PotionEffectType.GLOWING, 35, 0, false, false);
    private static final Title.Times WAVE_TITLE_TIMES = Title.Times.times(
            Ticks.duration(10), Ticks.duration(70), Ticks.duration(20));
    private static final ItemStack OMINOUS_BANNER_TEMPLATE = createOminousBanner();

    private final LargeRaids plugin;
    private final NamespacedKey memberKey;
    private final RaidConfig raidConfig;
    private final RewardsConfig rewardsConfig;
    private final int maxTotalWaves;
    private final Location startLoc;
    private final Map<UUID, Integer> playerKills;
    private final Map<UUID, Double> playerDamage;
    private final Map<UUID, Location> waveRaiderLocations;
    private final Set<Chunk> ticketedChunks;

    private int totalWaves;
    private int omenLevel;
    private Raid currentRaid;
    private AbstractRaidWrapper currentNMSRaid;
    private int currentWave;
    private boolean raidersOutlined;
    private Location raidSpawn;
    private Location raidTarget;
    private double raidTargetRadius;
    private double raidTargetNavSpeed;
    private Location savedCenterLoc;
    private boolean pendingRestore;
    private int restoreTicksWaited;
    private UUID triggererUuid;
    private int triggererDeaths;

    LargeRaid(RaidConfig raidConfig, RewardsConfig rewardsConfig, Location location, int omenLevel) {
        this.plugin = JavaPlugin.getPlugin(LargeRaids.class);
        this.memberKey = new NamespacedKey(this.plugin, RAID_MEMBER_KEY);
        this.raidConfig = raidConfig;
        this.rewardsConfig = rewardsConfig;
        this.startLoc = location;
        this.maxTotalWaves = raidConfig.getMaximumWaves();
        this.currentWave = 1;
        this.playerKills = new HashMap<>();
        this.playerDamage = new HashMap<>();
        this.waveRaiderLocations = new HashMap<>();
        this.ticketedChunks = new HashSet<>();
        this.omenLevel = Math.min(this.maxTotalWaves, omenLevel);
        this.totalWaves = Math.max(5, this.omenLevel);
        this.pendingRestore = false;
        this.triggererDeaths = 0;
    }

    public boolean isPendingRestore() {
        return pendingRestore;
    }

    public UUID getTriggererUuid() {
        return triggererUuid;
    }

    public void setTriggererUuid(UUID triggererUuid) {
        this.triggererUuid = triggererUuid;
    }

    public int getTriggererDeaths() {
        return triggererDeaths;
    }

    public void incrementTriggererDeath() {
        this.triggererDeaths++;
        if (this.currentWave == this.totalWaves && !canUnlockSecretWave()) {
            prepareLastWave();
        }
    }

    public boolean canUnlockSecretWave() {
        return omenLevel >= maxTotalWaves && triggererDeaths < 3;
    }

    public boolean shouldSpawnArchIllager() {
        return currentWave > maxTotalWaves;
    }

    boolean startRaid() {
        if (!getNMSRaidAtCenter().isEmpty())
            return false;

        AbstractRaidWrapper raid = createRaid(startLoc);
        if (raid.isEmpty())
            return false;

        setRaid(VersionUtil.getCraftRaidWrapper(raid).getRaid());

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (currentRaid == null || currentRaid.getStatus() == RaidStatus.STOPPED)
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
        waveRaiderLocations.clear();
        broadcastWave();

        Location center = getCenter();
        AbstractRaidWrapper oldNmsRaid = this.currentNMSRaid;
        setRaid(null);

        if (oldNmsRaid != null && !oldNmsRaid.isEmpty()) {
            oldNmsRaid.stop();
            AbstractWorldServerWrapper level = VersionUtil.getCraftWorldWrapper(center.getWorld()).getHandle();
            level.getRaids().removeRaid(oldNmsRaid);
        }

        AbstractRaidWrapper newRaid = createRaid(center);
        if (newRaid.isEmpty())
            return;
        setRaid(VersionUtil.getCraftRaidWrapper(newRaid).getRaid());

        if (isLastWave())
            prepareLastWave();

        plugin.getRaidManager().saveRaids();
    }

    void spawnWave() {
        spawnWave(null);
    }

    void spawnWave(Location vanillaSpawnLoc) {
        if (currentRaid == null)
            return;

        if (pendingRestore && !waveRaiderLocations.isEmpty()) {
            AbstractRaidWrapper nmsRaid = getCurrentNMSRaid();
            for (Raider raider : new ArrayList<>(currentRaid.getRaiders())) {
                if (!raider.getPersistentDataContainer().has(memberKey, PersistentDataType.INTEGER)) {
                    if (nmsRaid != null && !nmsRaid.isEmpty()) {
                        nmsRaid.removeFromRaid(VersionUtil.getCraftRaiderWrapper(raider).getHandle(), true);
                    }
                    removeRaiderAndMount(raider);
                }
            }
            return;
        }

        List<Raider> raiders = new ArrayList<>(currentRaid.getRaiders());
        Location center = getCenter();
        Location loc = getWaveSpawnLocation(vanillaSpawnLoc);

        if (loc == null || isInvalidWaveSpawn(center, loc)) {
            loc = getRandomSpawnLocation(center);
        }

        AbstractRaidWrapper nmsRaid = getCurrentNMSRaid();

        if (isInvalidWaveSpawn(center, loc)) {
            for (Raider raider : raiders) {
                if (nmsRaid != null && !nmsRaid.isEmpty()) {
                    nmsRaid.removeFromRaid(VersionUtil.getCraftRaiderWrapper(raider).getHandle(), true);
                }
                removeRaiderAndMount(raider);
            }
            if (!isLastWave()) {
                triggerNextWave();
            }
            return;
        }

        List<Raider> newRaiders = new ArrayList<>();
        List<Entity> nonRaiderVehicles = new ArrayList<>();
        waveRaiderLocations.clear();

        if (shouldSpawnArchIllager()) {
            ArchIllagerManager archManager = (ArchIllagerManager) plugin.getMobManagers().getMobManager(ArchIllager.class);
            AdjudicatorManager adjManager = (AdjudicatorManager) plugin.getMobManagers().getMobManager(Adjudicator.class);
            VanillaRaiderManager vanillaManager = (VanillaRaiderManager) plugin.getMobManagers().getMobManager(VanillaRaider.class);

            List<com.solarrabbit.largeraids.raid.mob.Raider> secretWaveMobs = new ArrayList<>(5);
            secretWaveMobs.add(archManager.spawn(loc));
            secretWaveMobs.add(adjManager.spawnRider(loc));
            secretWaveMobs.add(adjManager.spawnRider(loc));

            for (int i = 0; i < 2; i++) {
                VanillaRiderRaider patrolRider = vanillaManager.spawnRider(loc, EntityType.PILLAGER);
                Raider pillager = patrolRider.getBukkitEntity();
                pillager.setPatrolLeader(true);
                EntityEquipment equip = pillager.getEquipment();
                if (equip != null) {
                    equip.setHelmet(OMINOUS_BANNER_TEMPLATE.clone());
                    equip.setHelmetDropChance(1.0f);
                }
                secretWaveMobs.add(patrolRider);
            }

            for (com.solarrabbit.largeraids.raid.mob.Raider entity : secretWaveMobs) {
                registerSpawnedWaveEntity(entity, nmsRaid, newRaiders, nonRaiderVehicles);
            }
        } else {
            for (Map.Entry<Function<Location, ? extends com.solarrabbit.largeraids.raid.mob.Raider>, Integer> kv : raidConfig
                    .getRaiders().getWaveMobs(this.currentWave).entrySet()) {
                int count = kv.getValue();
                Function<Location, ? extends com.solarrabbit.largeraids.raid.mob.Raider> supplier = kv.getKey();
                for (int i = 0; i < count; i++) {
                    com.solarrabbit.largeraids.raid.mob.Raider entity = supplier.apply(loc);
                    registerSpawnedWaveEntity(entity, nmsRaid, newRaiders, nonRaiderVehicles);
                }
            }
        }

        if (raidTarget != null) {
            setRaiderTarget();
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Raider raider : newRaiders)
                raider.setInvulnerable(false);
            for (Entity vehicle : nonRaiderVehicles)
                vehicle.setInvulnerable(false);
        }, INVULNERABLE_TICKS);

        for (Raider raider : raiders) {
            nmsRaid.removeFromRaid(VersionUtil.getCraftRaiderWrapper(raider).getHandle(), true);
            removeRaiderAndMount(raider);
        }

        plugin.getRaidManager().saveRaids();
    }

    private void registerSpawnedWaveEntity(com.solarrabbit.largeraids.raid.mob.Raider entity,
                                           AbstractRaidWrapper nmsRaid, List<Raider> newRaiders, List<Entity> nonRaiderVehicles) {
        Raider bukkitEntity = (Raider) entity.getBukkitEntity();
        bukkitEntity.getPersistentDataContainer().set(memberKey, PersistentDataType.INTEGER, this.currentWave);
        nmsRaid.joinRaid(2, VersionUtil.getCraftRaiderWrapper(bukkitEntity).getHandle(), null, true);
        bukkitEntity.setInvulnerable(true);
        newRaiders.add(bukkitEntity);
        waveRaiderLocations.put(bukkitEntity.getUniqueId(), bukkitEntity.getLocation());

        if (entity instanceof RiderRaider riderRaider) {
            Entity vehicle = riderRaider.getVehicle();
            if (vehicle instanceof Raider ravager) {
                ravager.getPersistentDataContainer().set(memberKey, PersistentDataType.INTEGER, this.currentWave);
                nmsRaid.joinRaid(2, VersionUtil.getCraftRaiderWrapper(ravager).getHandle(), null, true);
                ravager.setInvulnerable(true);
                newRaiders.add(ravager);
                waveRaiderLocations.put(ravager.getUniqueId(), ravager.getLocation());
            } else if (vehicle != null) {
                vehicle.getPersistentDataContainer().set(memberKey, PersistentDataType.INTEGER, this.currentWave);
                vehicle.setInvulnerable(true);
                nonRaiderVehicles.add(vehicle);
            }
        }
    }

    private boolean isInvalidWaveSpawn(Location center, Location loc) {
        if (raidSpawn != null) {
            return false;
        }
        if (center == null || loc == null || center.getWorld() != loc.getWorld()) {
            return true;
        }
        double dx = loc.getX() - center.getX();
        double dz = loc.getZ() - center.getZ();
        double horizontalDistSq = dx * dx + dz * dz;

        return horizontalDistSq >= OUT_OF_BOUNDS_SQ || horizontalDistSq <= CENTER_GLITCH_SQ;
    }

    private void removeRaiderAndMount(Raider raider) {
        waveRaiderLocations.remove(raider.getUniqueId());
        Entity vehicle = raider.getVehicle();
        if (vehicle != null) {
            waveRaiderLocations.remove(vehicle.getUniqueId());
            cleanupGhostArmorStands(vehicle);
            if (vehicle.getType() == EntityType.HORSE) {
                vehicle.remove();
            }
        }
        cleanupGhostArmorStands(raider);
        raider.remove();
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

    public void skipWave() {
        if (isLastWave())
            return;
        else if (!isLoading() && currentRaid != null) {
            for (Raider raider : new ArrayList<>(currentRaid.getRaiders())) {
                removeRaiderAndMount(raider);
            }
        }
        triggerNextWave();
    }

    public void stopRaid() {
        releaseChunkTickets();
        waveRaiderLocations.clear();
        Location center = getCenter();
        AbstractRaidWrapper nmsRaid = this.currentNMSRaid;
        Raid raidToClean = this.currentRaid;
        setRaid(null);

        if (nmsRaid != null && !nmsRaid.isEmpty()) {
            nmsRaid.stop();
            if (center.getWorld() != null) {
                AbstractWorldServerWrapper level = VersionUtil.getCraftWorldWrapper(center.getWorld()).getHandle();
                level.getRaids().removeRaid(nmsRaid);
                level.getRaids().setDirty();
            }
        }
        if (raidToClean != null) {
            for (Raider raider : new ArrayList<>(raidToClean.getRaiders())) {
                removeRaiderAndMount(raider);
            }
        }
    }

    void announceVictory() {
        Sound sound = raidConfig.getSounds().getVictorySound();
        if (sound != null)
            playSoundToPlayersInRadius(sound);
        AbstractRaidWrapper nmsRaid = getCurrentNMSRaid();
        if (nmsRaid != null && !nmsRaid.isEmpty()) {
            nmsRaid.getHeroesOfTheVillage().clear();
        }
        for (UUID uuid : playerDamage.keySet())
            Optional.ofNullable(Bukkit.getPlayer(uuid)).filter(this::shouldAwardPlayer).ifPresent(this::awardPlayer);
    }

    void announceDefeat() {
        Sound sound = raidConfig.getSounds().getDefeatSound();
        if (sound != null)
            playSoundToPlayersInRadius(sound);
    }

    public void applyGlowing() {
        if (currentRaid == null) return;
        ArchIllagerManager archManager = (ArchIllagerManager) plugin.getMobManagers().getMobManager(ArchIllager.class);
        for (Raider raider : currentRaid.getRaiders()) {
            if (raider instanceof Illusioner ill && archManager != null
                    && archManager.isArchIllager(ill) && archManager.hasActiveClones(ill)) {
                continue;
            }
            raider.addPotionEffect(GLOW_EFFECT);
        }
    }

    public Location getCenter() {
        if (currentRaid != null) {
            return currentRaid.getLocation();
        }
        return savedCenterLoc != null ? savedCenterLoc : startLoc;
    }

    public int getRaidOmenLevel() {
        return omenLevel;
    }

    public int getCurrentWave() {
        return currentWave;
    }

    public int getTotalWaves() {
        return shouldSpawnArchIllager() ? maxTotalWaves + 1 : totalWaves;
    }

    public boolean isLastWave() {
        int effectiveFinalWave = canUnlockSecretWave() ? (maxTotalWaves + 1) : totalWaves;
        return currentWave >= effectiveFinalWave;
    }

    public boolean isActive() {
        return currentNMSRaid != null && currentNMSRaid.isActive();
    }

    public boolean areRaidersOutlined() {
        return raidersOutlined;
    }

    public void setRaidersOutlined(boolean raidersOutlined) {
        this.raidersOutlined = raidersOutlined;
        plugin.getRaidManager().saveRaids();
    }

    public boolean isLoading() {
        return currentNMSRaid == null || !currentNMSRaid.hasFirstWaveSpawned() || currentNMSRaid.isBetweenWaves();
    }

    public boolean firstWaveSpawned() {
        return currentNMSRaid != null && currentNMSRaid.hasFirstWaveSpawned();
    }

    public int getTotalRaidersAlive() {
        return currentNMSRaid == null ? 0 : currentNMSRaid.getTotalRaidersAlive();
    }

    void absorbOmenLevel(int level) {
        omenLevel = Math.min(this.maxTotalWaves, this.omenLevel + level);
        totalWaves = Math.max(5, omenLevel);
        plugin.getRaidManager().saveRaids();
    }

    public Set<Player> getPlayersInRadius() {
        Location center = getCenter();
        World world = center.getWorld();
        if (world == null) return new HashSet<>();

        List<Player> worldPlayers = world.getPlayers();
        Set<Player> set = new HashSet<>(worldPlayers.size());
        for (Player player : worldPlayers) {
            if (player.getLocation().distanceSquared(center) <= RADIUS_SQ) {
                set.add(player);
            }
        }
        return set;
    }

    public Map<UUID, Integer> getPlayerKills() {
        return playerKills;
    }

    public boolean releaseOmen() {
        if (currentRaid == null || currentRaid.getBadOmenLevel() <= VANILLA_RAID_OMEN_LEVEL)
            return false;
        currentRaid.setBadOmenLevel(VANILLA_RAID_OMEN_LEVEL);
        return true;
    }

    public boolean isSimilar(Raid raid) {
        if (this.currentNMSRaid == null || raid == null)
            return false;
        if (this.currentRaid == raid)
            return true;
        return this.currentNMSRaid.equals(VersionUtil.getCraftRaidWrapper(raid).getHandle());
    }

    void incrementPlayerKill(Player player) {
        playerKills.merge(player.getUniqueId(), 1, Integer::sum);
    }

    void incrementPlayerDamage(Player player, double damage) {
        playerDamage.merge(player.getUniqueId(), damage, Double::sum);
    }

    private void broadcastWave() {
        boolean secretBossWave = shouldSpawnArchIllager();
        boolean configuredFinalWave = (currentWave == totalWaves);

        Title waveTitle = null;
        Component waveMessage = null;

        if (secretBossWave) {
            waveTitle = Title.title(
                    ChatColorUtil.parse("<blue><bold>FINAL WAVE?"),
                    Component.empty(),
                    WAVE_TITLE_TIMES
            );
            String dialogue = ThreadLocalRandom.current().nextBoolean()
                    ? "<blue><bold>???: <aqua>This is NOT over!"
                    : "<blue><bold>???: <aqua>Guess I'll have to do everything here myself.";
            waveMessage = ChatColorUtil.parse(dialogue);
        } else {
            if (raidConfig.isTitleEnabled()) {
                String titleStr = configuredFinalWave
                        ? raidConfig.getFinalWaveTitle()
                        : raidConfig.getDefaultWaveTitle(currentWave);
                if (titleStr != null) {
                    waveTitle = Title.title(ChatColorUtil.parse(titleStr), Component.empty(), WAVE_TITLE_TIMES);
                }
            }
            if (raidConfig.isMessageEnabled()) {
                String msgStr = configuredFinalWave
                        ? raidConfig.getFinalWaveMessage()
                        : raidConfig.getDefaultWaveMessage(currentWave);
                if (msgStr != null) {
                    waveMessage = ChatColorUtil.parse(msgStr);
                }
            }
        }

        for (Player player : getPlayersInRadius()) {
            if (waveTitle != null) {
                player.showTitle(waveTitle);
            }
            if (waveMessage != null) {
                player.sendMessage(waveMessage);
            }
        }
    }

    private boolean shouldAwardPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        boolean hasMinKills = Optional.ofNullable(playerKills.get(uuid)).orElse(0) >= rewardsConfig
                .getMinRaiderKills();
        boolean hasMinDamage = Optional.ofNullable(playerDamage.get(uuid)).orElse(0.0) >= rewardsConfig
                .getMinDamageDeal();
        return hasMinKills && hasMinDamage;
    }

    private void awardPlayer(Player player) {
        int level = Math.min(rewardsConfig.getHeroLevel(), omenLevel);
        int duration = rewardsConfig.getHeroDuration() * 60 * 20;
        if (level > 0)
            player.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, duration, level - 1));

        String rewardMsg = rewardsConfig.getMessage();
        if (rewardMsg != null && !rewardMsg.isEmpty()) {
            player.sendMessage(ChatColorUtil.parse(rewardMsg));
        }

        player.getInventory().addItem(rewardsConfig.getItems())
                .forEach((i, item) -> player.getWorld().dropItem(player.getLocation(), item));

        for (String command : rewardsConfig.getCommands())
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("<player>", player.getName())
                    .replace("<omen>", String.valueOf(getRaidOmenLevel())));
    }

    void setRaid(Raid raid) {
        this.currentRaid = raid;
        this.currentNMSRaid = (raid == null) ? null : VersionUtil.getCraftRaidWrapper(raid).getHandle();
    }

    private void playSoundToPlayersInRadius(Sound sound) {
        getPlayersInRadius().forEach(player -> player.playSound(player.getLocation(), sound, 50, 1));
    }

    private Location getWaveSpawnLocation(Location vanillaSpawnLoc) {
        if (raidSpawn != null)
            return raidSpawn;

        if (vanillaSpawnLoc != null)
            return vanillaSpawnLoc;

        if (this.currentRaid != null) {
            List<Raider> list = this.currentRaid.getRaiders();
            if (!list.isEmpty()) {
                return list.get(0).getLocation();
            }
        }
        return getRandomSpawnLocation(getCenter());
    }

    private Location getRandomSpawnLocation(Location center) {
        World world = center.getWorld();
        if (world == null) return center;

        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 50; i++) {
            double angle = random.nextDouble(2 * Math.PI);
            double distance = 48.0 + random.nextDouble(16.0);

            int x = (int) Math.floor(center.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(center.getZ() + Math.sin(angle) * distance);

            int groundY = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Block ground = world.getBlockAt(x, groundY, z);
            Block feet = world.getBlockAt(x, groundY + 1, z);
            Block head = world.getBlockAt(x, groundY + 2, z);

            boolean validGround = (ground.getType().isSolid() || ground.getType() == Material.SNOW) && !ground.isLiquid();
            if (validGround && feet.isPassable() && !feet.isLiquid() && head.isPassable() && !head.isLiquid()) {
                return new Location(world, x + 0.5, groundY + 1.0, z + 0.5);
            }
        }
        return center;
    }

    private void prepareLastWave() {
        int withoutBonus = getDefaultWaveNumber(getCenter().getWorld());
        if (currentNMSRaid != null && !currentNMSRaid.isEmpty()) {
            currentNMSRaid.setGroupsSpawned(withoutBonus);
        }
    }

    AbstractRaidWrapper getCurrentNMSRaid() {
        return currentNMSRaid;
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
        plugin.getRaidManager().saveRaids();
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
        plugin.getRaidManager().saveRaids();
    }

    void setRaiderTarget() {
        if (currentRaid == null) return;
        AbstractBlockPositionWrapper target = raidTarget == null ? null
                : VersionUtil.getBlockPositionWrapper(raidTarget);
        for (Raider raider : currentRaid.getRaiders()) {
            AbstractRaiderWrapper wrapper = VersionUtil.getCraftRaiderWrapper(raider).getHandle();
            wrapper.setRaiderTarget(target, raidTargetRadius, raidTargetNavSpeed);
        }
    }

    public boolean addAttackGoal(int prio, boolean mustSee, Class<?> entityClass) {
        if (currentRaid == null) return false;
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
        AbstractRaidWrapper raid = null;
        try {
            raid = raids.createOrExtendRaid(abstractPlayer);
        } catch (NullPointerException ignored) {
        }
        raids.setDirty();

        if (raid == null || raid.isEmpty()) {
            AbstractBlockPositionWrapper blkPos = VersionUtil.getBlockPositionWrapper(location.getX(), location.getY(),
                    location.getZ());
            raid = level.getRaidAt(blkPos);
        }
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

    private static ItemStack createOminousBanner() {
        ItemStack banner = new ItemStack(Material.WHITE_BANNER);
        BannerMeta meta = (BannerMeta) banner.getItemMeta();
        if (meta != null) {
            meta.addPattern(new Pattern(DyeColor.CYAN, PatternType.RHOMBUS));
            meta.addPattern(new Pattern(DyeColor.LIGHT_GRAY, PatternType.STRIPE_BOTTOM));
            meta.addPattern(new Pattern(DyeColor.GRAY, PatternType.STRIPE_CENTER));
            meta.addPattern(new Pattern(DyeColor.LIGHT_GRAY, PatternType.BORDER));
            meta.addPattern(new Pattern(DyeColor.BLACK, PatternType.STRIPE_MIDDLE));
            meta.addPattern(new Pattern(DyeColor.LIGHT_GRAY, PatternType.HALF_HORIZONTAL));
            meta.addPattern(new Pattern(DyeColor.LIGHT_GRAY, PatternType.CIRCLE));
            meta.addPattern(new Pattern(DyeColor.BLACK, PatternType.BORDER));
            meta.displayName(ChatColorUtil.parse("<gold><italic>Ominous Banner"));
            banner.setItemMeta(meta);
        }
        banner.setData(DataComponentTypes.TOOLTIP_DISPLAY,
                TooltipDisplay.tooltipDisplay().addHiddenComponents(DataComponentTypes.BANNER_PATTERNS).build());
        return banner;
    }

    // --- Persistence & Restart Restoration ---

    private void addChunkTicket(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        Chunk chunk = loc.getChunk();
        chunk.load();
        if (chunk.addPluginChunkTicket(plugin)) {
            ticketedChunks.add(chunk);
        }
    }

    private void releaseChunkTickets() {
        for (Chunk chunk : ticketedChunks) {
            chunk.removePluginChunkTicket(plugin);
        }
        ticketedChunks.clear();
    }

    void beginRestore(Location centerLoc) {
        this.savedCenterLoc = centerLoc;
        this.pendingRestore = true;
        this.restoreTicksWaited = 0;

        addChunkTicket(centerLoc);
        for (Location loc : waveRaiderLocations.values()) {
            addChunkTicket(loc);
        }

        AbstractRaidWrapper nmsRaid = getNMSRaidAtCenter();
        if (nmsRaid.isEmpty()) {
            RaidManager manager = plugin.getRaidManager();
            manager.setIdle();
            nmsRaid = createRaid(centerLoc);
            manager.setActive();
        }
        if (!nmsRaid.isEmpty()) {
            setRaid(VersionUtil.getCraftRaidWrapper(nmsRaid).getRaid());
            if (isLastWave()) {
                prepareLastWave();
            }
        }
    }

    void tickRestoreCheck() {
        if (!pendingRestore) return;
        restoreTicksWaited++;

        boolean allEntitiesLoaded = true;
        for (Chunk chunk : ticketedChunks) {
            if (!chunk.isEntitiesLoaded()) {
                allEntitiesLoaded = false;
                break;
            }
        }

        if (allEntitiesLoaded || restoreTicksWaited >= 60) {
            completeRestore();
        }
    }

    private void completeRestore() {
        RaidManager manager = plugin.getRaidManager();
        Location center = getCenter();

        AbstractRaidWrapper nmsRaid = getCurrentNMSRaid();
        if (nmsRaid == null || nmsRaid.isEmpty()) {
            manager.setIdle();
            nmsRaid = createRaid(center);
            manager.setActive();
            if (!nmsRaid.isEmpty()) {
                setRaid(VersionUtil.getCraftRaidWrapper(nmsRaid).getRaid());
                if (isLastWave()) {
                    prepareLastWave();
                }
            }
        }

        Set<Raider> restoredRaiders = new HashSet<>();

        for (UUID uuid : new HashSet<>(waveRaiderLocations.keySet())) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity instanceof Raider raider && !raider.isDead() && raider.isValid()) {
                restoredRaiders.add(raider);
            }
        }

        if (center.getWorld() != null) {
            Collection<Raider> nearbyRaiders = center.getWorld().getNearbyEntitiesByType(
                    Raider.class, center, RADIUS + 32, RADIUS + 32, RADIUS + 32);
            for (Raider raider : nearbyRaiders) {
                if (!raider.isDead() && raider.isValid()) {
                    if (raider.getPersistentDataContainer().has(memberKey, PersistentDataType.INTEGER)
                            || manager.isCustomMobEntity(raider)) {
                        restoredRaiders.add(raider);
                    }
                }
            }
        }

        if (!restoredRaiders.isEmpty() && nmsRaid != null && !nmsRaid.isEmpty()) {
            waveRaiderLocations.clear();
            for (Raider raider : restoredRaiders) {
                raider.setInvulnerable(false);
                raider.getPersistentDataContainer().set(memberKey, PersistentDataType.INTEGER, this.currentWave);
                nmsRaid.joinRaid(2, VersionUtil.getCraftRaiderWrapper(raider).getHandle(), null, true);
                waveRaiderLocations.put(raider.getUniqueId(), raider.getLocation());
                manager.restoreCustomRaiderState(raider, center);

                Entity vehicle = raider.getVehicle();
                if (vehicle instanceof Raider ravager && !ravager.isDead() && ravager.isValid()) {
                    ravager.setInvulnerable(false);
                    ravager.getPersistentDataContainer().set(memberKey, PersistentDataType.INTEGER, this.currentWave);
                    nmsRaid.joinRaid(2, VersionUtil.getCraftRaiderWrapper(ravager).getHandle(), null, true);
                    waveRaiderLocations.put(ravager.getUniqueId(), ravager.getLocation());
                    manager.restoreCustomRaiderState(ravager, center);
                } else if (vehicle != null) {
                    vehicle.setInvulnerable(false);
                }
            }

            if (currentRaid != null) {
                for (Raider raider : new ArrayList<>(currentRaid.getRaiders())) {
                    if (!restoredRaiders.contains(raider)
                            && !raider.getPersistentDataContainer().has(memberKey, PersistentDataType.INTEGER)
                            && !manager.isCustomMobEntity(raider)) {
                        nmsRaid.removeFromRaid(VersionUtil.getCraftRaiderWrapper(raider).getHandle(), true);
                        removeRaiderAndMount(raider);
                    }
                }
            }

            if (raidTarget != null) {
                setRaiderTarget();
            }
        } else if (nmsRaid != null && !nmsRaid.isEmpty() && nmsRaid.hasFirstWaveSpawned()) {
            if (currentRaid != null && !currentRaid.getRaiders().isEmpty()) {
                this.pendingRestore = false;
                releaseChunkTickets();
                manager.setIdle();
                spawnWave(null);
                manager.setActive();
                return;
            } else {
                manager.setIdle();
                setRaid(null);
                nmsRaid.stop();
                AbstractWorldServerWrapper level = VersionUtil.getCraftWorldWrapper(center.getWorld()).getHandle();
                level.getRaids().removeRaid(nmsRaid);
                AbstractRaidWrapper freshRaid = createRaid(center);
                if (!freshRaid.isEmpty()) {
                    setRaid(VersionUtil.getCraftRaidWrapper(freshRaid).getRaid());
                    if (isLastWave()) {
                        prepareLastWave();
                    }
                }
                manager.setActive();
            }
        }

        releaseChunkTickets();
        this.pendingRestore = false;
    }

    public void saveToConfig(ConfigurationSection section) {
        Location center = getCenter();
        if (center == null || center.getWorld() == null) return;

        AbstractWorldServerWrapper level = VersionUtil.getCraftWorldWrapper(center.getWorld()).getHandle();
        level.getRaids().setDirty();

        section.set("world", center.getWorld().getName());
        section.set("start.x", startLoc.getX());
        section.set("start.y", startLoc.getY());
        section.set("start.z", startLoc.getZ());

        section.set("center.x", center.getX());
        section.set("center.y", center.getY());
        section.set("center.z", center.getZ());

        section.set("omen-level", omenLevel);
        section.set("total-waves", totalWaves);
        section.set("current-wave", currentWave);
        section.set("raiders-outlined", raidersOutlined);

        if (triggererUuid != null) {
            section.set("triggerer-uuid", triggererUuid.toString());
        }
        section.set("triggerer-deaths", triggererDeaths);

        if (raidSpawn != null && raidSpawn.getWorld() != null) {
            section.set("spawn.world", raidSpawn.getWorld().getName());
            section.set("spawn.x", raidSpawn.getX());
            section.set("spawn.y", raidSpawn.getY());
            section.set("spawn.z", raidSpawn.getZ());
        }

        if (raidTarget != null && raidTarget.getWorld() != null) {
            section.set("target.world", raidTarget.getWorld().getName());
            section.set("target.x", raidTarget.getX());
            section.set("target.y", raidTarget.getY());
            section.set("target.z", raidTarget.getZ());
            section.set("target.radius", raidTargetRadius);
            section.set("target.speed", raidTargetNavSpeed);
        }

        ConfigurationSection killsSec = section.createSection("player-kills");
        playerKills.forEach((uuid, kills) -> killsSec.set(uuid.toString(), kills));

        ConfigurationSection dmgSec = section.createSection("player-damage");
        playerDamage.forEach((uuid, dmg) -> dmgSec.set(uuid.toString(), dmg));

        if (currentRaid != null) {
            for (Raider raider : currentRaid.getRaiders()) {
                if (raider != null && !raider.isDead() && raider.isValid()) {
                    waveRaiderLocations.put(raider.getUniqueId(), raider.getLocation());
                }
            }
        }

        ConfigurationSection raidersSec = section.createSection("raiders");
        for (Map.Entry<UUID, Location> entry : waveRaiderLocations.entrySet()) {
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (entity != null && entity.isDead()) {
                continue;
            }
            Location loc = (entity != null && entity.isValid()) ? entity.getLocation() : entry.getValue();
            if (loc != null && loc.getWorld() != null) {
                String key = entry.getKey().toString();
                raidersSec.set(key + ".x", loc.getX());
                raidersSec.set(key + ".y", loc.getY());
                raidersSec.set(key + ".z", loc.getZ());
            }
        }
    }

    public static Optional<LargeRaid> fromConfig(RaidConfig raidConfig, RewardsConfig rewardsConfig,
                                                 ConfigurationSection section) {
        if (section == null) return Optional.empty();
        String worldName = section.getString("world");
        if (worldName == null) return Optional.empty();
        World world = Bukkit.getWorld(worldName);
        if (world == null) return Optional.empty();

        Location startLoc = new Location(world,
                section.getDouble("start.x"),
                section.getDouble("start.y"),
                section.getDouble("start.z"));
        Location centerLoc = new Location(world,
                section.getDouble("center.x"),
                section.getDouble("center.y"),
                section.getDouble("center.z"));

        int omenLevel = section.getInt("omen-level", 5);
        LargeRaid raid = new LargeRaid(raidConfig, rewardsConfig, startLoc, omenLevel);
        raid.totalWaves = section.getInt("total-waves", raid.totalWaves);
        raid.currentWave = section.getInt("current-wave", 1);
        raid.raidersOutlined = section.getBoolean("raiders-outlined", false);

        String trigUuidStr = section.getString("triggerer-uuid", null);
        if (trigUuidStr != null) {
            try {
                raid.triggererUuid = UUID.fromString(trigUuidStr);
            } catch (IllegalArgumentException ignored) {}
        }
        raid.triggererDeaths = section.getInt("triggerer-deaths", 0);

        if (section.isConfigurationSection("spawn")) {
            World spawnWorld = Bukkit.getWorld(section.getString("spawn.world", worldName));
            if (spawnWorld != null) {
                raid.raidSpawn = new Location(spawnWorld,
                        section.getDouble("spawn.x"),
                        section.getDouble("spawn.y"),
                        section.getDouble("spawn.z"));
            }
        }

        if (section.isConfigurationSection("target")) {
            World targetWorld = Bukkit.getWorld(section.getString("target.world", worldName));
            if (targetWorld != null) {
                raid.raidTarget = new Location(targetWorld,
                        section.getDouble("target.x"),
                        section.getDouble("target.y"),
                        section.getDouble("target.z"));
                raid.raidTargetRadius = section.getDouble("target.radius");
                raid.raidTargetNavSpeed = section.getDouble("target.speed");
            }
        }

        ConfigurationSection killsSec = section.getConfigurationSection("player-kills");
        if (killsSec != null) {
            for (String key : killsSec.getKeys(false)) {
                try {
                    raid.playerKills.put(UUID.fromString(key), killsSec.getInt(key));
                } catch (IllegalArgumentException ignored) {}
            }
        }

        ConfigurationSection dmgSec = section.getConfigurationSection("player-damage");
        if (dmgSec != null) {
            for (String key : dmgSec.getKeys(false)) {
                try {
                    raid.playerDamage.put(UUID.fromString(key), dmgSec.getDouble(key));
                } catch (IllegalArgumentException ignored) {}
            }
        }

        ConfigurationSection raidersSec = section.getConfigurationSection("raiders");
        if (raidersSec != null) {
            for (String key : raidersSec.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    Location rLoc = new Location(world,
                            raidersSec.getDouble(key + ".x"),
                            raidersSec.getDouble(key + ".y"),
                            raidersSec.getDouble(key + ".z"));
                    raid.waveRaiderLocations.put(uuid, rLoc);
                } catch (IllegalArgumentException ignored) {}
            }
        }

        raid.beginRestore(centerLoc);
        return Optional.of(raid);
    }
}