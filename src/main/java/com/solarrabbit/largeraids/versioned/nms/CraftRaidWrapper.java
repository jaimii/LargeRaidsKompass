package com.solarrabbit.largeraids.versioned.nms;

import com.solarrabbit.largeraids.nms.AbstractCraftRaidWrapper;
import com.solarrabbit.largeraids.nms.AbstractRaidWrapper;

import net.minecraft.server.level.ServerLevel;

import org.bukkit.Raid;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftRaid;
import org.bukkit.craftbukkit.CraftWorld;

public class CraftRaidWrapper extends AbstractCraftRaidWrapper {

    public CraftRaidWrapper(AbstractRaidWrapper nmsRaid) {
        super(new CraftRaid(((RaidWrapper) nmsRaid).raid, ((RaidWrapper) nmsRaid).server));
    }

    public CraftRaidWrapper(Raid raid) {
        super(raid);
    }

    @Override
    public RaidWrapper getHandle() {
        if (!(this.raid instanceof CraftRaid craftRaid)) {
            return new RaidWrapper(null, null);
        }
        net.minecraft.world.entity.raid.Raid nmsRaid = craftRaid.getHandle();
        World bukkitWorld = this.raid.getLocation().getWorld();
        ServerLevel nmsLevel = (bukkitWorld instanceof CraftWorld craftWorld) ? craftWorld.getHandle() : null;
        return new RaidWrapper(nmsRaid, nmsLevel);
    }
}