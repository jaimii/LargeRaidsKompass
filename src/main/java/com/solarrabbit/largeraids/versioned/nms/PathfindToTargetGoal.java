package com.solarrabbit.largeraids.versioned.nms;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

public class PathfindToTargetGoal extends Goal {
    private final Mob mob;
    private BlockPos targetPos;
    private double targetRadius;
    private double navSpeed;
    private boolean runOnce;

    public PathfindToTargetGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    public void setTargetPos(BlockPos pos, double radius, double navSpeed, boolean runOnce) {
        this.targetPos = pos;
        this.targetRadius = radius;
        this.navSpeed = navSpeed;
        this.runOnce = runOnce;
    }

    private boolean isCloseToGoal() {
        return targetPos.closerToCenterThan(this.mob.position(), targetRadius);
    }

    @Override
    public boolean canUse() {
        if (this.mob.getTarget() == null
                && !this.mob.hasControllingPassenger()
                && targetPos != null) {
            if (isCloseToGoal()) {
                if (runOnce) {
                    targetPos = null;
                    WorldServerWrapper.getGoalSelector(mob).removeGoal(this);
                    return false;
                }
            } else {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void tick() {
        if (targetPos != null) {
            this.mob.getNavigation().moveTo(targetPos.getX(), targetPos.getY(), targetPos.getZ(), navSpeed);
        }
    }
}