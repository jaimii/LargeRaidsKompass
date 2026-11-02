package com.solarrabbit.largeraids.raid.mob.manager;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftRaider;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Spellcaster;
import org.bukkit.util.Vector;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class EvokerGoalUtil {
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

    public static GoalSelector getGoalSelector(Mob mob) {
        if (mob == null || GOAL_SELECTOR_GETTER == null) return null;
        try {
            return (GoalSelector) GOAL_SELECTOR_GETTER.invokeExact(mob);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static void injectRaidFleeGoal(Spellcaster evoker, Location raidCenter) {
        try {
            net.minecraft.world.entity.raid.Raider nmsEvoker = ((CraftRaider) evoker).getHandle();
            GoalSelector goalSelector = getGoalSelector(nmsEvoker);
            if (goalSelector == null) return;

            List<Goal> toRemove = new ArrayList<>();
            for (WrappedGoal wrapped : goalSelector.getAvailableGoals()) {
                Goal underlying = wrapped.getGoal();
                String className = underlying.getClass().getSimpleName();
                if (className.contains("AvoidEntity") || className.contains("Avoid") || className.contains("Flee")) {
                    toRemove.add(underlying);
                }
            }
            for (Goal goal : toRemove) {
                goalSelector.removeGoal(goal);
            }

            goalSelector.addGoal(1, new EvokerRaidFleeGoal(nmsEvoker, raidCenter));
        } catch (Exception ignored) {}
    }

    public static class EvokerRaidFleeGoal extends Goal {
        private final Mob mob;
        private final Location raidCenter;
        private final double maxRadiusSq = 130.0 * 130.0;
        private Player targetPlayer;

        public EvokerRaidFleeGoal(Mob mob, Location raidCenter) {
            this.mob = mob;
            this.raidCenter = raidCenter;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            Entity bukkitEntity = mob.getBukkitEntity();
            if (bukkitEntity == null || !bukkitEntity.isValid()) return false;

            Location evokerLoc = bukkitEntity.getLocation();
            Player nearest = null;
            double nearestDistSq = 64.0;

            for (Player p : bukkitEntity.getWorld().getPlayers()) {
                if (p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR) {
                    double distSq = evokerLoc.distanceSquared(p.getLocation());
                    if (distSq < nearestDistSq) {
                        nearestDistSq = distSq;
                        nearest = p;
                    }
                }
            }

            if (nearest == null) return false;
            this.targetPlayer = nearest;
            return true;
        }

        @Override
        public void start() {
            moveAwayFromPlayer();
        }

        @Override
        public void tick() {
            if (targetPlayer != null && targetPlayer.isValid()) {
                moveAwayFromPlayer();
            }
        }

        private void moveAwayFromPlayer() {
            Entity bukkitEntity = mob.getBukkitEntity();
            if (bukkitEntity == null) return;

            Location evokerLoc = bukkitEntity.getLocation();
            Location playerLoc = targetPlayer.getLocation();

            Vector fleeDir = evokerLoc.toVector().subtract(playerLoc.toVector());
            if (fleeDir.lengthSquared() == 0) {
                fleeDir = new Vector(1, 0, 0);
            } else {
                fleeDir.normalize();
            }

            Location targetFleeLoc = evokerLoc.clone().add(fleeDir.multiply(8.0));

            if (raidCenter != null && raidCenter.getWorld() != null && raidCenter.getWorld().equals(evokerLoc.getWorld())) {
                if (targetFleeLoc.distanceSquared(raidCenter) > maxRadiusSq) {
                    Vector toCenter = raidCenter.toVector().subtract(evokerLoc.toVector());
                    if (toCenter.lengthSquared() > 0) {
                        toCenter.normalize();
                        Vector boundedDir = fleeDir.add(toCenter.multiply(1.5));
                        if (boundedDir.lengthSquared() > 0) {
                            boundedDir.normalize();
                        }
                        targetFleeLoc = evokerLoc.clone().add(boundedDir.multiply(8.0));
                    }
                }
            }

            mob.getNavigation().moveTo(targetFleeLoc.getX(), targetFleeLoc.getY(), targetFleeLoc.getZ(), 0.80);
        }

        @Override
        public boolean canContinueToUse() {
            if (targetPlayer == null || !targetPlayer.isValid()) return false;
            Entity bukkitEntity = mob.getBukkitEntity();
            if (bukkitEntity == null || !bukkitEntity.isValid()) return false;

            return bukkitEntity.getLocation().distanceSquared(targetPlayer.getLocation()) <= 100.0
                    && !mob.getNavigation().isDone();
        }
    }
}