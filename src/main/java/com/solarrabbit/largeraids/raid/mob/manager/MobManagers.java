package com.solarrabbit.largeraids.raid.mob.manager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.solarrabbit.largeraids.config.custommobs.CustomMobsConfig;
import com.solarrabbit.largeraids.raid.mob.Adjudicator;
import com.solarrabbit.largeraids.raid.mob.AdjudicatorRider;
import com.solarrabbit.largeraids.raid.mob.ArchIllager;
import com.solarrabbit.largeraids.raid.mob.Bomber;
import com.solarrabbit.largeraids.raid.mob.FireworkPillager;
import com.solarrabbit.largeraids.raid.mob.KingRaider;
import com.solarrabbit.largeraids.raid.mob.MythicRaider;
import com.solarrabbit.largeraids.raid.mob.Necromancer;
import com.solarrabbit.largeraids.raid.mob.Raider;
import com.solarrabbit.largeraids.raid.mob.VanillaRaider;
import com.solarrabbit.largeraids.raid.mob.VanillaRiderRaider;

import org.bukkit.event.Listener;

public class MobManagers {
    public final Map<Class<? extends Raider>, MobManager> managers;

    public MobManagers() {
        this.managers = new HashMap<>();
        init();
    }

    private void init() {
        managers.put(FireworkPillager.class, new FireworkPillagerManager());
        managers.put(Bomber.class, new BomberManager());
        managers.put(Necromancer.class, new NecromancerManager());
        managers.put(KingRaider.class, new KingRaiderManager());
        managers.put(ArchIllager.class, new ArchIllagerManager());
        managers.put(MythicRaider.class, new MythicRaiderManager());

        AdjudicatorManager adjudicatorManager = new AdjudicatorManager();
        managers.put(Adjudicator.class, adjudicatorManager);
        managers.put(AdjudicatorRider.class, adjudicatorManager);

        VanillaRaiderManager vanillaRaiderManager = new VanillaRaiderManager();
        managers.put(VanillaRaider.class, vanillaRaiderManager);
        managers.put(VanillaRiderRaider.class, vanillaRaiderManager);
    }

    public void loadSettings(CustomMobsConfig config) {
        managers.forEach((c, m) -> m.loadSettings(config));
    }

    public MobManager getMobManager(Class<? extends Raider> raiderClass) {
        return managers.get(raiderClass);
    }

    public List<Listener> getListenerManagers() {
        return managers.values().stream().filter(Listener.class::isInstance)
                .map(Listener.class::cast)
                .distinct()
                .collect(Collectors.toList());
    }
}