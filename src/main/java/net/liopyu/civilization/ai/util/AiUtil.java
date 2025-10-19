package net.liopyu.civilization.ai.util;

import com.mojang.logging.LogUtils;
import net.liopyu.civilization.ai.ActionMode;
import net.liopyu.civilization.entity.Adventurer;
import net.liopyu.civilization.mixin.BlockBehaviourAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;

public final class AiUtil {
    private static final java.util.WeakHashMap<java.util.UUID, java.util.ArrayDeque<net.minecraft.core.BlockPos>> TEMP_PILLARS = new java.util.WeakHashMap<>();

    private static java.util.ArrayDeque<net.minecraft.core.BlockPos> getPillarStack(net.liopyu.civilization.entity.Adventurer mob) {
        return TEMP_PILLARS.computeIfAbsent(mob.getUUID(), k -> new java.util.ArrayDeque<>());
    }

    public static void markTemporaryPillar(net.liopyu.civilization.entity.Adventurer mob, net.minecraft.core.BlockPos pos) {
        java.util.ArrayDeque<net.minecraft.core.BlockPos> dq = getPillarStack(mob);
        dq.addLast(pos.immutable());
    }

    public static boolean isTemporaryPillar(net.liopyu.civilization.entity.Adventurer mob, net.minecraft.core.BlockPos pos) {
        java.util.ArrayDeque<net.minecraft.core.BlockPos> dq = TEMP_PILLARS.get(mob.getUUID());
        return dq != null && dq.contains(pos);
    }

    public static java.util.List<net.minecraft.core.BlockPos> drainTemporaryPillarsLifo(net.liopyu.civilization.entity.Adventurer mob) {
        java.util.ArrayDeque<net.minecraft.core.BlockPos> dq = TEMP_PILLARS.remove(mob.getUUID());
        if (dq == null || dq.isEmpty()) return java.util.Collections.emptyList();
        java.util.ArrayList<net.minecraft.core.BlockPos> out = new java.util.ArrayList<>(dq.size());
        while (!dq.isEmpty()) out.add(dq.removeLast());
        return out;
    }


    private AiUtil() {
    }

    public static boolean isLog(BlockState s) {
        return s != null && s.is(BlockTags.LOGS);
    }

    public static boolean isLeaves(BlockState s) {
        return s != null && s.is(BlockTags.LEAVES);
    }

    public static net.minecraft.core.BlockPos findNearestLogAvoidingOthers(net.liopyu.civilization.entity.Adventurer mob, int r, double avoidRadius) {
        net.minecraft.core.BlockPos base = mob.blockPosition();
        net.minecraft.core.BlockPos best = null; double bestD = Double.MAX_VALUE;
        for (int dx = -r; dx <= r; dx++)
            for (int dy = -r; dy <= r; dy++)
                for (int dz = -r; dz <= r; dz++) {
                    net.minecraft.core.BlockPos p = base.offset(dx, dy, dz);
                    net.minecraft.world.level.block.state.BlockState st = mob.level().getBlockState(p);
                    if (!st.is(net.minecraft.tags.BlockTags.LOGS)) continue;
                    net.minecraft.core.BlockPos treeBase = findTreeBase(mob.level(), p);
                    java.util.List<net.liopyu.civilization.entity.Adventurer> others =
                            mob.level().getEntitiesOfClass(
                                    net.liopyu.civilization.entity.Adventurer.class,
                                    new net.minecraft.world.phys.AABB(treeBase).inflate(avoidRadius),
                                    a -> a != mob && a.getActionMode() == ActionMode.GATHER_WOOD
                            );
                    if (!others.isEmpty()) continue;
                    double d = p.distSqr(base);
                    if (d < bestD) {
                        bestD = d; best = p.immutable();
                    }
                }
        return best;
    }


    public static boolean harvestBlockToInternal(Adventurer mob, BlockPos pos) {
        var level = mob.level();
        var st = level.getBlockState(pos);
        if (st.isAir()) return false;

        var tool = mob.getMainHandItem();
        if (!tool.isEmpty()) tool.getItem().mineBlock(tool, level, st, pos, mob);

        if (!(level instanceof net.minecraft.server.level.ServerLevel sl)) return false;

        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
        LootParams.Builder lp =
                new LootParams.Builder(sl)
                        .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,
                                net.minecraft.world.phys.Vec3.atCenterOf(pos))
                        .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_STATE, st)
                        .withOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY, be)
                        .withOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY, mob)
                        .withOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL, tool);
        var drops = ((BlockBehaviourAccessor) st.getBlock()).invokeGetDrops(st, lp);


        boolean any = false;
        for (net.minecraft.world.item.ItemStack drop : drops) {
            if (!drop.isEmpty()) {
                if (mob.addItemToInternal(drop.copy())) any = true;
            }
        }

        level.removeBlock(pos, false);
        return any;
    }

    public static BlockPos findTreeBase(Level level, BlockPos anyLog) {
        BlockPos p = anyLog;
        while (isLog(level.getBlockState(p.below()))) p = p.below();
        return p.immutable();
    }

    public static BlockPos findStandNear(Level level, BlockPos target, int radius) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos feet = target.offset(dx, dy, dz);
                    if (!level.isLoaded(feet)) continue;
                    if (!level.isEmptyBlock(feet)) continue;
                    if (!level.getBlockState(feet.below()).isSolid()) continue;
                    return feet.immutable();
                }
            }
        }
        return null;
    }

    public static boolean peekHasPlaceableBlock(net.liopyu.civilization.entity.Adventurer mob) {
        net.minecraft.world.item.ItemStack hand = mob.getMainHandItem();
        if (hand.getItem() instanceof net.minecraft.world.item.BlockItem && hand.getCount() > 0) return true;
        for (int i = 0; i < mob.getInternalInventory().size(); i++) {
            net.minecraft.world.item.ItemStack s = mob.getInternalInventory().get(i);
            if (s.getItem() instanceof net.minecraft.world.item.BlockItem && s.getCount() > 0) return true;
        }
        return false;
    }

    public static net.minecraft.world.item.ItemStack takePlaceableBlock(net.liopyu.civilization.entity.Adventurer mob) {
        net.minecraft.world.item.ItemStack hand = mob.getMainHandItem();
        if (hand.getItem() instanceof net.minecraft.world.item.BlockItem && hand.getCount() > 0) {
            net.minecraft.world.item.ItemStack out = hand.copy();
            out.setCount(1);
            hand.shrink(1);
            return out;
        }
        for (int i = 0; i < mob.getInternalInventory().size(); i++) {
            net.minecraft.world.item.ItemStack s = mob.getInternalInventory().get(i);
            if (s.getItem() instanceof net.minecraft.world.item.BlockItem && s.getCount() > 0) {
                net.minecraft.world.item.ItemStack out = s.copy();
                out.setCount(1);
                s.shrink(1);
                return out;
            }
        }
        return net.minecraft.world.item.ItemStack.EMPTY;
    }

    public static boolean pillarUpOneBlock(net.liopyu.civilization.entity.Adventurer mob, int delayTicks) {
        if (!(mob.level() instanceof net.minecraft.server.level.ServerLevel sl)) return false;
        if (!mob.onGround()) return false;
        if (!peekHasPlaceableBlock(mob)) return false;
        net.minecraft.core.BlockPos placePos = mob.blockPosition();
        mob.getNavigation().stop();
        mob.getJumpControl().jump();
        net.minecraft.server.MinecraftServer server = sl.getServer();
        int when = server.getTickCount() + Math.max(1, delayTicks);
        server.tell(new net.minecraft.server.TickTask(when, () -> {
            if (mob.isRemoved()) return;
            if (!sl.isLoaded(placePos)) return;
            net.minecraft.world.level.block.state.BlockState cur = sl.getBlockState(placePos);
            if (!cur.isAir()) {
                if (!cur.canBeReplaced()) {
                    if (cur.getCollisionShape(sl, placePos).isEmpty()) {
                        harvestBlockToInternal(mob, placePos);
                    } else {
                        return;
                    }
                }
            }
            net.minecraft.world.item.ItemStack stack = takePlaceableBlock(mob);
            if (stack.isEmpty()) return;
            if (!(stack.getItem() instanceof net.minecraft.world.item.BlockItem bi)) return;
            net.minecraft.world.level.block.state.BlockState state = bi.getBlock().defaultBlockState();
            if (sl.setBlock(placePos, state, 3)) {
                markTemporaryPillar(mob, placePos);
            }
        }));

        return true;
    }


    public static java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> obstaclePredicateForMode(net.liopyu.civilization.entity.Adventurer mob) {
        net.liopyu.civilization.ai.ActionMode mode = mob.getActionMode();
        switch (mode) {
            case GATHER_WOOD:
                return net.liopyu.civilization.ai.util.AiUtil::isLeaves;
            default:
                return s -> s != null && (s.canBeReplaced() || s.is(net.minecraft.tags.BlockTags.LEAVES) || s.is(net.minecraft.world.level.block.Blocks.COBWEB));
        }
    }

    public static net.minecraft.core.BlockPos firstBlockingAlong(net.liopyu.civilization.entity.Adventurer mob, net.minecraft.core.BlockPos target, java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> predicate) {
        net.minecraft.world.phys.Vec3 start = mob.getEyePosition(1.0F);
        net.minecraft.world.phys.Vec3 end = new net.minecraft.world.phys.Vec3(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        net.minecraft.world.level.ClipContext ctx = new net.minecraft.world.level.ClipContext(start, end, net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, mob);
        net.minecraft.world.phys.HitResult hit = mob.level().clip(ctx);
        if (hit == null || hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return null;
        net.minecraft.core.BlockPos hp = ((net.minecraft.world.phys.BlockHitResult) hit).getBlockPos();
        if (hp.equals(target)) return null;
        net.minecraft.world.level.block.state.BlockState st = mob.level().getBlockState(hp);
        return predicate.test(st) ? hp.immutable() : null;
    }

    public static boolean isAABBPassableForEntity(net.liopyu.civilization.entity.Adventurer mob, net.minecraft.world.phys.AABB boxUp) {
        return mob.level().noCollision(mob, boxUp);
    }

    public static net.minecraft.core.BlockPos firstBlockingInAABB(net.liopyu.civilization.entity.Adventurer mob, net.minecraft.world.phys.AABB boxUp, java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> predicate) {
        int minX = net.minecraft.util.Mth.floor(boxUp.minX);
        int minY = net.minecraft.util.Mth.floor(boxUp.minY);
        int minZ = net.minecraft.util.Mth.floor(boxUp.minZ);
        int maxX = net.minecraft.util.Mth.floor(boxUp.maxX);
        int maxY = net.minecraft.util.Mth.floor(boxUp.maxY);
        int maxZ = net.minecraft.util.Mth.floor(boxUp.maxZ);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    net.minecraft.core.BlockPos p = new net.minecraft.core.BlockPos(x, y, z);
                    if (!mob.level().isLoaded(p)) continue;
                    net.minecraft.world.level.block.state.BlockState st = mob.level().getBlockState(p);
                    if (st.isAir()) continue;
                    if (predicate.test(st)) return p.immutable();
                }
            }
        }
        return null;
    }

    public static @org.jetbrains.annotations.Nullable net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> woodFamilyTag(net.minecraft.world.level.block.state.BlockState st) {
        if (st == null) return null;
        if (!st.is(net.minecraft.tags.BlockTags.LOGS)) return null;
        net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block>[] families = new net.minecraft.tags.TagKey[]{
                net.minecraft.tags.BlockTags.OAK_LOGS,
                net.minecraft.tags.BlockTags.BIRCH_LOGS,
                net.minecraft.tags.BlockTags.SPRUCE_LOGS,
                net.minecraft.tags.BlockTags.JUNGLE_LOGS,
                net.minecraft.tags.BlockTags.ACACIA_LOGS,
                net.minecraft.tags.BlockTags.DARK_OAK_LOGS,
                net.minecraft.tags.BlockTags.MANGROVE_LOGS,
                net.minecraft.tags.BlockTags.CRIMSON_STEMS,
                net.minecraft.tags.BlockTags.WARPED_STEMS,
                net.minecraft.tags.BlockTags.CHERRY_LOGS,
                net.minecraft.tags.BlockTags.BAMBOO_BLOCKS
        };
        for (var t : families) if (st.is(t)) return t;
        return net.minecraft.tags.BlockTags.LOGS;
    }

    public static boolean isSameWoodFamily(net.minecraft.world.level.block.state.BlockState st, net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> family) {
        return family != null && st != null && st.is(family);
    }

    public static void aiLogger(String string) {
        //if (true) return;
        LogUtils.getLogger().info(string);
    }

}
