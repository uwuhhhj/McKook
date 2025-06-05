package com.meteor.mckook.util.message;

import com.meteor.mckook.McKook;

public abstract class AbstractMessageManager {
    protected final McKook plugin;

    protected AbstractMessageManager(McKook plugin) {
        this.plugin = plugin;
    }

    protected McKook getPlugin() {
        return plugin;
    }
}
