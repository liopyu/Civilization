package net.liopyu.civilization.ai.goal.active;

import com.mojang.logging.LogUtils;
import net.liopyu.civilization.ai.core.ActionMode;
import net.liopyu.civilization.ai.core.AdventurerController;
import net.liopyu.civilization.ai.goal.ModeScopedGoal;
import net.liopyu.civilization.ai.util.AiUtil;
import net.liopyu.civilization.entity.Adventurer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;

public final class PillarUpGoal extends ModeScopedGoal {
    private static final org.slf4j.Logger LOG = LogUtils.getLogger();
    private AdventurerController.PillarRequest req;
    private int stepDelay;
    private int lastLogTick;

    public PillarUpGoal(Adventurer a) {
        super(a, ActionMode.PILLAR_UP);
    }

    @Override
    protected void onEnter(ActionMode mode) {
        req = adv.controller().getPillar();
        stepDelay = 0;
        lastLogTick = -999;
        if (req != null) LOG.info("[Pillar] {} enter base={} targetY={}", adv.getStringUUID(), req.base, req.targetY);
    }

    @Override
    protected void onExit(ActionMode mode) {
        adv.getNavigation().stop();
        req = null;
        LOG.info("[Pillar] {} exit", adv.getStringUUID());
    }

    @Override
    public void tick() {
        if (req == null) {
            req = adv.controller().getPillar();
            if (req == null) return;
        }

        // (1) Move to base using UniversalReach (no direct moveTo / repath)
        BlockPos stand = new BlockPos(
                req.base.getX(),
                Math.max(req.base.getY(), adv.blockPosition().getY()),
                req.base.getZ()
        );

        // Use the same “reach” radius the request was planned with.
        double baseReach = Math.sqrt(req.reachSq);
        if (!net.liopyu.civilization.ai.nav.UniversalReach.reach(
                adv,
                stand,
                baseReach,
                net.liopyu.civilization.ai.nav.NavTask.ALLOW_TUNNEL, // allow gentle clearing on the way
                Math.max(900, req.priority),                        // bias high while in this mode
                20 * 10)) {                                         // short TTL; we tick every frame anyway
            return; // still traveling/adapting to reach the base
        }
        adv.getNavigation().stop();

        // (2) If we already reached the requested Y, or targets are done, finalize (cleanup pillar)
        boolean targetYReached = adv.blockPosition().getY() >= req.targetY;
        boolean noTargets = req.targets.isEmpty();
        if (targetYReached || noTargets) {
            dismantlePillar();
            adv.controller().clearPillar(req.id);
            adv.controller().requestMode(ActionMode.GATHER_WOOD, 800, 60, 20);
            if (adv.tickCount % 20 == 0)
                LOG.info("[Pillar] {} finished; cleanup={}, resume", adv.getStringUUID());
            return;
        }

        // (3) Work current target
        BlockPos cur = req.targets.peekFirst();
        if (cur == null) return;

        if (cur.getY() <= adv.blockPosition().getY()) {
            dismantlePillar();
            adv.controller().clearPillar(req.id);
            adv.controller().requestMode(ActionMode.GATHER_WOOD, 800, 60, 20);
            if (adv.tickCount % 20 == 0)
                LOG.info("[Pillar] {} next target below {}; dismantled", adv.getStringUUID(), cur);
            return;
        }

        // (4) If target in reach: clear soft blockers then break target
        double t2 = adv.distanceToSqr(cur.getX() + 0.5, cur.getY() + 0.5, cur.getZ() + 0.5);
        if (t2 <= req.reachSq) {
            var box = adv.getBoundingBox().move(0, 1.0, 0).inflate(0.05);
            var pred = AiUtil.obstaclePredicateForMode(adv);
            BlockPos soft = AiUtil.firstBlockingInAABB(adv, box, pred);
            if (soft != null && !adv.protectedBlocks().isProtected(soft)) {
                AiUtil.harvestBlockToInternal(adv, soft);
                if (adv.tickCount - lastLogTick >= 20) {
                    LOG.info("[Pillar] {} cleared head {}", adv.getStringUUID(), soft);
                    lastLogTick = adv.tickCount;
                }
                stepDelay = 4;
                return;
            }

            if (adv.tickCount % 6 == 0) adv.swing(InteractionHand.MAIN_HAND);
            if (AiUtil.harvestBlockToInternal(adv, cur)) {
                if (adv.tickCount - lastLogTick >= 20) {
                    LOG.info("[Pillar] {} broke target {}", adv.getStringUUID(), cur);
                    lastLogTick = adv.tickCount;
                }
            }
            req.targets.pollFirst();
            stepDelay = 6;
            return;
        }

        // (5) Not in reach yet → gain height (place step)
        if (stepDelay > 0) {
            stepDelay--; return;
        }

        var nextBox = adv.getBoundingBox().move(0, 1.0, 0).inflate(0.05);
        var pred = AiUtil.obstaclePredicateForMode(adv);
        BlockPos blocker = AiUtil.firstBlockingInAABB(adv, nextBox, pred);
        if (blocker != null) {
            if (!adv.protectedBlocks().isProtected(blocker)) {
                AiUtil.harvestBlockToInternal(adv, blocker);
                if (adv.tickCount - lastLogTick >= 20) {
                    LOG.info("[Pillar] {} cleared blocker {}", adv.getStringUUID(), blocker);
                    lastLogTick = adv.tickCount;
                }
            } else if (adv.tickCount - lastLogTick >= 20) {
                LOG.info("[Pillar] {} head blocker {} PROTECTED", adv.getStringUUID(), blocker);
                lastLogTick = adv.tickCount;
            }
            stepDelay = 6;
            return;
        }

        if (!AiUtil.peekHasPlaceableBlock(adv)) {
            var items = java.util.Set.of(Blocks.DIRT.asItem(), Blocks.COBBLESTONE.asItem());
            var blocks = java.util.Set.of(Blocks.DIRT, Blocks.STONE, Blocks.COBBLESTONE);
            adv.controller().planner().requestMineable(
                    ResourceLocation.fromNamespaceAndPath("civilization", "pillar_fill"),
                    items, java.util.Set.of(), blocks, java.util.Set.of(),
                    6, 850
            );
            adv.controller().requestMode(ActionMode.GATHER_MINEABLE, 900, 100, 20);
            if (adv.tickCount - lastLogTick >= 40) {
                LOG.info("[Pillar] {} queued fillers", adv.getStringUUID());
                lastLogTick = adv.tickCount;
            }
            return;
        }

        if (adv.tickCount % 6 == 0) adv.swing(InteractionHand.MAIN_HAND);
        boolean ok = AiUtil.pillarUpOneBlock(adv, 4);
        if (ok) {
            AiUtil.markTemporaryPillar(adv, stand);
            stepDelay = 8;
            if (adv.tickCount - lastLogTick >= 20) {
                LOG.info("[Pillar] {} step at {}", adv.getStringUUID(), stand);
                lastLogTick = adv.tickCount;
            }
        } else {
            stepDelay = 10;
            if (adv.tickCount - lastLogTick >= 20) {
                LOG.info("[Pillar] {} step FAILED at {}", adv.getStringUUID(), stand);
                lastLogTick = adv.tickCount;
            }
        }
    }

    private void dismantlePillar() {
        var lifo = AiUtil.drainTemporaryPillarsLifo(adv);
        for (BlockPos p : lifo) {
            adv.protectedBlocks().unprotect(p);
            AiUtil.harvestBlockToInternal(adv, p);
        }
        if (adv.tickCount % 20 == 0)
            LOG.info("[Pillar] {} dismantled {} steps", adv.getStringUUID(), lifo.size());
    }
}

