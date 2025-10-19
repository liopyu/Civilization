package net.liopyu.civilization.ai.goal.active;

import net.liopyu.civilization.ai.ActionMode;
import net.liopyu.civilization.ai.goal.ModeScopedGoal;
import net.liopyu.civilization.entity.Adventurer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public final class BuildShelterGoal extends ModeScopedGoal {
    private final List<BlockPos> plan = new ArrayList<>();
    private int idx;
    private int placeCooldown;
    private int repathCooldown;
    private BlockPos origin;

    public BuildShelterGoal(Adventurer a) {
        super(a, ActionMode.BUILD_SHELTER);
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    protected void onEnter(ActionMode mode) {
        plan.clear();
        idx = 0;
        placeCooldown = 0;
        repathCooldown = 0;
        origin = adv.hasHome() ? adv.getHomePos().orElse(adv.blockPosition()) : adv.blockPosition();
        int y = findGroundY(adv.level(), origin);
        origin = new BlockPos(origin.getX(), y + 1, origin.getZ());
        makePlan();
    }

    @Override
    protected void onExit(ActionMode mode) {
        adv.getNavigation().stop();
        plan.clear();
    }

    @Override
    public void tick() {
        if (idx >= plan.size()) {
            adv.controller().requestMode(ActionMode.SET_HOME, 800, 80, 20);
            return;
        }
        if (placeCooldown > 0) placeCooldown--;
        BlockPos p = plan.get(idx);
        if (!adv.isValidWorkPos(p)) {
            idx++;
            return;
        }
        Level lvl = adv.level();
        BlockState st = lvl.getBlockState(p);
        if (st.is(Blocks.COBBLESTONE)) {
            idx++;
            return;
        }
        if (!isPlaceable(lvl, p)) {
            idx++;
            return;
        }
        double r = adv.entityInteractionRange() + 1.25;
        double cx = p.getX() + 0.5;
        double cy = p.getY() + 0.5;
        double cz = p.getZ() + 0.5;
        double d2 = adv.distanceToSqr(cx, cy, cz);
        if (d2 > r * r) {
            if (repathCooldown <= 0 || adv.getNavigation().isDone()) {
                adv.getNavigation().moveTo(cx, cy, cz, 1.0);
                repathCooldown = 10;
            } else repathCooldown--;
            return;
        }
        adv.getNavigation().stop();
        adv.getLookControl().setLookAt(cx, cy, cz);
        if (placeCooldown > 0) return;
        if (!adv.consumeItems(Blocks.COBBLESTONE.asItem(), 1)) {
            adv.controller().requestMode(ActionMode.MINE_STONE, 700, 120, 20);
            return;
        }
        if (adv.tickCount % 6 == 0) adv.swing(InteractionHand.MAIN_HAND);
        lvl.setBlock(p, Blocks.COBBLESTONE.defaultBlockState(), 3);
        adv.controller().requestPickup(net.liopyu.civilization.ai.core.PickupRequest.any(adv.tickCount, 60, 8).withNear(p));

        placeCooldown = 6;
        idx++;
    }

    private void makePlan() {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos b = new BlockPos(origin.getX() + dx, origin.getY() - 1, origin.getZ() + dz);
                plan.add(b.immutable());
            }
        }
    }

    private static boolean isPlaceable(Level lvl, BlockPos p) {
        BlockState here = lvl.getBlockState(p);
        return here.isAir() || here.canBeReplaced();
    }

    private static int findGroundY(Level lvl, BlockPos start) {
        int y = start.getY();
        BlockPos pos = new BlockPos(start.getX(), y, start.getZ());
        int down = 0;
        while (down < 6 && y > lvl.getMinBuildHeight()) {
            BlockPos below = pos.below();
            if (lvl.getBlockState(below).isSolidRender(lvl, below)) break;
            y--;
            pos = pos.below();
            down++;
        }
        int up = 0;
        while (up < 6 && y < lvl.getMaxBuildHeight() - 1) {
            BlockPos below = new BlockPos(start.getX(), y - 1, start.getZ());
            if (lvl.getBlockState(below).isSolidRender(lvl, below)) break;
            y++;
            up++;
        }
        return y;
    }
}
