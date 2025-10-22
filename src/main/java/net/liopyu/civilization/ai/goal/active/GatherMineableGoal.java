package net.liopyu.civilization.ai.goal.active;

import com.mojang.logging.LogUtils;
import net.liopyu.civilization.ai.core.AdventurerController;
import org.slf4j.Logger;
import net.liopyu.civilization.ai.core.ActionMode;
import net.liopyu.civilization.ai.goal.ModeScopedGoal;
import net.liopyu.civilization.ai.core.ResourcePlanner;
import net.liopyu.civilization.entity.Adventurer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

public final class GatherMineableGoal extends ModeScopedGoal {
    private static final Logger LOG = LogUtils.getLogger();

    private BlockPos target;
    private int mineTicks;
    private int repathCooldown;
    private int searchCooldown;
    private ResourcePlanner.Need active;
    private int baseRadius = 12;
    private int maxRadius = 48;
    private int curRadius;
    private int noFindTicks;
    private int roamCooldown;
    private BlockPos roamTarget;

    public GatherMineableGoal(Adventurer a) {
        super(a, ActionMode.GATHER_MINEABLE);
    }

    @Override
    protected void onEnter(ActionMode mode) {
        target = null;
        mineTicks = 0;
        repathCooldown = 0;
        searchCooldown = 0;
        active = null;
        curRadius = baseRadius;
        noFindTicks = 0;
        roamCooldown = 0;
        roamTarget = null;
        LOG.info("[Mineable] {} enter mode, radius={}..{}", adv.getStringUUID(), baseRadius, maxRadius);
    }

    @Override
    protected void onExit(ActionMode mode) {
        adv.getNavigation().stop();
        target = null;
        active = null;
        LOG.info("[Mineable] {} exit mode", adv.getStringUUID());
    }

    @Override
    public void tick() {
        // 1) Ensure we have an active "need"
        if (active == null) {
            Map<Item, Integer> inv = new HashMap<>();
            // (optional) seed counts of items in this need
            active = adv.controller().planner().topMineableNeed(inv).orElse(null);
            if (active == null) {
                LOG.info("[Mineable] {} no active need", adv.getStringUUID());
                return;
            }
            LOG.info("[Mineable] {} picked need id={} remaining={} prio={}",
                    adv.getStringUUID(), active.id, active.remaining, active.priority);
        }

        // 2) Lock a valid target
        if (target == null || !isCandidate(target)) {
            if (searchCooldown <= 0) {
                target = findCandidate(curRadius);
                searchCooldown = 10;

                if (target == null) {
                    // adaptive roam while searching
                    noFindTicks++;
                    if ((noFindTicks % 60) == 0 && curRadius < maxRadius) {
                        int old = curRadius;
                        curRadius = Math.min(maxRadius, curRadius + 6);
                        LOG.info("[Mineable] {} expand search radius {} -> {}", adv.getStringUUID(), old, curRadius);
                    }
                    if (roamTarget == null && roamCooldown <= 0) {
                        var v = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPos(adv, Math.min(24, curRadius), 6);
                        if (v != null) {
                            roamTarget = BlockPos.containing(v.x, v.y, v.z);
                            // Simple nudge while UniversalReach has nothing to do
                            adv.getNavigation().moveTo(v.x, v.y, v.z, 1.1);
                            roamCooldown = 40;
                            LOG.info("[Mineable] {} roaming to {}", adv.getStringUUID(), roamTarget);
                        } else {
                            LOG.info("[Mineable] {} roam pick failed at radius={}", adv.getStringUUID(), curRadius);
                        }
                    }
                    if (roamTarget != null) {
                        double d2r = adv.distanceToSqr(roamTarget.getX() + 0.5, roamTarget.getY() + 0.5, roamTarget.getZ() + 0.5);
                        if (d2r < 4.0 || adv.getNavigation().isDone()) {
                            LOG.info("[Mineable] {} reached roam {}, clearing roam target", adv.getStringUUID(), roamTarget);
                            roamTarget = null;
                        }
                    }
                    if (roamCooldown > 0) roamCooldown--;
                    return;
                } else {
                    LOG.info("[Mineable] {} found target {} at radius={}", adv.getStringUUID(), target, curRadius);
                    roamTarget = null;
                    noFindTicks = 0;
                    curRadius = Math.max(baseRadius, curRadius - 2);
                }
            } else {
                searchCooldown--;
                return;
            }
        }

        // 3) Delegate reaching logic entirely to UniversalReach
        final double reach = adv.entityInteractionRange() + 1.25;
        // Allow all movement helpers: stairs down, pillaring up, tunneling
        final int navFlags =
                net.liopyu.civilization.ai.nav.NavTask.ALLOW_STAIRS |
                        net.liopyu.civilization.ai.nav.NavTask.ALLOW_PILLAR |
                        net.liopyu.civilization.ai.nav.NavTask.ALLOW_TUNNEL;

        // IMPORTANT: Do this BEFORE any manual pathing. If this returns false,
        // UniversalReach is actively working (pathing, carving, or scheduling steps).
        if (!net.liopyu.civilization.ai.nav.UniversalReach.reach(adv, target, reach, navFlags, 975, 20 * 20)) {
            mineTicks = 0; // don't accumulate swing while moving/carving
            return;
        }

        // 4) We are within reach — perform the mining action
        final double cx = target.getX() + 0.5, cy = target.getY() + 0.5, cz = target.getZ() + 0.5;
        adv.getNavigation().stop();
        adv.getLookControl().setLookAt(cx, cy, cz);

        if (!isCandidate(target)) {
            LOG.info("[Mineable] {} target {} no longer valid", adv.getStringUUID(), target);
            target = null; mineTicks = 0; return;
        }

        if (adv.tickCount % 6 == 0) adv.swing(InteractionHand.MAIN_HAND);
        mineTicks++;
        if (mineTicks == 1) LOG.info("[Mineable] {} start mining {}", adv.getStringUUID(), target);

        if (mineTicks >= 34) {
            Level lvl = adv.level();
            BlockPos p = target;
            target = null;
            mineTicks = 0;

            BlockState st = lvl.getBlockState(p);
            if (isCandidateState(st)) {
                lvl.destroyBlock(p, true, adv);
                adv.controller().requestPickup(
                        net.liopyu.civilization.ai.core.PickupRequest.any(adv.tickCount, 60, 8).withNear(p)
                );
                LOG.info("[Mineable] {} broke {} at {}", adv.getStringUUID(), st.getBlock(), p);
            } else {
                LOG.info("[Mineable] {} skip break, state invalid at {}", adv.getStringUUID(), p);
            }

            Map<Item, Integer> inv = new HashMap<>();
            if (active != null) for (Item it : active.items) inv.put(it, adv.countItem(it));
            adv.controller().planner().reconcile(inv);

            boolean more = adv.controller().planner().hasOutstanding(net.liopyu.civilization.ai.core.ResourceType.MINEABLE);
            LOG.info("[Mineable] {} reconcile done, moreOutstanding={}", adv.getStringUUID(), more);
            if (more) active = null;
        }
    }


    private boolean isCandidate(BlockPos pos) {
        if (adv.protectedBlocks().isProtected(pos)) return false;
        return isCandidateState(adv.level().getBlockState(pos));
    }

    private boolean isCandidateState(BlockState st) {
        if (active == null) return false;
        if (!st.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE)) return false;
        if (!active.accepts(st, adv.level().registryAccess())) return false;
        if (st.is(net.minecraft.world.level.block.Blocks.COBBLESTONE)) return true;
        return true;
    }

    private BlockPos findCandidate(int radius) {
        BlockPos o = adv.blockPosition();
        BlockPos best = null;
        double bestD2 = Double.MAX_VALUE;
        int r = Math.max(1, radius);
        for (int y = -r; y <= r; y++) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos p = o.offset(x, y, z);
                    if (!adv.isValidWorkPos(p)) continue;
                    if (adv.protectedBlocks().isProtected(p)) continue;
                    if (!isCandidateState(adv.level().getBlockState(p))) continue;
                    double d2 = adv.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
                    if (d2 < bestD2) {
                        bestD2 = d2; best = p.immutable();
                    }
                }
            }
        }
        return best;
    }
}
