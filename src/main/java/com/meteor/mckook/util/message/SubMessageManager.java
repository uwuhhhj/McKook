package com.meteor.mckook.util.message;

import org.bukkit.configuration.file.YamlConfiguration;

public class SubMessageManager {
    private final YamlConfiguration config;

    public SubMessageManager(YamlConfiguration config) {
        this.config = config;
    }

    public YamlConfiguration getConfig() {
        return config;
    }
}
