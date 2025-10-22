package net.liopyu.civilization.ai.goal.active;

import net.liopyu.civilization.ai.core.*;
import net.liopyu.civilization.ai.goal.ModeScopedGoal;
import net.liopyu.civilization.entity.Adventurer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public final class BuildShelterGoal extends ModeScopedGoal {
    private ResourceLocation chosenPlanId;
    private BuildPlan plan;
    private List<BuildPlan.Step> sequence;
    private int idx;
    private int placeCooldown;
    private int repathCooldown;
    private BlockPos siteCenter;
    private boolean siteLocked;
    private boolean needsQueued;
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private int lastGatherReqTick;

    public BuildShelterGoal(Adventurer a) {
        super(a, ActionMode.BUILD_SHELTER);
    }

    private Map<Item, Integer> missingItems(Map<Item, Integer> req) {
        Map<Item, Integer> miss = new java.util.HashMap<>();
        for (var e : req.entrySet()) {
            int have = adv.countItem(e.getKey());
            int need = e.getValue();
            if (have < need) miss.put(e.getKey(), need - have);
        }
        return miss;
    }

    @Override
    protected void onEnter(ActionMode mode) {
        chosenPlanId = BuildPlanSelector.selectFor(adv.getType()).orElse(null);
        plan = null;
        sequence = Collections.emptyList();
        idx = 0;
        placeCooldown = 0;
        repathCooldown = 0;
        siteCenter = null;
        siteLocked = false;
        needsQueued = false;
        lastGatherReqTick = 0;
    }

    @Override
    protected void onExit(ActionMode mode) {
        adv.getNavigation().stop();
    }

    @Override
    public boolean canUse() {
        return adv.getActionMode() == ActionMode.BUILD_SHELTER && !adv.hasHome();
    }

    @Override
    public boolean canContinueToUse() {
        return adv.getActionMode() == ActionMode.BUILD_SHELTER && !adv.hasHome();
    }

    @Override
    public void tick() {
        boolean tick20 = (adv.tickCount % 20) == 0;

        if (adv.hasHome()) {
            if (tick20) LOG.info("[Shelter] {} has home, switching to EXPLORE", adv.getStringUUID());
            adv.controller().requestMode(ActionMode.EXPLORE, 800, 80, 20);
            return;
        }

        if (chosenPlanId == null) {
            if (tick20) LOG.info("[Shelter] {} no plan id", adv.getStringUUID());
            return;
        }
        if (!(adv.level() instanceof net.minecraft.server.level.ServerLevel sl)) {
            if (tick20) LOG.info("[Shelter] {} not server level", adv.getStringUUID());
            return;
        }

        if (plan == null) {
            plan = BuildPlan.fromStructure(sl, chosenPlanId, true);
            sequence = plan.steps();
            idx = 0;
            if (tick20)
                LOG.info("[Shelter] {} loaded plan {} steps={}", adv.getStringUUID(), chosenPlanId, sequence.size());
        }
        if (sequence.isEmpty()) {
            if (tick20) LOG.info("[Shelter] {} empty plan, switching to SET_HOME", adv.getStringUUID());
            adv.controller().requestMode(ActionMode.SET_HOME, 800, 80, 20);
            return;
        }

        if (!siteLocked) {
            siteCenter = chooseSiteByHeightmap(adv, sequence, 12);
            if (siteCenter == null) return;
            for (var s : sequence) adv.protectedBlocks().protect(siteCenter.offset(s.rel));
            siteLocked = true;

            if (needsExcavation(siteCenter, sequence)) {
                var req = makeExcavationBox(siteCenter, sequence);
                if (tick20)
                    LOG.info("[Shelter] {} site needs excavation {} -> {}", adv.getStringUUID(), req.min, req.max);
                adv.controller().requestExcavation(req);
                adv.controller().requestMode(ActionMode.EXCAVATE, 950, 200, 40);
                return;
            } else {
                if (tick20) LOG.info("[Shelter] {} site locked at {}", adv.getStringUUID(), siteCenter);
            }
        }

        var req = plan.requiredItems();
        if (!hasAllRequired(req)) {
            if (adv.tickCount % 20 == 0)
                LOG.info("[Shelter] {} missing items {}", adv.getStringUUID(), missingItems(req));
            if (!needsQueued) {
                queueDeficits(req);
                needsQueued = true;
                if (adv.tickCount % 20 == 0) LOG.info("[Shelter] {} queued deficits", adv.getStringUUID());
            }
            if (adv.tickCount - lastGatherReqTick >= 40) {
                adv.controller().requestMode(ActionMode.GATHER_MINEABLE, 900, 120, 40);
                lastGatherReqTick = adv.tickCount;
            }
            return;
        }

        if (idx >= sequence.size()) {
            if (tick20) LOG.info("[Shelter] {} finished placement, switching to SET_HOME", adv.getStringUUID());
            adv.controller().requestMode(ActionMode.SET_HOME, 800, 80, 20);
            return;
        }

        if (placeCooldown > 0) placeCooldown--;

        var step = sequence.get(idx);
        var p = siteCenter.offset(step.rel);
        if (!adv.isValidWorkPos(p)) {
            if (tick20) LOG.info("[Shelter] {} invalid work pos {}, skipping", adv.getStringUUID(), p);
            idx++;
            return;
        }

        Level lvl = adv.level();
        BlockState current = lvl.getBlockState(p);
        if (current.getBlock() == step.state.getBlock()) {
            if (tick20) LOG.info("[Shelter] {} already placed at {}, advancing", adv.getStringUUID(), p);
            idx++;
            return;
        }
        if (!isPlaceable(lvl, p)) {
            if (tick20) LOG.info("[Shelter] {} not placeable at {}, skipping", adv.getStringUUID(), p);
            idx++;
            return;
        }

        double reach = adv.entityInteractionRange() + 1.25;
        if (!net.liopyu.civilization.ai.nav.UniversalReach.reach(
                adv, p, reach,
                net.liopyu.civilization.ai.nav.NavTask.ALLOW_TUNNEL,
                920, 20 * 10)) return;

        adv.getLookControl().setLookAt(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
        if (placeCooldown > 0) return;

        Item needItem = step.state.getBlock().asItem();
        if (needItem == net.minecraft.world.item.Items.AIR) {
            if (tick20) LOG.info("[Shelter] {} AIR step at {}, skipping", adv.getStringUUID(), p);
            idx++;
            return;
        }
        if (!adv.consumeItems(needItem, 1)) {
            if (tick20)
                LOG.info("[Shelter] {} cannot consume {} at step {}, holding", adv.getStringUUID(), needItem, idx);
            return;
        }

        if (adv.tickCount % 6 == 0) adv.swing(InteractionHand.MAIN_HAND);
        lvl.setBlock(p, step.state, 3);
        adv.controller().requestPickup(PickupRequest.any(adv.tickCount, 40, 6).withNear(p));
        if (tick20)
            LOG.info("[Shelter] {} placed {} at {} (step {}/{})", adv.getStringUUID(), step.state.getBlock().toString(), p, idx + 1, sequence.size());

        placeCooldown = 6;
        idx++;
    }


    private static BlockPos chooseSiteGrounded(Adventurer a, int radius, List<BuildPlan.Step> seq) {
        var lvl = a.level();
        var o = a.blockPosition();
        int r = Math.max(3, radius);

        int minRelY = Integer.MAX_VALUE;
        for (var s : seq) if (s.state.isSolid() || !s.state.isAir()) minRelY = Math.min(minRelY, s.rel.getY());
        if (minRelY == Integer.MAX_VALUE) minRelY = 0;

        BlockPos best = null;
        double bestD2 = Double.MAX_VALUE;

        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                var baseXZ = o.offset(x, 0, z);
                int groundY = findSurfaceY(lvl, baseXZ);
                if (groundY == Integer.MIN_VALUE) continue;

                var candidateCenter = new BlockPos(baseXZ.getX(), groundY + 1 - minRelY, baseXZ.getZ());
                if (!areaOk(a, candidateCenter, seq)) continue;

                double d2 = a.distanceToSqr(candidateCenter.getX() + 0.5, candidateCenter.getY() + 0.02, candidateCenter.getZ() + 0.5);
                if (d2 < bestD2) {
                    bestD2 = d2; best = candidateCenter.immutable();
                }
            }
        }
        return best;
    }

    private static int findSurfaceY(Level lvl, BlockPos xz) {
        int y = xz.getY();
        int minY = lvl.getMinBuildHeight(), maxY = lvl.getMaxBuildHeight() - 2;
        y = Math.max(minY + 1, Math.min(maxY, y));

        BlockPos pos = new BlockPos(xz.getX(), y, xz.getZ());
        int tries = 0;

        while (tries++ < 24 && y > minY + 1) {
            var below = pos.below();
            if (lvl.getBlockState(below).isSolidRender(lvl, below) && lvl.getBlockState(pos).isAir()) return y;
            pos = below; y--;
        }
        tries = 0; pos = new BlockPos(xz.getX(), y, xz.getZ());
        while (tries++ < 24 && y < maxY) {
            var below = pos.below();
            if (lvl.getBlockState(below).isSolidRender(lvl, below) && lvl.getBlockState(pos).isAir()) return y;
            pos = pos.above(); y++;
        }
        return Integer.MIN_VALUE;
    }

    private boolean hasAllRequired(Map<Item, Integer> req) {
        for (Map.Entry<Item, Integer> e : req.entrySet()) if (adv.countItem(e.getKey()) < e.getValue()) return false;
        return true;
    }

    private void queueDeficits(Map<Item, Integer> req) {
        int woodDeficit = 0;
        Set<Item> mineItems = new HashSet<>();
        Set<Block> mineBlocks = new HashSet<>();

        for (Map.Entry<Item, Integer> e : req.entrySet()) {
            Item item = e.getKey();
            int need = e.getValue();
            int have = adv.countItem(item);
            int shortfall = Math.max(0, need - have);
            if (shortfall == 0) continue;

            // Classify by block state & tags
            Block b = Block.byItem(item);
            BlockState st = (b != null) ? b.defaultBlockState() : null;

            // Wood family (logs/planks/bamboo stems etc.) -> gather wood
            boolean isWood = st != null && (
                    st.is(net.minecraft.tags.BlockTags.LOGS) ||
                            st.is(net.minecraft.tags.BlockTags.PLANKS) ||
                            st.is(net.minecraft.tags.BlockTags.BAMBOO_BLOCKS) ||
                            st.is(net.minecraft.tags.BlockTags.WOODEN_FENCES) ||
                            st.is(net.minecraft.tags.BlockTags.WOODEN_SLABS) ||
                            st.is(net.minecraft.tags.BlockTags.WOODEN_STAIRS)
            );
            if (isWood) {
                woodDeficit += shortfall;
                continue;
            }

            // Pickaxe-minable (stone, deepslate, ores, etc.) -> mineable pipeline
            boolean pickaxeMinable = st != null && st.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE);
            if (pickaxeMinable) {
                mineItems.add(item);
                mineBlocks.add(b);
                continue;
            }

            // TODO: shovel-minable (dirt/sand/gravel): either:
            //  a) add a GatherDiggableGoal, or
            //  b) relax GatherMineable to accept MINEABLE_WITH_SHOVEL if the Need says so.
            // For now, treat common fillers as mineables if you want:
            // if (st != null && st.is(BlockTags.DIRT) || st.is(Blocks.SAND) || st.is(Blocks.GRAVEL)) { ... }
        }

        // Kick the miner only for pickaxe stuff
        if (!mineItems.isEmpty()) {
            int deficitCount = 0;
            for (Item it : mineItems) {
                int have = adv.countItem(it);
                int need = req.getOrDefault(it, 0);
                deficitCount += Math.max(0, need - have);
            }
            if (deficitCount > 0) {
                adv.controller().planner().requestMineable(
                        ResourceLocation.fromNamespaceAndPath("civilization", "buildplan_" + chosenPlanId.getPath()),
                        mineItems, java.util.Set.of(), mineBlocks, java.util.Set.of(),
                        deficitCount, 800
                );
                // leave mode switching to your outer tick, or you can nudge:
                adv.controller().requestMode(ActionMode.GATHER_MINEABLE, 900, 120, 40);
            }
        }

        // Kick the woodcutter if we need wood-like things
        if (woodDeficit > 0) {
            // You can be fancy and pass a wood family tag to the planner,
            // but a simple nudge works because GatherWoodGoal already handles finding trees.
            adv.controller().requestMode(ActionMode.GATHER_WOOD, 880, 100, 20);
        }
    }

    private static boolean isPlaceable(Level lvl, BlockPos p) {
        var here = lvl.getBlockState(p);
        return here.isAir() || here.canBeReplaced();
    }

    private static BlockPos chooseSite(Adventurer a, int radius, List<BuildPlan.Step> seq) {
        BlockPos o = a.blockPosition();
        BlockPos best = null;
        double bestD2 = Double.MAX_VALUE;
        int r = Math.max(2, radius);
        for (int y = -2; y <= 2; y++) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos c = o.offset(x, y, z);
                    if (!a.isValidWorkPos(c)) continue;
                    if (!areaOk(a, c, seq)) continue;
                    double d2 = a.distanceToSqr(c.getX() + 0.5, c.getY() + 0.02, c.getZ() + 0.5);
                    if (d2 < bestD2) {
                        bestD2 = d2; best = c.immutable();
                    }
                }
            }
        }
        return best;
    }

    private static boolean areaOk(Adventurer a, BlockPos center, List<BuildPlan.Step> seq) {
        var lvl = a.level();
        for (var s : seq) {
            var p = center.offset(s.rel);
            if (!a.isValidWorkPos(p)) return false;
            var here = lvl.getBlockState(p);
            if (!(here.isAir() || here.canBeReplaced())) return false;
            var below = p.below();
            if (!lvl.getBlockState(below).isSolidRender(lvl, below)) return false;
            if (a.protectedBlocks().isProtected(p)) return false;
        }
        return true;
    }

    private static net.minecraft.core.BlockPos chooseSiteByHeightmap(Adventurer a, java.util.List<BuildPlan.Step> seq, int searchRadius) {
        var lvl = a.level();
        var o = a.blockPosition();
        int r = Math.max(3, searchRadius);
        int minRelY = Integer.MAX_VALUE;
        for (var s : seq) minRelY = Math.min(minRelY, s.rel.getY());
        if (minRelY == Integer.MAX_VALUE) minRelY = 0;

        net.minecraft.core.BlockPos best = null;
        double bestD2 = Double.MAX_VALUE;

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int x = o.getX() + dx, z = o.getZ() + dz;
                int surfaceY = lvl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                var center = new net.minecraft.core.BlockPos(x, surfaceY + 1 - minRelY, z);
                double d2 = a.distanceToSqr(center.getX() + 0.5, center.getY() + 0.02, center.getZ() + 0.5);
                if (d2 < bestD2) {
                    bestD2 = d2; best = center.immutable();
                }
            }
        }
        return best;
    }

    private boolean needsExcavation(net.minecraft.core.BlockPos center, java.util.List<BuildPlan.Step> seq) {
        var lvl = adv.level();
        for (var s : seq) {
            var p = center.offset(s.rel);
            if (!adv.isValidWorkPos(p)) return true;
            var here = lvl.getBlockState(p);
            if (!(here.isAir() || here.canBeReplaced())) return true;
            var below = p.below();
            if (!lvl.getBlockState(below).isSolidRender(lvl, below)) return true;
        }
        return false;
    }

    private ExcavationRequest makeExcavationBox(net.minecraft.core.BlockPos center, java.util.List<BuildPlan.Step> seq) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (var s : seq) {
            var p = center.offset(s.rel);
            minX = Math.min(minX, p.getX()); minY = Math.min(minY, p.getY()); minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX()); maxY = Math.max(maxY, p.getY()); maxZ = Math.max(maxZ, p.getZ());
        }
        var min = new net.minecraft.core.BlockPos(minX, minY, minZ);
        var max = new net.minecraft.core.BlockPos(maxX, maxY + 1, maxZ);
        return ExcavationRequest.of(min, max, 900, adv.tickCount, 20 * 20);
    }

}
