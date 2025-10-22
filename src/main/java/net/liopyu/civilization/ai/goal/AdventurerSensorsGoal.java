package net.liopyu.civilization.ai.goal;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.liopyu.civilization.ai.core.AdventurerKeys;
import net.liopyu.civilization.entity.Adventurer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Items;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public final class AdventurerSensorsGoal extends Goal {
    private static final Logger LOG = LogUtils.getLogger();

    private final Adventurer adv;
    private boolean flip;

    private boolean lastHome;
    private boolean lastHungry;
    private boolean lastNight;
    private boolean lastUnderAttack;
    private int lastLogs = -1;
    private int lastStones = -1;
    private int lastInvHash;

    public AdventurerSensorsGoal(Adventurer adv) {
        this.adv = adv;
        this.setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return true;
    }

    @Override
    public void tick() {
        boolean hasHome = adv.hasHome();
        int logs = adv.countItem(Items.OAK_LOG) + adv.countItem(Items.SPRUCE_LOG) + adv.countItem(Items.BIRCH_LOG) + adv.countItem(Items.JUNGLE_LOG) + adv.countItem(Items.ACACIA_LOG) + adv.countItem(Items.DARK_OAK_LOG) + adv.countItem(Items.MANGROVE_LOG) + adv.countItem(Items.CHERRY_LOG) + adv.countItem(Items.CRIMSON_STEM) + adv.countItem(Items.WARPED_STEM);
        int stones = adv.countItem(Items.COBBLESTONE) + adv.countItem(Items.COBBLED_DEEPSLATE) + adv.countItem(Items.STONE);
        boolean hungry = adv.getHealth() < adv.getMaxHealth() * 0.5f;
        boolean night = !adv.level().isDay();
        boolean underAttack = adv.getLastHurtByMob() != null;

        adv.controller().set(AdventurerKeys.HAS_HOME, hasHome);
        adv.controller().set(AdventurerKeys.WOOD_COUNT, logs);
        adv.controller().set(AdventurerKeys.STONE_COUNT, stones);
        adv.controller().set(AdventurerKeys.HUNGRY, hungry);
        adv.controller().set(AdventurerKeys.NIGHT, night);
        adv.controller().set(AdventurerKeys.UNDER_ATTACK, underAttack);

        Map<net.minecraft.world.item.Item, Integer> inv = new HashMap<>();
        int totalItems = 0;
        for (net.minecraft.world.item.ItemStack s : adv.getInternalInventory()) {
            if (!s.isEmpty()) {
                inv.merge(s.getItem(), s.getCount(), Integer::sum);
                totalItems += s.getCount();
            }
        }
        adv.controller().planner().reconcile(inv);

        if (hasHome != lastHome) {
            LOG.info("[Sense] {} home={}", adv.getStringUUID(), hasHome);
            lastHome = hasHome;
        }
        if (hungry != lastHungry) {
            LOG.info("[Sense] {} hungry={}", adv.getStringUUID(), hungry);
            lastHungry = hungry;
        }
        if (night != lastNight) {
            LOG.info("[Sense] {} night={}", adv.getStringUUID(), night);
            lastNight = night;
        }
        if (underAttack != lastUnderAttack) {
            LOG.info("[Sense] {} underAttack={}", adv.getStringUUID(), underAttack);
            lastUnderAttack = underAttack;
        }
        if (logs != lastLogs) {
            LOG.info("[Sense] {} logs={}", adv.getStringUUID(), logs);
            lastLogs = logs;
        }
        if (stones != lastStones) {
            LOG.info("[Sense] {} stones={}", adv.getStringUUID(), stones);
            lastStones = stones;
        }

        int invHash = inv.hashCode();
        if (invHash != lastInvHash && (adv.tickCount % 20) == 0) {
            LOG.info("[Sense] {} invKinds={} invTotal={}", adv.getStringUUID(), inv.size(), totalItems);
            lastInvHash = invHash;
        }

        if ((adv.tickCount % 120) == 0) {
            LOG.info("[Sense] {} heartbeat home={} logs={} stones={} hungry={} night={} attack={}",
                    adv.getStringUUID(), hasHome, logs, stones, hungry, night, underAttack);
        }

        flip = !flip;
        adv.controller().set(AdventurerKeys.PULSE, flip);
    }
}
