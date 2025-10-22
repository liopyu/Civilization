package net.liopyu.civilization.ai.core;

import com.mojang.logging.LogUtils;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ModAdventurerModules {
    private static final List<AdventurerModule> MODULES = new CopyOnWriteArrayList<>();

    public static void register(AdventurerModule module) {
        MODULES.add(module);
        LogUtils.getLogger().info("[Modules] registered {}", module.getClass().getSimpleName());
    }


    static void attachAll(AdventurerController controller) {
        for (AdventurerModule m : MODULES) m.attach(controller);
    }
}
