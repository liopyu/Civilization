package net.liopyu.civilization.ai.nav;

import net.liopyu.civilization.entity.Adventurer;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Map;
import java.util.WeakHashMap;

public final class NavRequests {
    private static final Map<Adventurer, ArrayDeque<NavTask>> Q = new WeakHashMap<>();
    private static final Map<Adventurer, NavTask> ACTIVE = new WeakHashMap<>();

    public static void submit(Adventurer a, NavTask task) {
        var dq = Q.computeIfAbsent(a, k -> new ArrayDeque<>());
        dq.add(task);
        dq.stream().sorted(Comparator.comparingInt(t -> -t.priority));
    }

    public static NavTask peek(Adventurer a) {
        prune(a);
        var act = ACTIVE.get(a);
        if (act != null) return act;
        var dq = Q.get(a);
        if (dq == null) return null;
        NavTask best = null;
        for (NavTask t : dq) if (best == null || t.priority > best.priority) best = t;
        if (best != null) {
            ACTIVE.put(a, best);
            dq.remove(best);
        }
        return best;
    }

    public static void complete(Adventurer a, java.util.UUID id) {
        var act = ACTIVE.get(a);
        if (act != null && act.id.equals(id)) ACTIVE.remove(a);
        prune(a);
    }

    public static boolean isDone(Adventurer a, java.util.UUID id) {
        var act = ACTIVE.get(a);
        if (act != null && act.id.equals(id)) return false;
        var dq = Q.get(a);
        if (dq == null) return true;
        for (NavTask t : dq) if (t.id.equals(id)) return false;
        return true;
    }

    private static void prune(Adventurer a) {
        int now = a.tickCount;
        var dq = Q.get(a);
        if (dq != null) dq.removeIf(t -> now > t.untilTick);
        var act = ACTIVE.get(a);
        if (act != null && now > act.untilTick) ACTIVE.remove(a);
    }

    private NavRequests() {
    }
}
