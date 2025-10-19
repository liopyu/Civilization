package net.liopyu.civilization.ai.core;

import com.mojang.logging.LogUtils;
import net.liopyu.civilization.ai.ActionMode;
import net.liopyu.civilization.entity.Adventurer;

import java.util.*;
import java.util.function.BiFunction;

public final class AdventurerController {
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


    public AdventurerController(Adventurer owner) {
        this.owner = owner;
    }

    public Adventurer owner() {
        return owner;
    }

    public <T> void set(ValueKey<T> key, T value) {
        if (value != null && !key.type().isInstance(value)) return;
        Object prev = values.put(key, value);
        if (!Objects.equals(prev, value)) changed.add(key);
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
    }

    public void listen(ActionMode mode, ModeListener l) {
        listeners.computeIfAbsent(mode, k -> new ArrayList<>()).add(l);
    }

    public void requestMode(ActionMode mode, int priority, int ttlTicks, int minDwellTicks) {
        int now = owner.tickCount;
        if (pendingOverride == null || priority >= pendingOverride.priority) {
            pendingOverride = new Override(mode, priority, now + Math.max(1, ttlTicks));
        }
        if (minDwellTicks > 0) currentDwellTicks = Math.max(currentDwellTicks, minDwellTicks);
    }

    public void setBaseDwellTicks(int ticks) {
        baseDwellTicks = Math.max(0, ticks);
        currentDwellTicks = Math.max(currentDwellTicks, baseDwellTicks);
    }

    public void tick() {
        if (!attached) {
            ModAdventurerModules.attachAll(this);
            attached = true;
        }
        int now = owner.tickCount;
        if (pendingOverride != null && now > pendingOverride.untilTick) pendingOverride = null;

        boolean onlyHeartbeat = changed.size() == 1 && changed.contains(AdventurerKeys.PULSE);

        if (changed.isEmpty() && pendingOverride == null) return;

        Candidate best = null;

        if (pendingOverride != null) {
            best = new Candidate(pendingOverride.mode, pendingOverride.priority);
        } else if (!onlyHeartbeat) {
            best = findBestFromChanged();
        } else {
            best = findBestFromAll();
        }

        changed.clear();

        if (best == null) return;

        ActionMode current = owner.getActionMode();
        if (best.mode != current) {
            if (now - lastChangeTick < currentDwellTicks) return;
            broadcastExit(current);
            owner.setActionMode(best.mode);
            LogUtils.getLogger().info("Setting mode to: " + best.mode);
            lastChangeTick = now;
            broadcastEnter(best.mode);
            currentDwellTicks = baseDwellTicks;
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
    }

    private void broadcastExit(ActionMode mode) {
        List<ModeListener> ls = listeners.get(mode);
        if (ls == null) return;
        for (ModeListener l : ls) l.onExit(mode);
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
        if (pickup == null || req.priority >= pickup.priority) pickup = req;
    }

    public PickupRequest getPickupRequest() {
        int now = owner.tickCount;
        if (pickup != null && now > pickup.untilTick) pickup = null;
        return pickup;
    }

    public void clearPickup() {
        pickup = null;
    }
}
