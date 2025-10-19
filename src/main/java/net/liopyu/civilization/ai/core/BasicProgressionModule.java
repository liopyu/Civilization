package net.liopyu.civilization.ai.core;

import net.liopyu.civilization.ai.ActionMode;

import java.util.Optional;

public final class BasicProgressionModule implements AdventurerModule {
    @Override
    public void attach(AdventurerController c) {
        c.when(AdventurerKeys.UNDER_ATTACK, 1000, (a, v) -> v != null && v ? Optional.of(ActionMode.RETURN_HOME) : Optional.empty());
        c.when(AdventurerKeys.HUNGRY, 900, (a, v) -> v != null && v ? Optional.of(ActionMode.EAT) : Optional.empty());
        c.when(AdventurerKeys.NIGHT, 800, (a, v) -> v != null && v && !c.get(AdventurerKeys.HAS_HOME).orElse(false) ? Optional.of(ActionMode.BUILD_SHELTER) : Optional.empty());
        c.when(AdventurerKeys.HAS_HOME, 750, (a, v) -> v != null && v ? Optional.of(ActionMode.EXPLORE) : Optional.empty());
        c.when(AdventurerKeys.WOOD_COUNT, 700, (a, v) -> {
            int n = v == null ? 0 : v;
            if (!c.get(AdventurerKeys.HAS_HOME).orElse(false) && n >= 8) return Optional.of(ActionMode.BUILD_SHELTER);
            return n < 16 ? Optional.of(ActionMode.GATHER_WOOD) : Optional.empty();
        });
        c.when(AdventurerKeys.STONE_COUNT, 650, (a, v) -> {
            int n = v == null ? 0 : v;
            return n < 32 ? Optional.of(ActionMode.MINE_STONE) : Optional.empty();
        });
        c.setBaseDwellTicks(20);
        c.requestMode(ActionMode.EXPLORE, 50, 40, 20);
    }
}
