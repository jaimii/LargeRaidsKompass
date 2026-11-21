package com.solarrabbit.largeraids.versioned.nms;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.solarrabbit.largeraids.nms.AbstractBlockPositionWrapper;
import com.solarrabbit.largeraids.nms.AbstractRaidWrapper;
import com.solarrabbit.largeraids.nms.AbstractRaiderWrapper;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.raid.Raid;

public class RaidWrapper implements AbstractRaidWrapper {
    private static final MethodHandle GROUPS_SPAWNED_SETTER;

    static {
        MethodHandle setter = null;
        try {
            setter = MethodHandles.privateLookupIn(Raid.class, MethodHandles.lookup())
                    .findSetter(Raid.class, "groupsSpawned", int.class);
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
        }
        GROUPS_SPAWNED_SETTER = setter;
    }

    final Raid raid;
    final ServerLevel server;

    RaidWrapper(Raid raid, ServerLevel server) {
        this.raid = raid;
        this.server = server;
    }

    @Override
    public boolean isEmpty() {
        return raid == null;
    }

    @Override
    public void stop() {
        if (raid != null) {
            raid.stop();
        }
    }

    @Override
    public boolean isBetweenWaves() {
        return raid != null && raid.isBetweenWaves();
    }

    @Override
    public boolean hasFirstWaveSpawned() {
        return raid != null && raid.hasFirstWaveSpawned();
    }

    @Override
    public void setRaidOmenLevel(int level) {
        if (this.raid != null) {
            this.raid.setRaidOmenLevel(level);
        }
    }

    @Override
    public int getTotalGroups() {
        return this.raid == null ? 0 : this.raid.numGroups;
    }

    @Override
    public int getGroupsSpawned() {
        return this.raid == null ? 0 : this.raid.getGroupsSpawned();
    }

    @Override
    public void setGroupsSpawned(int groupsSpawned) {
        if (this.raid == null || GROUPS_SPAWNED_SETTER == null) return;
        try {
            GROUPS_SPAWNED_SETTER.invokeExact(this.raid, groupsSpawned);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @Override
    public int getTotalRaidersAlive() {
        return this.raid == null ? 0 : this.raid.getTotalRaidersAlive();
    }

    @Override
    public void joinRaid(int i, AbstractRaiderWrapper raider, @Nullable AbstractBlockPositionWrapper blockPosition,
                         boolean flag) {
        if (this.raid == null) return;
        raid.joinRaid(server, i, ((RaiderWrapper) raider).raider,
                blockPosition == null ? null : ((BlockPositionWrapper) blockPosition).blockPos, flag);
    }

    @Override
    public boolean addWaveMob(int wave, AbstractRaiderWrapper raider, boolean flag) {
        return this.raid != null && this.raid.addWaveMob(server, wave, ((RaiderWrapper) raider).raider, flag);
    }

    @Override
    public void removeFromRaid(AbstractRaiderWrapper raider, boolean flag) {
        if (this.raid != null) {
            this.raid.removeFromRaid(server, ((RaiderWrapper) raider).raider, flag);
        }
    }

    @Override
    public Set<UUID> getHeroesOfTheVillage() {
        return this.raid == null ? Collections.emptySet() : this.raid.heroesOfTheVillage;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof RaidWrapper other) {
            return this.raid != null && this.raid == other.raid;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return this.raid == null ? 0 : this.raid.hashCode();
    }

    @Override
    public boolean isActive() {
        return raid != null && raid.isActive();
    }
}