package net.liopyu.civilization.ai.goal;

import net.liopyu.civilization.ai.ActionMode;
import net.liopyu.civilization.ai.util.AiUtil;
import net.liopyu.civilization.entity.Adventurer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.EnumSet;

public class MineBlockGoal extends Goal {
    private final Adventurer mob;
    private final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
    private BlockPos current;
    private int breakingTicks;
    private int breakingTotal;

    private BlockPos lastProgressPos;
    private boolean showingProgress;

    private int swingCooldown;

    public MineBlockGoal(Adventurer mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return !mob.level().isClientSide && !queue.isEmpty() && mob.getActionMode() != ActionMode.IDLE;
    }

    @Override
    public boolean canContinueToUse() {
        return !queue.isEmpty() && mob.getActionMode() != ActionMode.IDLE;
    }

    public void enqueue(BlockPos p) {
        if (p != null) queue.add(p.immutable());
    }

    public void clearQueue() {
        queue.clear();
        endProgress();
        current = null;
        breakingTicks = 0;
        breakingTotal = 0;
    }

    @Override
    public void stop() {
        endProgress();
        swingCooldown = 0;
        current = null;
        breakingTicks = 0;
        breakingTotal = 0;
    }

    @Override
    public void tick() {
        if (current == null) {
            while (!queue.isEmpty() && current == null) {
                BlockPos next = queue.removeFirst();
                if (mob.isValidWorkPos(next) && !mob.level().getBlockState(next).isAir()) current = next;
            }
            if (current == null) {
                endProgress(); return;
            }
            breakingTicks = 0;
            breakingTotal = computeBreakTimeTicks(mob.getMainHandItem(), mob.level().getBlockState(current), current);
            if (breakingTotal <= 0) breakingTotal = 1;
            beginProgress();
            return;
        }

        if (!mob.isValidWorkPos(current)) {
            endProgress();
            current = null;
            breakingTicks = 0;
            return;
        }

        BlockState st = mob.level().getBlockState(current);
        if (st.isAir()) {
            endProgress();
            current = null;
            breakingTicks = 0;
            return;
        }

        mob.getLookControl().setLookAt(current.getX() + 0.5, current.getY() + 0.5, current.getZ() + 0.5);
        breakingTicks++;
        if (!mob.level().isClientSide) {
            if (swingCooldown <= 0) {
                mob.swing(InteractionHand.MAIN_HAND, true);
                swingCooldown = 2 + mob.getRandom().nextInt(4);
            } else {
                swingCooldown--;
            }
        }
        int stage = (int) Math.floor((breakingTicks / (double) breakingTotal) * 10.0);
        tickProgress(stage);


        if (breakingTicks >= breakingTotal) {
            AiUtil.harvestBlockToInternal(mob, current);
            endProgress();
            current = null;
            breakingTicks = 0;
            breakingTotal = 0;
        }
    }

    private void startProgress(BlockPos current) {
        if (!(mob.level() instanceof ServerLevel sl)) return;
        if (current == null) return;

        sl.destroyBlockProgress(mob.getId(), current, 0);
        lastProgressPos = current.immutable();
        showingProgress = true;

        // Start swinging immediately and then every ~6–9 ticks
        swingCooldown = 0;
    }

    private int computeBreakTimeTicks(ItemStack tool, BlockState state, BlockPos pos) {
        float hardness = state.getDestroySpeed(mob.level(), pos);
        if (hardness < 0) return 0;
        float speed = 1.0F;
        if (!tool.isEmpty()) {
            float s = tool.getDestroySpeed(state);
            if (s > 1.0F) speed = s;
        }
        if (speed <= 0.0F) speed = 0.5F;
        float t = Math.max(1.0F, 20.0F * hardness / speed);
        if (t > 200F) t = 200F;
        return (int) Math.ceil(t);
    }

    private void beginProgress() {
        if (!(mob.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        if (current == null) return;
        sl.destroyBlockProgress(mob.getId(), current, 0);
        lastProgressPos = current.immutable();
        showingProgress = true;
        swingCooldown = 0;

    }


    private void tickProgress(int stage) {
        if (!(mob.level() instanceof ServerLevel sl)) return;
        if (current == null) return;
        int clamped = Math.max(0, Math.min(9, stage));
        sl.destroyBlockProgress(mob.getId(), current, clamped);
        showingProgress = true;
    }

    private void endProgress() {
        if (!(mob.level() instanceof ServerLevel sl)) return;
        if (lastProgressPos != null && showingProgress) {
            sl.destroyBlockProgress(mob.getId(), lastProgressPos, -1);
        }
        lastProgressPos = null;
        showingProgress = false;
    }
}
