package net.liopyu.civilization.ai.core;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import org.slf4j.Logger;
import net.liopyu.civilization.entity.Adventurer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.*;
import java.util.function.BiFunction;

public final class AdventurerController {
    private static final Logger LOG = LogUtils.getLogger();

    private final Adventurer owner;
    private final Map<ValueKey<?>, Object> values = new HashMap<>();
    private final Map<ValueKey<?>, List<Rule<?>>> rules = new HashMap<>();
    private final Set<ValueKey<?>> changed = new HashSet<>();
    private final Map<ActionMode, List<ModeListener>> listeners = new EnumMap<>(ActionMode.class);
    private boolean attached;
    private int lastChangeTick;
    private int baseDwellTicks = 20;
    private int currentDwellTicks = 20;
    private Override pendingOverride;
    private PickupRequest pickup;
    private final ResourcePlanner planner = new ResourcePlanner();
    private ExcavationRequest excavation;
    private PillarRequest pillar;

    public AdventurerController(Adventurer owner) {
        this.owner = owner;
    }

    public Adventurer owner() {
        return owner;
    }

    public <T> void set(ValueKey<T> key, T value) {
        if (value != null && !key.type().isInstance(value)) return;
        Object prev = values.put(key, value);
        if (!Objects.equals(prev, value)) {
            changed.add(key);
            if (key != AdventurerKeys.PULSE && (owner.tickCount % 20) == 0)
                LOG.info("[Ctrl] {} set {} -> {}", owner.getStringUUID(), key.id(), value);
        }
    }

    public <T> Optional<T> get(ValueKey<T> key) {
        Object v = values.get(key);
        if (v == null) return Optional.empty();
        if (!key.type().isInstance(v)) return Optional.empty();
        @SuppressWarnings("unchecked")
        T out = (T) v;
        return Optional.of(out);
    }

    public <T> void when(ValueKey<T> key, int priority, BiFunction<Adventurer, T, Optional<ActionMode>> decide) {
        List<Rule<?>> list = rules.computeIfAbsent(key, k -> new ArrayList<>());
        list.add(new Rule<>(priority, decide));
        list.sort(Comparator.comparingInt(r -> -((Rule<?>) r).priority));
        LOG.info("[Ctrl] rule {} prio {}", key.id(), priority);
    }

    public void listen(ActionMode mode, ModeListener l) {
        listeners.computeIfAbsent(mode, k -> new ArrayList<>()).add(l);
        LOG.info("[Ctrl] listen {}", mode);
    }

    public void requestMode(ActionMode mode, int priority, int ttlTicks, int minDwellTicks) {
        int now = owner.tickCount;
        if (owner.tickCount % 20 == 0)
            LOG.info("[Ctrl] {} requestMode {} prio={} ttl={} dwellMin={}", owner.getStringUUID(), mode, priority, ttlTicks, minDwellTicks);
        if (pendingOverride == null || priority >= pendingOverride.priority) {
            pendingOverride = new Override(mode, priority, now + Math.max(1, ttlTicks));
        }
        if (minDwellTicks > 0) currentDwellTicks = Math.max(currentDwellTicks, minDwellTicks);
    }

    public void setBaseDwellTicks(int ticks) {
        baseDwellTicks = Math.max(0, ticks);
        currentDwellTicks = Math.max(currentDwellTicks, baseDwellTicks);
        LOG.info("[Ctrl] {} baseDwell={}", owner.getStringUUID(), baseDwellTicks);
    }

    public void tick() {
        boolean tick20 = (owner.tickCount % 20) == 0;
        if (!attached) {
            ModAdventurerModules.attachAll(this);
            attached = true;
            LOG.info("[Ctrl] {} modules attached", owner.getStringUUID());
        }
        int now = owner.tickCount;
        if (pendingOverride != null && now > pendingOverride.untilTick) {
            LOG.info("[Ctrl] {} override expired {}", owner.getStringUUID(), pendingOverride.mode);
            pendingOverride = null;
        }

        boolean onlyHeartbeat = changed.size() == 1 && changed.contains(AdventurerKeys.PULSE);
        if (changed.isEmpty() && pendingOverride == null) {
            if ((now % 60) == 0)
                LOG.info("[Ctrl] {} idle heartbeat mode={} dwell={}", owner.getStringUUID(), owner.getActionMode(), currentDwellTicks);
            return;
        }

        Candidate best;
        if (pendingOverride != null) {
            best = new Candidate(pendingOverride.mode, pendingOverride.priority);
            if (tick20)
                LOG.info("[Ctrl] {} using override {} prio={}", owner.getStringUUID(), best.mode, best.priority);
        } else if (!onlyHeartbeat) {
            best = findBestFromChanged();
            if (tick20)
                LOG.info("[Ctrl] {} evaluate changed keys={} -> {}", owner.getStringUUID(), changed.size(), best == null ? "none" : best.mode);
        } else {
            best = findBestFromAll();
            if (tick20)
                LOG.info("[Ctrl] {} heartbeat reevaluate all -> {}", owner.getStringUUID(), best == null ? "none" : best.mode);
        }

        changed.clear();
        if (best == null) return;

        ActionMode current = owner.getActionMode();
        if (best.mode != current) {
            if (now - lastChangeTick < currentDwellTicks) {
                if ((now % 20) == 0)
                    LOG.info("[Ctrl] {} dwell hold {}ms mode={} -> {}", owner.getStringUUID(), (currentDwellTicks - (now - lastChangeTick)) * 50, current, best.mode);
                return;
            }
            broadcastExit(current);
            owner.setActionMode(best.mode);
            LOG.info("[Ctrl] {} mode {} -> {} prio={}", owner.getStringUUID(), current, best.mode, best.priority);
            lastChangeTick = now;
            broadcastEnter(best.mode);
            currentDwellTicks = baseDwellTicks;
        } else {
            if ((now % 80) == 0)
                LOG.info("[Ctrl] {} remain mode={} prio={}", owner.getStringUUID(), current, best.priority);
        }
    }

    private Candidate findBestFromChanged() {
        ActionMode bestMode = null;
        int bestPrio = Integer.MIN_VALUE;
        for (ValueKey<?> k : changed) {
            List<Rule<?>> rs = rules.get(k);
            if (rs == null || rs.isEmpty()) continue;
            Object val = values.get(k);
            for (Rule<?> r : rs) {
                @SuppressWarnings("unchecked")
                Optional<ActionMode> m = ((Rule<Object>) r).decide.apply(owner, val);
                if (m.isPresent() && r.priority > bestPrio) {
                    bestPrio = r.priority;
                    bestMode = m.get();
                    break;
                }
            }
        }
        if (bestMode == null) return null;
        return new Candidate(bestMode, bestPrio);
    }

    private Candidate findBestFromAll() {
        ActionMode bestMode = null;
        int bestPrio = Integer.MIN_VALUE;
        for (Map.Entry<ValueKey<?>, List<Rule<?>>> e : rules.entrySet()) {
            Object val = values.get(e.getKey());
            List<Rule<?>> rs = e.getValue();
            if (rs == null || rs.isEmpty()) continue;
            for (Rule<?> r : rs) {
                @SuppressWarnings("unchecked")
                Optional<ActionMode> m = ((Rule<Object>) r).decide.apply(owner, val);
                if (m.isPresent() && r.priority > bestPrio) {
                    bestPrio = r.priority;
                    bestMode = m.get();
                    break;
                }
            }
        }
        if (bestMode == null) return null;
        return new Candidate(bestMode, bestPrio);
    }

    private void broadcastEnter(ActionMode mode) {
        List<ModeListener> ls = listeners.get(mode);
        if (ls == null) return;
        for (ModeListener l : ls) l.onEnter(mode);
        LOG.info("[Ctrl] {} enter {}", owner.getStringUUID(), mode);
    }

    private void broadcastExit(ActionMode mode) {
        List<ModeListener> ls = listeners.get(mode);
        if (ls == null) return;
        for (ModeListener l : ls) l.onExit(mode);
        LOG.info("[Ctrl] {} exit {}", owner.getStringUUID(), mode);
    }

    private static final class Rule<T> {
        final int priority;
        final BiFunction<Adventurer, T, Optional<ActionMode>> decide;

        Rule(int priority, BiFunction<Adventurer, T, Optional<ActionMode>> decide) {
            this.priority = priority;
            this.decide = decide;
        }
    }

    private static final class Override {
        final ActionMode mode;
        final int priority;
        final int untilTick;

        Override(ActionMode mode, int priority, int untilTick) {
            this.mode = mode;
            this.priority = priority;
            this.untilTick = untilTick;
        }
    }

    private static final class Candidate {
        final ActionMode mode;
        final int priority;

        Candidate(ActionMode mode, int priority) {
            this.mode = mode;
            this.priority = priority;
        }
    }

    public void requestPickup(PickupRequest req) {
        if (req == null) return;
        if (pickup == null || req.priority >= pickup.priority) {
            pickup = req;
            LOG.info("[Ctrl] {} pickup request prio={} ttl={}", owner.getStringUUID(), req.priority, req.untilTick - owner.tickCount);
        }
    }

    public PickupRequest getPickupRequest() {
        int now = owner.tickCount;
        if (pickup != null && now > pickup.untilTick) {
            LOG.info("[Ctrl] {} pickup expired", owner.getStringUUID());
            pickup = null;
        }
        return pickup;
    }

    public void clearPickup() {
        pickup = null;
        LOG.info("[Ctrl] {} pickup cleared", owner.getStringUUID());
    }

    public ResourcePlanner planner() {
        return planner;
    }

    public void requestExcavation(ExcavationRequest req) {
        if (req == null) return;
        int now = owner.tickCount;
        if (excavation == null || req.priority >= excavation.priority || now > excavation.untilTick) {
            excavation = req;
            LOG.info("[Ctrl] {} excavation {}->{} prio={} ttl={}", owner.getStringUUID(), req.min, req.max, req.priority, req.untilTick - now);
        }
    }

    public ExcavationRequest getExcavation() {
        int now = owner.tickCount;
        if (excavation != null && now > excavation.untilTick) {
            LOG.info("[Ctrl] {} excavation expired", owner.getStringUUID());
            excavation = null;
        }
        return excavation;
    }

    public void clearExcavation(java.util.UUID id) {
        if (excavation != null && excavation.id.equals(id)) {
            LOG.info("[Ctrl] {} excavation cleared {}", owner.getStringUUID(), id);
            excavation = null;
        }
    }

    public CompoundTag save(CompoundTag out) {
        if (pendingOverride != null) {
            CompoundTag ov = new CompoundTag();
            ov.putInt("mode", pendingOverride.mode.ordinal());
            ov.putInt("prio", pendingOverride.priority);
            ov.putInt("until", pendingOverride.untilTick);
            out.put("override", ov);
        }

        if (excavation != null) {
            CompoundTag ex = new CompoundTag();
            ex.putUUID("id", excavation.id);
            ex.putLong("min", excavation.min.asLong());
            ex.putLong("max", excavation.max.asLong());
            ex.putInt("prio", excavation.priority);
            ex.putInt("until", excavation.untilTick);
            out.put("excavation", ex);
        }

        // --- Pillar request  ---
        if (pillar != null) {
            CompoundTag t = new CompoundTag();
            t.putLong("base", pillar.base.asLong());
            t.putInt("ty", pillar.targetY);
            t.putInt("prio", pillar.priority);
            t.putInt("until", pillar.untilTick);

            t.putDouble("reachSq", pillar.reachSq);

            ListTag list = new ListTag();
            if (pillar.targets != null && !pillar.targets.isEmpty()) {
                for (BlockPos p : pillar.targets) list.add(LongTag.valueOf(p.asLong()));
            }
            t.put("targets", list);

            if (list.size() == 1) t.putLong("tgt", ((LongTag) list.get(0)).getAsLong());

            out.put("pillar", t);
        }
        // ----------------------------------------------------------------------

        CompoundTag pl = new CompoundTag();
        pl.put("needs", planner.saveNeeds());
        out.put("planner", pl);

        out.putInt("baseDwell", baseDwellTicks);
        out.putInt("currentDwell", currentDwellTicks);
        out.putInt("lastChange", lastChangeTick);

        LOG.info("[Ctrl] {} save dwell={} current={} lastChange={}",
                owner.getStringUUID(), baseDwellTicks, currentDwellTicks, lastChangeTick);
        return out;
    }


    public void load(CompoundTag in) {
        pendingOverride = null;
        if (in.contains("override")) {
            CompoundTag ov = in.getCompound("override");
            int mi = ov.getInt("mode");
            ActionMode[] ms = ActionMode.values();
            ActionMode m = mi >= 0 && mi < ms.length ? ms[mi] : ActionMode.IDLE;
            pendingOverride = new Override(m, ov.getInt("prio"), ov.getInt("until"));
        }
        excavation = null;
        if (in.contains("excavation")) {
            CompoundTag ex = in.getCompound("excavation");
            excavation = new ExcavationRequest(ex.getUUID("id"),
                    BlockPos.of(ex.getLong("min")),
                    BlockPos.of(ex.getLong("max")),
                    ex.getInt("prio"),
                    ex.getInt("until"));
        }
        pillar = null;
        if (in.contains("pillar", Tag.TAG_COMPOUND)) {
            CompoundTag t = in.getCompound("pillar");

            BlockPos base = BlockPos.of(t.getLong("base"));
            int targetY = t.getInt("ty");
            int prio = t.getInt("prio");
            int until = t.getInt("until");

            java.util.ArrayDeque<BlockPos> targets = new java.util.ArrayDeque<>();
            if (t.contains("targets", Tag.TAG_LIST)) {
                ListTag list = t.getList("targets", Tag.TAG_LONG);
                for (int i = 0; i < list.size(); i++) {
                    long packed = ((LongTag) list.get(i)).getAsLong();
                    targets.add(BlockPos.of(packed));
                }
            } else if (t.contains("tgt", Tag.TAG_LONG)) {
                targets.add(BlockPos.of(t.getLong("tgt")));
            }
            double reachSq = t.contains("reachSq", Tag.TAG_DOUBLE)
                    ? t.getDouble("reachSq")
                    : (t.contains("reach", Tag.TAG_DOUBLE)
                    ? Math.pow(t.getDouble("reach"), 2)
                    : 4.0);
            double reach = Math.sqrt(Math.max(0.0, reachSq));
            pillar = new PillarRequest(base, targetY, targets, reach, prio, until);
        }

        if (in.contains("planner")) {
            CompoundTag pl = in.getCompound("planner");
            if (pl.contains("needs")) planner.loadNeeds(pl.getList("needs", Tag.TAG_COMPOUND));
        }
        baseDwellTicks = in.getInt("baseDwell");
        currentDwellTicks = in.getInt("currentDwell");
        lastChangeTick = in.getInt("lastChange");
        LOG.info("[Ctrl] {} load dwell={} current={} lastChange={} override={} excavation={}",
                owner.getStringUUID(), baseDwellTicks, currentDwellTicks, lastChangeTick,
                pendingOverride != null ? pendingOverride.mode : "none",
                excavation != null ? (excavation.min + "->" + excavation.max) : "none");
    }

    public static final class PillarRequest {
        public final java.util.UUID id = java.util.UUID.randomUUID();
        public final BlockPos base;
        public final int targetY;
        public final java.util.ArrayDeque<BlockPos> targets;
        public final double reachSq;
        public final int priority;
        public final int untilTick;

        public PillarRequest(BlockPos base,
                             int targetY,
                             java.util.Deque<BlockPos> targets,
                             double reach,
                             int priority,
                             int untilTick) {
            this.base = base.immutable();
            this.targetY = targetY;
            this.targets = new java.util.ArrayDeque<>(targets);
            this.reachSq = reach * reach;
            this.priority = priority;
            this.untilTick = untilTick;
        }
    }

    public void requestPillar(PillarRequest req) {
        if (req == null) return;
        int now = owner.tickCount;
        if (pillar == null || req.priority >= pillar.priority || now > pillar.untilTick) pillar = req;
    }

    public PillarRequest getPillar() {
        int now = owner.tickCount;
        if (pillar != null && now > pillar.untilTick) pillar = null;
        return pillar;
    }

    public void clearPillar(java.util.UUID id) {
        if (pillar != null && pillar.id.equals(id)) pillar = null;
    }

}
