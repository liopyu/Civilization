package net.liopyu.civilization.ai.core;

public final class ModAdventurerAI {
    public static void bootstrap() {
        ModAdventurerModules.register(new BasicProgressionModule());
    }
}
