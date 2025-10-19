package net.liopyu.civilization.ai.core;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ModAdventurerModules {
    private static final List<AdventurerModule> MODULES = new CopyOnWriteArrayList<>();

    public static void register(AdventurerModule module) {
        MODULES.add(module);
    }

    static void attachAll(AdventurerController controller) {
        for (AdventurerModule m : MODULES) m.attach(controller);
    }
}
