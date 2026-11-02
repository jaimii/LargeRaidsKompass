package com.solarrabbit.largeraids;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import com.solarrabbit.largeraids.PluginLogger.Level;
import com.solarrabbit.largeraids.command.*;
import com.solarrabbit.largeraids.command.completer.*;
import com.solarrabbit.largeraids.config.MiscConfig;
import com.solarrabbit.largeraids.config.PlaceholderConfig;
import com.solarrabbit.largeraids.config.RaidConfig;
import com.solarrabbit.largeraids.config.RewardsConfig;
import com.solarrabbit.largeraids.config.custommobs.CustomMobsConfig;
import com.solarrabbit.largeraids.config.trigger.TriggersConfig;
import com.solarrabbit.largeraids.database.DatabaseAdapter;
import com.solarrabbit.largeraids.misc.BookGenerator;
import com.solarrabbit.largeraids.misc.CustomEggListener;
import com.solarrabbit.largeraids.misc.TraderBookListener;
import com.solarrabbit.largeraids.raid.RaidManager;
import com.solarrabbit.largeraids.raid.mob.manager.MobManagers;
import com.solarrabbit.largeraids.support.Placeholder;
import com.solarrabbit.largeraids.trigger.DropInLavaTriggerListener;
import com.solarrabbit.largeraids.trigger.TimeBombTriggerListener;
import com.solarrabbit.largeraids.trigger.Trigger;
import com.solarrabbit.largeraids.trigger.TriggerManager;
import com.solarrabbit.largeraids.trigger.omen.VillageAbsorbOmenListener;
import com.solarrabbit.largeraids.util.BossBarCreator;
import com.solarrabbit.largeraids.util.CustomEggUtil;
import com.solarrabbit.largeraids.village.BellListener;
import com.solarrabbit.largeraids.village.VillageManager;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class LargeRaids extends JavaPlugin {
    private static final String VERSION = "26.3";

    private YamlConfiguration messages;
    private PluginLogger logger;
    private DatabaseAdapter db;
    private Set<Trigger> registeredTriggerListeners;

    private RaidConfig raidConfig;
    private RewardsConfig rewardsConfig;
    private TriggersConfig triggerConfig;
    private MiscConfig miscConfig;
    private CustomMobsConfig customMobsConfig;

    private Placeholder placeholder;
    private RaidManager raidManager;
    private MobManagers mobManagers;
    private TriggerManager triggerManager;
    private VillageManager villageManager;
    private BossBarCreator bossbarCreator;

    @Override
    public void onEnable() {
        logger = new PluginLogger();

        String serverVersion = Bukkit.getServer().getMinecraftVersion();
        if (!isSupportedVersion(serverVersion)) {
            boolean skipCheck = LargeRaids.class.getResource("/BYPASS_VERSION_CHECK.txt") != null;
            log(String.format("Server version is not supported! Supported Version: %s, Your Version: %s",
                    VERSION, serverVersion), Level.FAIL, false);
            if (!skipCheck) {
                log("You can allow the plugin to run anyways (not recommended) "
                        + "by placing BYPASS_VERSION_CHECK.txt inside the plugin JAR", Level.FAIL);
                return;
            }
            log("Unexpected crashes or errors may occur!", Level.WARN);
        }

        // Initialize bStats metrics
        final int pluginId = 13910;
        new Metrics(this, pluginId);

        saveDefaultConfig();
        db = new DatabaseAdapter(this);
        db.load();

        raidManager = new RaidManager(this);
        raidManager.init();
        getServer().getPluginManager().registerEvents(raidManager, this);

        triggerManager = new TriggerManager(this);
        getServer().getPluginManager().registerEvents(triggerManager, this);

        villageManager = new VillageManager();
        getServer().getPluginManager().registerEvents(new BellListener(this), this);

        bossbarCreator = new BossBarCreator(raidManager);
        bossbarCreator.init(this);
        getServer().getPluginManager().registerEvents(bossbarCreator, this);

        BookGenerator bookGen = new BookGenerator(this.getResource("traderbook.yml"));
        getServer().getPluginManager().registerEvents(new TraderBookListener(bookGen, this), this);
        getServer().getPluginManager().registerEvents(new CustomEggListener(this), this);

        // Register custom mob managers and their event listeners
        mobManagers = new MobManagers();
        mobManagers.getListenerManagers()
                .forEach(manager -> getServer().getPluginManager().registerEvents(manager, this));

        loadCommands();
        loadMessages();
        loadCustomConfigs();

        // Restore any ongoing LargeRaids saved prior to server restart
        Bukkit.getScheduler().runTask(this, () -> {
            if (raidManager != null) {
                raidManager.loadRaids();
            }
        });
    }

    @Override
    public void onDisable() {
        if (raidManager != null) {
            raidManager.saveRaids();
            raidManager.currentRaids.clear();
        }

        if (bossbarCreator != null) {
            bossbarCreator.clearAllBars();
        }

        if (placeholder != null) {
            placeholder.unregister();
        }

        Bukkit.getScheduler().cancelTasks(this);
    }

    private boolean isSupportedVersion(String serverVersion) {
        if (serverVersion == null) return false;
        return serverVersion.equals(VERSION) || serverVersion.startsWith(VERSION + ".");
    }

    public void log(String message, Level level) {
        this.log(message, level, level == Level.FAIL);
    }

    public void log(String message, Level level, boolean disable) {
        if (this.logger != null) {
            this.logger.sendMessage(message, level);
        } else {
            getLogger().info("[" + level.name() + "] " + message);
        }
        if (disable) {
            if (this.logger != null) {
                this.logger.sendMessage("Disabling plugin...", Level.FAIL);
            }
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    public void reload() {
        reloadConfig();
        loadCustomConfigs();
    }

    private void loadCommands() {
        CustomEggUtil eggUtil = new CustomEggUtil(this);
        if (getCommand("lregg") != null) {
            getCommand("lregg").setExecutor(new GiveEggCommand(eggUtil));
            getCommand("lregg").setTabCompleter(new GiveEggTabCompleter());
        }
        if (getCommand("lrstart") != null) {
            getCommand("lrstart").setExecutor(new StartRaidCommand(this));
            getCommand("lrstart").setTabCompleter(new StartStopRaidCommandCompleter(db));
        }
        if (getCommand("lrskip") != null) {
            getCommand("lrskip").setExecutor(new SkipWaveCommand(this));
        }
        if (getCommand("lrstop") != null) {
            getCommand("lrstop").setExecutor(new StopRaidCommand(this));
            getCommand("lrstop").setTabCompleter(new StartStopRaidCommandCompleter(db));
        }
        if (getCommand("lrshow") != null) {
            getCommand("lrshow").setExecutor(new ShowCurrentWaveCommand(this));
        }
        if (getCommand("lrgive") != null) {
            getCommand("lrgive").setExecutor(new GiveSummonItemCommand(this));
        }
        if (getCommand("lrreload") != null) {
            getCommand("lrreload").setExecutor(new ReloadPluginCommand(this));
        }
        if (getCommand("lrcenters") != null) {
            getCommand("lrcenters").setExecutor(new VillageCentresCommand(this));
            getCommand("lrcenters").setTabCompleter(new VillageCentersCommandCompleter(db));
        }
        if (getCommand("lrglow") != null) {
            getCommand("lrglow").setExecutor(new OutlineRaidersCommand(this));
        }
        if (getCommand("lrattack") != null) {
            getCommand("lrattack").setExecutor(new RaiderAttackCommand(this));
        }
        if (getCommand("lrconfig") != null) {
            getCommand("lrconfig").setExecutor(new RaidConfigCommand(this));
            getCommand("lrconfig").setTabCompleter(new RaidConfigCommandCompleter());
        }
        if (getCommand("lrmobcontrol") != null) {
            getCommand("lrmobcontrol").setExecutor(new MobControlCommand(this));
        }
    }

    private void loadCustomConfigs() {
        if (!testConfig()) {
            this.log(this.getMessage("config.integrity-checks-failed"), Level.FAIL);
            return;
        }
        raidConfig = new RaidConfig(getConfig().getConfigurationSection("raid"));
        rewardsConfig = new RewardsConfig(getConfig().getConfigurationSection("rewards"));
        triggerConfig = new TriggersConfig(getConfig().getConfigurationSection("trigger"));
        miscConfig = new MiscConfig(getConfig().getConfigurationSection("miscellaneous"));
        customMobsConfig = new CustomMobsConfig(getConfig().getConfigurationSection("custom-mobs"));

        if (mobManagers != null) {
            mobManagers.loadSettings(customMobsConfig);
        }

        reloadTriggers();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            if (placeholder != null)
                placeholder.unregister();
            PlaceholderConfig placeholderConfig = new PlaceholderConfig(
                    getConfig().getConfigurationSection("placeholder"));
            placeholder = new Placeholder(raidManager, placeholderConfig);
            placeholder.register();
        }
    }

    public String getMessage(String node) {
        return messages != null ? messages.getString(node, "") : "";
    }

    public RaidManager getRaidManager() {
        return raidManager;
    }

    public MobManagers getMobManagers() {
        return mobManagers;
    }

    public TriggerManager getTriggerManager() {
        return triggerManager;
    }

    public VillageManager getVillageManager() {
        return villageManager;
    }

    public DatabaseAdapter getDatabaseAdapter() {
        return db;
    }

    public RaidConfig getRaidConfig() {
        return raidConfig;
    }

    public RewardsConfig getRewardsConfig() {
        return rewardsConfig;
    }

    public TriggersConfig getTriggerConfig() {
        return triggerConfig;
    }

    public MiscConfig getMiscConfig() {
        return miscConfig;
    }

    public CustomMobsConfig getCustomMobsConfig() {
        return customMobsConfig;
    }

    private void reloadTriggers() {
        if (registeredTriggerListeners != null) {
            for (Trigger listener : registeredTriggerListeners) {
                listener.unregisterListener();
            }
        }
        registeredTriggerListeners = new HashSet<>();

        if (triggerConfig.getOmenConfig().isEnabled())
            registerTrigger(new VillageAbsorbOmenListener(this), true);
        if (triggerConfig.getDropInLavaConfig().isEnabled())
            registerTrigger(new DropInLavaTriggerListener(this), true);
        if (triggerConfig.getTimeBombConfig().isEnabled())
            registerTrigger(new TimeBombTriggerListener(this), false);
    }

    private void registerTrigger(Trigger listener, boolean registerEvents) {
        if (registerEvents)
            getServer().getPluginManager().registerEvents(listener, this);
        registeredTriggerListeners.add(listener);
    }

    private void loadMessages() {
        messages = new YamlConfiguration();
        try (InputStream stream = this.getResource("messages.yml")) {
            if (stream != null) {
                messages.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            }
        } catch (IOException | InvalidConfigurationException e) {
            this.log("Unable to load messages!", Level.FAIL);
        }
    }

    private boolean testConfig() {
        boolean pass = true;
        int totalWaves = this.getConfig().getInt("raid.waves");
        int wavesToCheck = totalWaves;
        ConfigurationSection section = this.getConfig().getConfigurationSection("raid.mobs");
        if (section == null) return true;

        for (String mob : section.getKeys(false)) {
            int length = section.getIntegerList(mob).size();
            if (length < totalWaves) {
                this.log(String.format(this.getMessage("config.mob-array-too-short"), mob, totalWaves, length), Level.FAIL, false);
                wavesToCheck = Math.min(length, wavesToCheck);
                pass = false;
            } else if (length > totalWaves) {
                this.log(String.format(this.getMessage("config.mob-array-too-long"), mob, totalWaves, length - totalWaves), Level.WARN);
            }
        }
        for (int i = 0; i < wavesToCheck; i++) {
            final int wave = i;
            int totalRaiders = section.getKeys(false).stream().map(key -> section.getIntegerList(key).get(wave))
                    .reduce(0, Integer::sum);
            if (totalRaiders == 0) {
                this.log(String.format(this.getMessage("config.zero-raider-wave"), wave), Level.FAIL, false);
                pass = false;
            }
        }
        return pass;
    }
}