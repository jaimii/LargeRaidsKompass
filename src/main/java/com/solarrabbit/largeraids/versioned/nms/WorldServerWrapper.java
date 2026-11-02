package com.solarrabbit.largeraids.versioned.nms;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.List;

import com.solarrabbit.largeraids.nms.AbstractBlockPositionWrapper;
import com.solarrabbit.largeraids.nms.AbstractWorldServerWrapper;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.phys.AABB;

public class WorldServerWrapper implements AbstractWorldServerWrapper {
    private static final MethodHandle GOAL_SELECTOR_GETTER;

    static {
        MethodHandle getter = null;
        try {
            Field f = Mob.class.getDeclaredField("goalSelector");
            f.setAccessible(true);
            getter = MethodHandles.lookup().unreflectGetter(f);
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
        }
        GOAL_SELECTOR_GETTER = getter;
    }

    static GoalSelector getGoalSelector(Mob mob) {
        try {
            return (GoalSelector) GOAL_SELECTOR_GETTER.invokeExact(mob);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    final ServerLevel server;

    WorldServerWrapper(ServerLevel server) {
        this.server = server;
    }

    @Override
    @SuppressWarnings("unchecked")
    public int clearMobTargets(Class<?> entityClass, AbstractBlockPositionWrapper search, double range) {
        if (!(Mob.class.isAssignableFrom(entityClass)))
            return -1;

        BlockPos searchPos = ((BlockPositionWrapper) search).blockPos;
        AABB aabb = new AABB(searchPos.getX() - range, searchPos.getY() - range, searchPos.getZ() - range,
                searchPos.getX() + range, searchPos.getY() + range, searchPos.getZ() + range);
        List<? extends Mob> mobs = server.getEntitiesOfClass((Class<? extends Mob>) entityClass, aabb);
        int count = 0;
        for (Mob mob : mobs) {
            GoalSelector goalSelector = getGoalSelector(mob);
            for (WrappedGoal goal : goalSelector.getAvailableGoals()) {
                if (goal.getGoal() instanceof PathfindToTargetGoal) {
                    goalSelector.removeGoal(goal.getGoal());
                    count++;
                    break;
                }
            }
        }

        return count;
    }

    @Override
    @SuppressWarnings("unchecked")
    public int setMobTargets(Class<?> entityClass, AbstractBlockPositionWrapper search, double range,
                             AbstractBlockPositionWrapper target, double radius, double navSpeed, int prio, boolean pathfindOnce) {
        if (!(Mob.class.isAssignableFrom(entityClass)))
            return -1;

        BlockPos searchPos = ((BlockPositionWrapper) search).blockPos;
        AABB aabb = new AABB(searchPos.getX() - range, searchPos.getY() - range, searchPos.getZ() - range,
                searchPos.getX() + range, searchPos.getY() + range, searchPos.getZ() + range);
        List<? extends Mob> mobs = server.getEntitiesOfClass((Class<? extends Mob>) entityClass, aabb);
        for (Mob mob : mobs) {
            GoalSelector goalSelector = getGoalSelector(mob);
            for (WrappedGoal goal : goalSelector.getAvailableGoals()) {
                if (goal.getGoal() instanceof PathfindToTargetGoal) {
                    goalSelector.removeGoal(goal.getGoal());
                    break;
                }
            }

            PathfindToTargetGoal goal = new PathfindToTargetGoal(mob);
            goal.setTargetPos(((BlockPositionWrapper) target).blockPos, radius, navSpeed, pathfindOnce);
            goalSelector.addGoal(prio, goal);
        }

        return mobs.size();
    }

    @Override
    public RaidWrapper getRaidAt(AbstractBlockPositionWrapper blockPos) {
        return new RaidWrapper(this.server.getRaidAt(((BlockPositionWrapper) blockPos).blockPos), this.server);
    }

    @Override
    public RaidsWrapper getRaids() {
        return new RaidsWrapper(this.server.getRaids());
    }

    @Override
    public VillageManagerWrapper getVillageRecordManager() {
        return new VillageManagerWrapper(this.server.getPoiManager());
    }
}