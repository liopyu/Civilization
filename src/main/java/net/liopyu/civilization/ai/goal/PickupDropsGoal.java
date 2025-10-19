package net.liopyu.civilization.ai.goal;

import net.liopyu.civilization.ai.core.PickupRequest;
import net.liopyu.civilization.entity.Adventurer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;
import java.util.List;

public final class PickupDropsGoal extends Goal {
    private final Adventurer adv;
    private ItemEntity target;
    private int repathCooldown;
    private int searchCooldown;

    public PickupDropsGoal(Adventurer adv) {
        this.adv = adv;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        PickupRequest r = adv.controller().getPickupRequest();
        if (r == null) return false;
        if (target == null || !isValidTarget(r, target)) {
            if (searchCooldown <= 0) {
                target = findBestTarget(r);
                searchCooldown = 5;
            } else searchCooldown--;
        }
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        PickupRequest r = adv.controller().getPickupRequest();
        return r != null && target != null && isValidTarget(r, target);
    }

    @Override
    public void start() {
        repathCooldown = 0;
    }

    @Override
    public void stop() {
        target = null;
        repathCooldown = 0;
    }

    @Override
    public void tick() {
        PickupRequest r = adv.controller().getPickupRequest();
        if (r == null) {
            target = null;
            return;
        }
        if (target == null || !isValidTarget(r, target)) {
            target = findBestTarget(r);
            if (target == null) {
                adv.controller().clearPickup();
                return;
            }
        }
        double tx = target.getX();
        double ty = target.getY();
        double tz = target.getZ();
        double rr = adv.entityInteractionRange() + 1.0;
        double d2 = adv.distanceToSqr(tx, ty, tz);

        if (d2 > rr * rr) {
            if (repathCooldown <= 0 || adv.getNavigation().isDone()) {
                adv.getNavigation().moveTo(tx, ty, tz, 1.1);
                repathCooldown = 6;
            } else repathCooldown--;
            adv.getLookControl().setLookAt(tx, ty, tz);
            return;
        }

        adv.getNavigation().stop();
        ItemStack s = target.getItem();
        if (!s.isEmpty()) {
            if (adv.addItemToInternal(s)) target.discard();
            else target.setItem(s);
        }
        target = null;
        if (findBestTarget(r) == null) adv.controller().clearPickup();
    }

    private ItemEntity findBestTarget(PickupRequest r) {
        double rad = Math.max(2.0, r.radius);
        AABB box = adv.getBoundingBox().inflate(rad);
        List<ItemEntity> list = adv.level().getEntitiesOfClass(ItemEntity.class, box, e -> isValidTarget(r, e));
        if (list.isEmpty()) return null;
        ItemEntity best = null;
        double bestD2 = Double.MAX_VALUE;
        for (ItemEntity e : list) {
            double d2 = adv.distanceToSqr(e);
            if (d2 < bestD2) {
                bestD2 = d2;
                best = e;
            }
        }
        return best;
    }

    private boolean isValidTarget(PickupRequest r, ItemEntity e) {
        if (e == null || !e.isAlive() || e.getItem().isEmpty()) return false;
        if (r.nearHint != null && e.distanceToSqr(r.nearHint.getX() + 0.5, r.nearHint.getY() + 0.5, r.nearHint.getZ() + 0.5) > (r.radius * r.radius))
            return false;
        ItemStack s = e.getItem();
        if (!r.items.isEmpty()) return r.items.contains(s.getItem());
        if (!r.tags.isEmpty()) {
            Item it = s.getItem();
            for (TagKey<Item> t : r.tags) if (it.builtInRegistryHolder().is(t)) return true;
            return false;
        }
        return true;
    }
}
