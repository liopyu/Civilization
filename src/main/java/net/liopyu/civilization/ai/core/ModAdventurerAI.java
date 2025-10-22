package net.liopyu.civilization.ai.core;

import com.mojang.logging.LogUtils;

public final class ModAdventurerAI {
    public static void bootstrap() {
        LogUtils.getLogger().info("[AI] bootstrap");
        ModAdventurerModules.register(new BasicProgressionModule());
    }

}
