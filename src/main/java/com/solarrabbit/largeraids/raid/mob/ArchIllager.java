package com.solarrabbit.largeraids.raid.mob;

import org.bukkit.boss.BossBar;
import org.bukkit.entity.Illusioner;
import org.bukkit.entity.Ravager;

public class ArchIllager implements Boss, RiderRaider {
    private final Illusioner rider;
    private final Ravager ravager;
    private final BossBar bossBar;

    public ArchIllager(Illusioner rider, Ravager ravager, BossBar bossBar) {
        this.rider = rider;
        this.ravager = ravager;
        this.bossBar = bossBar;
    }

    @Override
    public Illusioner getBukkitEntity() {
        return rider;
    }

    @Override
    public Ravager getVehicle() {
        return ravager;
    }

    @Override
    public BossBar getBossBar() {
        return bossBar;
    }
}