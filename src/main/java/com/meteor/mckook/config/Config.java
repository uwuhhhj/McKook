package com.meteor.mckook.config;

import com.meteor.mckook.McKook;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

public class Config {
    private final McKook plugin;
    private FileConfiguration config;
    private static Config instance;

    private Config(McKook plugin) {
        this.plugin = plugin;
        reload();
    }

    public static void init(McKook plugin) {
        instance = new Config(plugin);
    }

    public static Config get() {
        return instance;
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    public void save() {
        plugin.saveConfig();
    }

    public FileConfiguration raw() {
        return config;
    }

    public String getBotToken() {
        return config.getString("kook.bot-token");
    }

    public String getGuildId() {
        return config.getString("setting.guild");
    }

    public Map<String, String> getChannelIdMap() {
        ConfigurationSection section = config.getConfigurationSection("setting.channel");
        if (section == null) {
            return Collections.emptyMap();
        }
        Map<String, String> map = new HashMap<>();
        for (String key : section.getKeys(false)) {
            map.put(key, section.getString(key));
        }
        return map;
    }

    public ConfigurationSection getChannelSection() {
        return config.getConfigurationSection("setting.channel");
    }

    public String getChannelId(String name) {
        ConfigurationSection section = config.getConfigurationSection("setting.channel");
        return section == null ? null : section.getString(name);
    }

    public Map<String, Integer> getRoles() {
        Map<String, Integer> roles = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("setting.roles");
        if (section != null) {
            for (String roleName : section.getKeys(false)) {
                String idStr = section.getString(roleName);
                if (idStr != null && !idStr.isEmpty()) {
                    idStr = idStr.replace("'", "");
                    try {
                        roles.put(roleName, Integer.parseInt(idStr.trim()));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return roles;
    }

    public boolean isPlayerJoinMessageEnabled() {
        return config.getBoolean("setting.message-bridge.player-join.enabled", true);
    }

    public boolean isPlayerQuitMessageEnabled() {
        return config.getBoolean("setting.message-bridge.player-quit.enabled", true);
    }

    public boolean isServerChatToKookEnabled() {
        return config.getBoolean("setting.message-bridge.server-chat-to-kook.enabled", false);
    }

    public boolean isKookChatToServerEnabled() {
        return config.getBoolean("setting.message-bridge.kook-chat-to-server.enabled", false);
    }

    public List<String> getBlackWorlds() {
        return config.getStringList("setting.black-worlds");
    }

    public boolean isWhitelistEnabled() {
        return config.getBoolean("setting.whitelist.enable", false);
    }

    public boolean isWhitelistJoinKickEnabled() {
        return config.getBoolean("setting.whitelist.check-range.join", false);
    }

    public boolean isWhitelistActionCheckEnabled() {
        return config.getBoolean("setting.whitelist.check-range.action", false);
    }

    public int getWhitelistKickDelaySeconds() {
        return config.getInt("setting.whitelist.kick-delay-seconds", 10);
    }

    public boolean isTitleReminderEnabled() {
        return config.getBoolean("setting.whitelist.title-reminder.enabled", true);
    }

    public int getTitleReminderIntervalSeconds() {
        return config.getInt("setting.whitelist.title-reminder.interval-seconds", 30);
    }

    public void set(String path, Object value) {
        config.set(path, value);
    }
}

