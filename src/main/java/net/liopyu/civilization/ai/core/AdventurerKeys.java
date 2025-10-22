package net.liopyu.civilization.ai.core;

public final class AdventurerKeys {
    public static final ValueKey<Boolean> HAS_HOME = ValueKey.of("civilization", "has_home", Boolean.class);
    public static final ValueKey<Integer> WOOD_COUNT = ValueKey.of("civilization", "wood_count", Integer.class);
    public static final ValueKey<Integer> STONE_COUNT = ValueKey.of("civilization", "stone_count", Integer.class);
    public static final ValueKey<Integer> DEFICIT_WOOD = ValueKey.of("civilization", "deficit_wood", Integer.class);
    public static final ValueKey<Integer> DEFICIT_STONE = ValueKey.of("civilization", "deficit_stone", Integer.class);
    public static final ValueKey<Boolean> HUNGRY = ValueKey.of("civilization", "hungry", Boolean.class);
    public static final ValueKey<Boolean> NIGHT = ValueKey.of("civilization", "night", Boolean.class);
    public static final ValueKey<Boolean> UNDER_ATTACK = ValueKey.of("civilization", "under_attack", Boolean.class);
    public static final ValueKey<Boolean> PULSE = ValueKey.of("civilization", "pulse", Boolean.class);
}