package com.solarrabbit.largeraids.versioned.nms;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

import com.solarrabbit.largeraids.nms.AbstractCraftRaidWrapper;
import com.solarrabbit.largeraids.nms.AbstractRaidWrapper;

import net.minecraft.server.level.ServerLevel;

import org.bukkit.Raid;
import org.bukkit.craftbukkit.CraftRaid;

public class CraftRaidWrapper extends AbstractCraftRaidWrapper {
    private static final MethodHandle RAID_HANDLE_GETTER;
    private static final MethodHandle LEVEL_HANDLE_GETTER;

    static {
        MethodHandle rGetter = null;
        MethodHandle lGetter = null;
        try {
            Field raidField = CraftRaid.class.getDeclaredField("handle");
            raidField.setAccessible(true);
            rGetter = MethodHandles.lookup().unreflectGetter(raidField);

            Field levelField = CraftRaid.class.getDeclaredField("level");
            levelField.setAccessible(true);
            lGetter = MethodHandles.lookup().unreflectGetter(levelField);
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
        }
        RAID_HANDLE_GETTER = rGetter;
        LEVEL_HANDLE_GETTER = lGetter;
    }

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
        try {
            net.minecraft.world.entity.raid.Raid nmsRaid =
                    (net.minecraft.world.entity.raid.Raid) RAID_HANDLE_GETTER.invokeExact(craftRaid);
            net.minecraft.world.level.Level nmsLevel =
                    (net.minecraft.world.level.Level) LEVEL_HANDLE_GETTER.invokeExact(craftRaid);
            return new RaidWrapper(nmsRaid, (ServerLevel) nmsLevel);
        } catch (Throwable e) {
            e.printStackTrace();
            return new RaidWrapper(null, null);
        }
    }
}