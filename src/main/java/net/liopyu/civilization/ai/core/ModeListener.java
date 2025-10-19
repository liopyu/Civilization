package net.liopyu.civilization.ai.core;

import net.liopyu.civilization.ai.ActionMode;

public interface ModeListener {
    void onEnter(ActionMode mode);

    void onExit(ActionMode mode);
}
