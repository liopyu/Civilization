package net.liopyu.civilization.ai.goal.active;

import com.mojang.logging.LogUtils;
import net.liopyu.civilization.ai.core.AdventurerController;
import org.slf4j.Logger;
import net.liopyu.civilization.ai.core.ActionMode;
import net.liopyu.civilization.ai.goal.ModeScopedGoal;
import net.liopyu.civilization.entity.Adventurer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;

import java.util.EnumSet;

public final class GatherWoodGoal extends ModeScopedGoal {
    private static final Logger LOG = LogUtils.getLogger();

    private BlockPos target;
    private int mineTicks;
    private int repathCooldown;
    private int searchCooldown;
    private java.util.Set<BlockPos> cluster;
    private int clusterCooldown;

    // NEW: avoid instant re-queue of pillar after a dismantle/no-op
    private int pillarRetryCd = 0;

    public GatherWoodGoal(Adventurer a) {
        super(a, ActionMode.GATHER_WOOD);
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    public void onEnter(ActionMode mode) {
        target = null;
        mineTicks = 0;
        repathCooldown = 0;
        searchCooldown = 0;
        clusterCooldown = 0;
        pillarRetryCd = 0;
        cluster = new java.util.HashSet<>();
        LOG.info("[Wood] {} enter", adv.getStringUUID());
    }

    @Override
    public void onExit(ActionMode mode) {
        adv.getNavigation().stop();
        target = null;
        mineTicks = 0;
        cluster = null;
        LOG.info("[Wood] {} exit", adv.getStringUUID());
    }

    @Override
    public void tick() {
        if (pillarRetryCd > 0) pillarRetryCd--;

        // Acquire/refresh a log target from the cluster
        if (target == null || !isLog(adv.level(), target, adv)) {
            if ((cluster == null || cluster.isEmpty()) && searchCooldown <= 0) {
                BlockPos start = findNearestLog(adv, 12);
                searchCooldown = 20;
                if (start != null) {
                    LOG.info("[Wood] {} seed log {}", adv.getStringUUID(), start);
                    buildCluster(adv.level(), start, 96, 6);
                    LOG.info("[Wood] {} cluster size {}", adv.getStringUUID(), cluster.size());
                    target = pickNextFromCluster();
                    if (target != null && adv.tickCount % 20 == 0)
                        LOG.info("[Wood] {} next {}", adv.getStringUUID(), target);
                } else if (adv.tickCount % 40 == 0) {
                    LOG.info("[Wood] {} no logs nearby", adv.getStringUUID());
                }
            } else if (cluster != null && !cluster.isEmpty()) {
                if (clusterCooldown <= 0) {
                    int before = cluster.size();
                    pruneCluster();
                    int after = cluster.size();
                    if (after != before) LOG.info("[Wood] {} pruned {} -> {}", adv.getStringUUID(), before, after);
                    clusterCooldown = 10;
                } else clusterCooldown--;
                target = pickNextFromCluster();
                if (target != null && adv.tickCount % 40 == 0)
                    LOG.info("[Wood] {} continuing {}", adv.getStringUUID(), target);
            }

            if (target == null) {
                if (adv.tickCount % 60 == 0) {
                    LOG.info("[Wood] {} no target, exploring", adv.getStringUUID());
                    adv.controller().requestMode(ActionMode.EXPLORE, 200, 80, 20);
                }
                return;
            }
        }

        // Walk / adapt terrain entirely via UniversalReach
        double reach = adv.entityInteractionRange() + 1.25;
        boolean inReach = net.liopyu.civilization.ai.nav.UniversalReach.reach(
                adv,
                target,
                reach,
                // Wood gathering may need all tools:
                net.liopyu.civilization.ai.nav.NavTask.ALLOW_PILLAR
                        | net.liopyu.civilization.ai.nav.NavTask.ALLOW_TUNNEL
                        | net.liopyu.civilization.ai.nav.NavTask.ALLOW_STAIRS,
                980,
                20 * 12
        );
        if (!inReach) {
            mineTicks = 0; return;
        }

        // Face the target and chop
        adv.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);

        if (!isLog(adv.level(), target, adv)) {
            if (cluster != null) cluster.remove(target);
            LOG.info("[Wood] {} target gone {}", adv.getStringUUID(), target);
            target = null;
            mineTicks = 0;
            return;
        }

        if (adv.tickCount % 6 == 0) adv.swing(InteractionHand.MAIN_HAND);
        mineTicks++;
        if (mineTicks == 1) LOG.info("[Wood] {} start chop {}", adv.getStringUUID(), target);

        if (mineTicks >= 30) {
            Level lvl = adv.level();
            BlockPos p = target;
            mineTicks = 0;
            if (lvl.getBlockState(p).is(BlockTags.LOGS)) {
                lvl.destroyBlock(p, true, adv);
                if (cluster != null) cluster.remove(p);
                adv.controller().requestPickup(
                        net.liopyu.civilization.ai.core.PickupRequest.any(adv.tickCount, 60, 8).withNear(p));
                LOG.info("[Wood] {} chopped {}", adv.getStringUUID(), p);
            } else {
                LOG.info("[Wood] {} skip chop invalid {}", adv.getStringUUID(), p);
            }

            target = (cluster != null && !cluster.isEmpty()) ? pickNextFromCluster() : null;
            if (target == null) {
                searchCooldown = 0;
                LOG.info("[Wood] {} cluster exhausted", adv.getStringUUID());
            }
        }

        if (adv.tickCount % 80 == 0) {
            int c = (cluster == null) ? 0 : cluster.size();
            LOG.info("[Wood] {} heartbeat target={} cluster={}", adv.getStringUUID(), target, c);
        }
    }


    private void buildCluster(Level lvl, BlockPos start, int max, int radius) {
        cluster.clear();
        java.util.Set<BlockPos> visited = new java.util.HashSet<>();
        java.util.ArrayDeque<BlockPos> q = new java.util.ArrayDeque<>();
        q.add(start.immutable());
        visited.add(start.immutable());
        BlockPos s = start.immutable();
        while (!q.isEmpty() && cluster.size() < max) {
            BlockPos p = q.removeFirst();
            if (!lvl.isLoaded(p)) continue;
            if (p.distManhattan(s) > radius * 2) continue;
            if (!lvl.getBlockState(p).is(BlockTags.LOGS)) continue;
            cluster.add(p.immutable());
            for (BlockPos d : new BlockPos[]{p.above(), p.below(), p.north(), p.south(), p.east(), p.west()}) {
                if (!visited.contains(d)) {
                    visited.add(d);
                    q.addLast(d);
                }
            }
        }
    }

    private void pruneCluster() {
        if (cluster == null || cluster.isEmpty()) return;
        java.util.Iterator<BlockPos> it = cluster.iterator();
        while (it.hasNext()) {
            BlockPos p = it.next();
            if (!isLog(adv.level(), p, adv)) it.remove();
        }
    }

    private BlockPos pickNextFromCluster() {
        if (cluster == null || cluster.isEmpty()) return null;
        BlockPos best = null;
        double bestD2 = Double.MAX_VALUE;
        for (BlockPos p : cluster) {
            double d2 = adv.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
            if (d2 < bestD2) {
                bestD2 = d2;
                best = p;
            }
        }
        return best == null ? null : best.immutable();
    }

    private static boolean isLog(Level level, BlockPos pos, Adventurer a) {
        return level.isLoaded(pos) && !a.protectedBlocks().isProtected(pos) && level.getBlockState(pos).is(BlockTags.LOGS);
    }

    private static BlockPos findNearestLog(Adventurer a, int radius) {
        BlockPos origin = a.blockPosition();
        BlockPos best = null;
        double bestD2 = Double.MAX_VALUE;
        int r = Math.max(1, radius);
        for (int y = -r; y <= r; y++) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos p = origin.offset(x, y, z);
                    if (!a.isValidWorkPos(p)) continue;
                    if (!a.level().getBlockState(p).is(BlockTags.LOGS)) continue;
                    double d2 = a.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
                    if (d2 < bestD2) {
                        bestD2 = d2;
                        best = p.immutable();
                    }
                }
            }
        }
        return best;
    }
}
