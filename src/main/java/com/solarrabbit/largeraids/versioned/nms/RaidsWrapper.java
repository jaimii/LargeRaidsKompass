package com.solarrabbit.largeraids.versioned.nms;

import com.solarrabbit.largeraids.nms.AbstractPlayerEntityWrapper;
import com.solarrabbit.largeraids.nms.AbstractRaidWrapper;
import com.solarrabbit.largeraids.nms.AbstractRaidsWrapper;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.raid.Raids;

public class RaidsWrapper implements AbstractRaidsWrapper {
    private final Raids raids;

    RaidsWrapper(Raids raids) {
        this.raids = raids;
    }

    @Override
    public RaidWrapper createOrExtendRaid(AbstractPlayerEntityWrapper player) {
        return new RaidWrapper(this.raids.createOrExtendRaid(((PlayerEntityWrapper) player).player,
                ((PlayerEntityWrapper) player).player.blockPosition()),
                (ServerLevel) ((PlayerEntityWrapper) player).player.level());
    }

    @Override
    public void removeRaid(AbstractRaidWrapper raid) {
        if (raid instanceof RaidWrapper wrapper && wrapper.raid != null) {
            this.raids.raidMap.values().removeIf(r -> r == wrapper.raid);
        }
    }

    @Override
    public void setDirty() {
        this.raids.setDirty();
    }
}