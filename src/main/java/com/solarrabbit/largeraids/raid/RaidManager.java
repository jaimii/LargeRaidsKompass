package com.solarrabbit.largeraids.raid;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.solarrabbit.largeraids.LargeRaids;
import com.solarrabbit.largeraids.PluginLogger.Level;
import com.solarrabbit.largeraids.event.LargeRaidExtendEvent;
import com.solarrabbit.largeraids.event.LargeRaidTriggerEvent;
import com.solarrabbit.largeraids.nms.AbstractBlockPositionWrapper;
import com.solarrabbit.largeraids.nms.AbstractCraftRaidWrapper;
import com.solarrabbit.largeraids.nms.AbstractCraftWorldWrapper;
import com.solarrabbit.largeraids.nms.AbstractRaidWrapper;
import com.solarrabbit.largeraids.nms.AbstractRaiderWrapper;
import com.solarrabbit.largeraids.raid.mob.Adjudicator;
import com.solarrabbit.largeraids.raid.mob.ArchIllager;
import com.solarrabbit.largeraids.raid.mob.VanillaRaider;
import com.solarrabbit.largeraids.raid.mob.manager.AdjudicatorManager;
import com.solarrabbit.largeraids.raid.mob.manager.ArchIllagerManager;
import com.solarrabbit.largeraids.raid.mob.manager.EvokerGoalUtil;
import com.solarrabbit.largeraids.raid.mob.manager.VanillaRaiderManager;
import com.solarrabbit.largeraids.util.BossBarCreator;
import com.solarrabbit.largeraids.util.VersionUtil;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Raid;
import org.bukkit.Raid.RaidStatus;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Illusioner;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Raider;
import org.bukkit.entity.Ravager;
import org.bukkit.entity.Spellcaster;
import org.bukkit.entity.Vindicator;
import org.bukkit.entity.Witch;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.raid.RaidFinishEvent;
import org.bukkit.event.raid.RaidSpawnWaveEvent;
import org.bukkit.event.raid.RaidStopEvent;
import org.bukkit.event.raid.RaidTriggerEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;

public class RaidManager implements Listener {
    private static final String ACTIVE_RAIDS_FILE = "active_raids.yml";
    private static final PotionEffect GLOW_EFFECT = new PotionEffect(PotionEffectType.GLOWING, 35, 0, false, false);

    public final Set<LargeRaid> currentRaids = new HashSet<>();
    public final Set<AbstractRaidWrapper> outlinedRaids = new HashSet<>();
    private final LargeRaids plugin;
    private final Object saveLock = new Object();

    private final NamespacedKey memberKey;
    private final NamespacedKey adjudicatorKey;
    private final NamespacedKey archIllagerKey;
    private final NamespacedKey bomberKey;
    private final NamespacedKey necromancerKey;
    private final NamespacedKey fireworkKey;
    private final NamespacedKey juggernautKey;
    private final NamespacedKey kingKey;
    private final NamespacedKey raidWitchKey;

    private boolean isIdle;
    private int tickCounter;

    public RaidManager(LargeRaids plugin) {
        this.plugin = plugin;
        this.isIdle = false;
        this.tickCounter = 0;

        this.memberKey = new NamespacedKey(plugin, LargeRaid.RAID_MEMBER_KEY);
        this.adjudicatorKey = new NamespacedKey(plugin, "adjudicator");
        this.archIllagerKey = new NamespacedKey(plugin, "arch_illager");
        this.bomberKey = new NamespacedKey(plugin, "bomber");
        this.necromancerKey = new NamespacedKey(plugin, "necromancer");
        this.fireworkKey = new NamespacedKey(plugin, "firework_pillager");
        this.juggernautKey = new NamespacedKey(plugin, "juggernaut");
        this.kingKey = new NamespacedKey(plugin, "juggernaut_king");
        this.raidWitchKey = new NamespacedKey(plugin, "raid_witch");
    }

    public boolean isIdle() {
        return isIdle;
    }

    public void setIdle() {
        isIdle = true;
    }

    public void setActive() {
        isIdle = false;
    }

    public int getNumOfRegisteredRaids() {
        return currentRaids.size();
    }

    @EventHandler
    private void onSpawn(RaidSpawnWaveEvent evt) {
        Location spawnLoc = null;
        if (evt.getRaiders() != null && !evt.getRaiders().isEmpty()) {
            spawnLoc = evt.getRaiders().get(0).getLocation().clone();
            if (getLargeRaid(evt.getRaid()).isPresent()) {
                for (Raider raider : evt.getRaiders()) {
                    raider.setInvisible(true);
                    raider.setSilent(true);
                    raider.setAI(false);
                }
            }
        }

        final Location finalSpawnLoc = spawnLoc;
        Bukkit.getScheduler().runTask(plugin, () -> getLargeRaid(evt.getRaid()).ifPresent(largeRaid -> {
            setIdle();
            largeRaid.spawnWave(finalSpawnLoc);
            setActive();
        }));
    }

    @EventHandler
    private void onNormalRaidTrigger(RaidTriggerEvent evt) {
        if (evt.getRaid().getBadOmenLevel() != 0)
            return;
        if (isIdle())
            return;
        if (!plugin.getTriggerConfig().canNormalRaid())
            evt.setCancelled(true);
    }

    @EventHandler
    private void onFinish(RaidFinishEvent evt) {
        if (isIdle())
            return;
        Raid raid = evt.getRaid();
        getLargeRaid(raid).ifPresent(largeRaid -> {
            RaidStatus status = raid.getStatus();
            if (status == RaidStatus.VICTORY)
                largeRaid.announceVictory();
            else if (status == RaidStatus.LOSS)
                largeRaid.announceDefeat();
            currentRaids.remove(largeRaid);
            saveRaids();
        });
    }

    @EventHandler
    private void onRaidStop(RaidStopEvent evt) {
        if (isIdle())
            return;
        getLargeRaid(evt.getRaid()).ifPresent(largeRaid -> {
            if (!largeRaid.isPendingRestore()) {
                currentRaids.remove(largeRaid);
                saveRaids();
            }
        });
    }

    @EventHandler
    private void onPlayerDeath(PlayerDeathEvent evt) {
        if (currentRaids.isEmpty())
            return;
        UUID deadUuid = evt.getEntity().getUniqueId();
        boolean changed = false;
        for (LargeRaid raid : currentRaids) {
            if (deadUuid.equals(raid.getTriggererUuid())) {
                raid.incrementTriggererDeath();
                changed = true;
            }
        }
        if (changed) {
            saveRaids();
        }
    }

    @EventHandler
    private void onEntitiesLoad(EntitiesLoadEvent evt) {
        for (Entity entity : evt.getEntities()) {
            if (!(entity instanceof Raider raider) || raider.isDead() || !raider.isValid())
                continue;

            boolean isMember = raider.getPersistentDataContainer().has(memberKey, PersistentDataType.INTEGER);
            boolean isCustom = isCustomMobEntity(raider);
            if (!isMember && !isCustom)
                continue;

            Optional<LargeRaid> matchingRaid = currentRaids.stream()
                    .filter(r -> r.getCenter().getWorld() != null
                            && r.getCenter().getWorld().equals(raider.getWorld())
                            && r.getCenter().distanceSquared(raider.getLocation()) <= 30976.0)
                    .findFirst();

            if (matchingRaid.isPresent()) {
                LargeRaid lr = matchingRaid.get();
                restoreCustomRaiderState(raider, lr.getCenter());
                AbstractRaidWrapper nmsRaid = lr.getCurrentNMSRaid();
                if (nmsRaid != null && !nmsRaid.isEmpty()) {
                    nmsRaid.joinRaid(2, VersionUtil.getCraftRaiderWrapper(raider).getHandle(), null, true);
                }
                if (lr.getRaidTarget() != null) {
                    lr.setRaiderTarget();
                }
            } else {
                restoreCustomRaiderState(raider, raider.getLocation());
            }
        }
    }

    public boolean isCustomMobEntity(Raider raider) {
        PersistentDataContainer pdc = raider.getPersistentDataContainer();
        return pdc.has(adjudicatorKey, PersistentDataType.BYTE)
                || pdc.has(archIllagerKey, PersistentDataType.BYTE)
                || pdc.has(bomberKey, PersistentDataType.BYTE)
                || pdc.has(necromancerKey, PersistentDataType.BYTE)
                || pdc.has(fireworkKey, PersistentDataType.BYTE)
                || pdc.has(juggernautKey, PersistentDataType.BYTE)
                || pdc.has(kingKey, PersistentDataType.BYTE)
                || pdc.has(raidWitchKey, PersistentDataType.BYTE);
    }

    public void restoreCustomRaiderState(Raider raider, Location raidCenter) {
        PersistentDataContainer pdc = raider.getPersistentDataContainer();
        if (raider instanceof Vindicator vindicator
                && pdc.has(adjudicatorKey, PersistentDataType.BYTE)) {
            AdjudicatorManager adjManager = (AdjudicatorManager) plugin.getMobManagers().getMobManager(Adjudicator.class);
            if (adjManager != null) {
                adjManager.restoreAdjudicator(vindicator);
            }
        } else if (raider instanceof Illusioner illusioner
                && pdc.has(archIllagerKey, PersistentDataType.BYTE)) {
            ArchIllagerManager archManager = (ArchIllagerManager) plugin.getMobManagers().getMobManager(ArchIllager.class);
            if (archManager != null) {
                archManager.restoreArchIllager(illusioner);
            }
        } else if (raider instanceof Witch witch) {
            VanillaRaiderManager vanillaManager = (VanillaRaiderManager) plugin.getMobManagers().getMobManager(VanillaRaider.class);
            if (vanillaManager != null) {
                vanillaManager.registerRaidWitch(witch);
            }
        } else if (raider instanceof Spellcaster evoker) {
            boolean isBomber = pdc.has(bomberKey, PersistentDataType.BYTE);
            boolean isNecromancer = pdc.has(necromancerKey, PersistentDataType.BYTE);
            boolean isKing = pdc.has(kingKey, PersistentDataType.BYTE);
            if (isBomber || isNecromancer || isKing) {
                EvokerGoalUtil.injectRaidFleeGoal(evoker, raidCenter);
            }
            if (isKing) {
                BossBarCreator.ensureRaidBossBar(evoker);
            }
        } else if (raider instanceof Ravager ravager
                && pdc.has(juggernautKey, PersistentDataType.BYTE)) {
            BossBarCreator.ensureRaidBossBar(ravager);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onDamage(EntityDamageByEntityEvent evt) {
        if (evt.isCancelled() || currentRaids.isEmpty())
            return;
        Entity killed = evt.getEntity();
        if (!(killed instanceof Raider raider))
            return;
        Entity attacker = evt.getDamager();
        Player damager;
        switch (evt.getCause()) {
            case ENTITY_ATTACK:
            case ENTITY_SWEEP_ATTACK:
                if (!(attacker instanceof Player p))
                    return;
                damager = p;
                break;
            case PROJECTILE:
                Projectile projectile = (Projectile) attacker;
                ProjectileSource source = projectile.getShooter();
                if (!(source instanceof Player p))
                    return;
                damager = p;
                break;
            default:
                return;
        }

        AbstractRaiderWrapper wrapper = VersionUtil.getCraftRaiderWrapper(raider).getHandle();
        AbstractRaidWrapper nmsRaid = wrapper.getCurrentRaid();
        if (nmsRaid.isEmpty())
            return;
        AbstractCraftRaidWrapper craftRaid = VersionUtil.getCraftRaidWrapper(nmsRaid);
        Optional<LargeRaid> lr = getLargeRaid(craftRaid.getRaid());
        lr.ifPresent(r -> {
            r.incrementPlayerDamage(damager, Math.min(raider.getHealth(), evt.getFinalDamage()));
            if (raider.getHealth() - evt.getFinalDamage() <= 0)
                r.incrementPlayerKill(damager);
        });
    }

    public void init() {
        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tick, 0, 1);
    }

    private void tick() {
        tickCounter++;
        boolean refreshGlow = (tickCounter % 20 == 0);

        if (!currentRaids.isEmpty()) {
            for (LargeRaid largeRaid : new HashSet<>(currentRaids)) {
                if (largeRaid.isPendingRestore()) {
                    largeRaid.tickRestoreCheck();
                    continue;
                }
                if (largeRaid.isActive() && largeRaid.getTotalRaidersAlive() == 0 && largeRaid.firstWaveSpawned()
                        && !largeRaid.isLastWave()) {
                    setIdle();
                    largeRaid.triggerNextWave();
                    setActive();
                } else if (refreshGlow && largeRaid.areRaidersOutlined()) {
                    largeRaid.applyGlowing();
                }
            }
        }

        if (refreshGlow && !outlinedRaids.isEmpty()) {
            Iterator<AbstractRaidWrapper> itr = outlinedRaids.iterator();
            while (itr.hasNext()) {
                AbstractRaidWrapper wrapper = itr.next();
                Raid raid = VersionUtil.getCraftRaidWrapper(wrapper).getRaid();

                if (raid.getStatus() != RaidStatus.ONGOING) {
                    itr.remove();
                } else {
                    for (Raider raider : raid.getRaiders()) {
                        raider.addPotionEffect(GLOW_EFFECT);
                    }
                }
            }
        }
    }

    public Optional<LargeRaid> getLargeRaid(Location location) {
        if (currentRaids.isEmpty() || location == null || location.getWorld() == null)
            return Optional.empty();
        AbstractBlockPositionWrapper blockPos = VersionUtil.getBlockPositionWrapper(location);
        AbstractCraftWorldWrapper craftWorld = VersionUtil.getCraftWorldWrapper(location.getWorld());
        AbstractRaidWrapper nmsRaid = craftWorld.getHandle().getRaidAt(blockPos);
        if (nmsRaid.isEmpty())
            return Optional.empty();
        AbstractCraftRaidWrapper craftRaid = VersionUtil.getCraftRaidWrapper(nmsRaid);
        return getLargeRaid(craftRaid.getRaid());
    }

    public Optional<LargeRaid> getLargeRaid(Raid raid) {
        if (currentRaids.isEmpty() || raid == null)
            return Optional.empty();
        for (LargeRaid largeRaid : currentRaids) {
            if (largeRaid.isSimilar(raid)) {
                return Optional.of(largeRaid);
            }
        }
        return Optional.empty();
    }

    public Optional<Raid> getRaid(Location location) {
        if (location == null || location.getWorld() == null)
            return Optional.empty();
        AbstractBlockPositionWrapper blockPos = VersionUtil.getBlockPositionWrapper(location);
        AbstractCraftWorldWrapper world = VersionUtil.getCraftWorldWrapper(location.getWorld());
        AbstractRaidWrapper raid = world.getHandle().getRaidAt(blockPos);
        return Optional.of(raid).filter(r -> !r.isEmpty()).map(VersionUtil::getCraftRaidWrapper)
                .map(AbstractCraftRaidWrapper::getRaid);
    }

    public void createRaid(Location location, int omenLevel) {
        Optional<LargeRaid> currentRaid = getLargeRaid(location);
        if (currentRaid.isPresent())
            return;
        LargeRaid largeRaid = new LargeRaid(plugin.getRaidConfig(), plugin.getRewardsConfig(), location, omenLevel);

        CommandSender triggerer = plugin.getTriggerManager().getLastTriggerer();
        if (triggerer instanceof Player p) {
            largeRaid.setTriggererUuid(p.getUniqueId());
        } else if (location.getWorld() != null) {
            location.getWorld().getPlayers().stream()
                    .min((p1, p2) -> Double.compare(p1.getLocation().distanceSquared(location), p2.getLocation().distanceSquared(location)))
                    .ifPresent(nearest -> largeRaid.setTriggererUuid(nearest.getUniqueId()));
        }

        setIdle();
        if (largeRaid.startRaid()) {
            LargeRaidTriggerEvent evt = new LargeRaidTriggerEvent(largeRaid);
            Bukkit.getPluginManager().callEvent(evt);
            if (evt.isCancelled()) {
                largeRaid.stopRaid();
            } else {
                currentRaids.add(largeRaid);
                saveRaids();
            }
        }
        setActive();
    }

    public void extendRaid(LargeRaid raid, int omenLevel) {
        int oldLevel = raid.getRaidOmenLevel();
        raid.absorbOmenLevel(omenLevel);
        int newLevel = raid.getRaidOmenLevel();
        if (newLevel != oldLevel)
            Bukkit.getPluginManager().callEvent(new LargeRaidExtendEvent(raid, oldLevel, newLevel));
    }

    public void saveRaids() {
        final File file = new File(plugin.getDataFolder(), ACTIVE_RAIDS_FILE);

        if (currentRaids.isEmpty()) {
            if (!plugin.isEnabled() || !Bukkit.isPrimaryThread()) {
                synchronized (saveLock) {
                    if (file.exists()) {
                        file.delete();
                    }
                }
            } else {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    synchronized (saveLock) {
                        if (file.exists()) {
                            file.delete();
                        }
                    }
                });
            }
            return;
        }

        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection raidsSection = config.createSection("raids");
        int index = 0;
        for (LargeRaid raid : currentRaids) {
            if (raid != null) {
                raid.saveToConfig(raidsSection.createSection("raid_" + (index++)));
            }
        }
        final String yamlData = config.saveToString();

        if (!plugin.isEnabled() || !Bukkit.isPrimaryThread()) {
            writeYamlToDisk(file, yamlData);
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> writeYamlToDisk(file, yamlData));
        }
    }

    private void writeYamlToDisk(File file, String yamlData) {
        synchronized (saveLock) {
            try {
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                Files.writeString(file.toPath(), yamlData, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            } catch (IOException e) {
                plugin.log("Failed to save active LargeRaids to disk!", Level.WARN);
                e.printStackTrace();
            }
        }
    }

    public void loadRaids() {
        File file = new File(plugin.getDataFolder(), ACTIVE_RAIDS_FILE);
        if (!file.exists())
            return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection raidsSection = config.getConfigurationSection("raids");
        if (raidsSection == null)
            return;

        int count = 0;
        for (String key : raidsSection.getKeys(false)) {
            ConfigurationSection sec = raidsSection.getConfigurationSection(key);
            Optional<LargeRaid> restored = LargeRaid.fromConfig(plugin.getRaidConfig(), plugin.getRewardsConfig(), sec);
            if (restored.isPresent()) {
                currentRaids.add(restored.get());
                count++;
            }
        }

        if (count > 0) {
            plugin.log("Restored " + count + " ongoing LargeRaid(s) from previous session.", Level.SUCCESS);
        }
    }
}