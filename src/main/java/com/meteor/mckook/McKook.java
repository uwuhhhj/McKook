package com.meteor.mckook;

import com.meteor.mckook.command.CommandManager;
import com.meteor.mckook.kook.KookBot;
import com.meteor.mckook.kook.command.KookCommandManager;
import com.meteor.mckook.message.AbstractKookMessage;
import com.meteor.mckook.message.sub.PlayerChatMessage;
import com.meteor.mckook.message.sub.PlayerJoinMessage;
import com.meteor.mckook.message.sub.PlayerLinkMessage;
import com.meteor.mckook.message.sub.WhitelistMessage;
import com.meteor.mckook.storage.DataManager;
import com.meteor.mckook.storage.mapper.LinkRepository;
import com.meteor.mckook.util.BaseConfig;
import lombok.Getter;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection; // 新增导入
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections; // 新增导入
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

public final class McKook extends JavaPlugin {

    @Getter
    private KookBot kookBot;
    private List<AbstractKookMessage> abstractKookMessages = new ArrayList<>();
    @Getter
    private LinkRepository linkRepository;
    private CommandManager commandManager;
    private KookCommandManager kookCommandManager;
    private Metrics metrics;

    private Map<String, YamlConfiguration> messageConfigurations = new HashMap<>();
    private static final String PLAYER_JOIN_MESSAGE_FILE = "PlayerJoinKookMessage";
    private static final String PLAYER_CHAT_MESSAGE_FILE = "PlayerChatMessage";
    private static final String PLAYER_LINK_MESSAGE_FILE = "PlayerLinkKookMessage";
    private static final String PLAYER_WHITELIST_MESSAGE_FILE = "WhitelistMessage";
    private static final List<String> MESSAGE_FILE_NAMES = Arrays.asList(
            PLAYER_JOIN_MESSAGE_FILE,
            PLAYER_CHAT_MESSAGE_FILE,
            PLAYER_LINK_MESSAGE_FILE,
            PLAYER_WHITELIST_MESSAGE_FILE
    );

    // 新增字段：用于存储从 config.yml 加载的角色配置
    private Map<String, Integer> configuredRoles = new HashMap<>();


    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadPluginConfig(); // 这会加载 config.yml, BaseConfig, message/*.yml 和 setting.roles

        DataManager.init(this);
        if (DataManager.getInstance() != null) {
            this.linkRepository = DataManager.getInstance().getLinkRepository();
        }

        if (this.linkRepository == null) {
            getLogger().log(Level.SEVERE, "LinkRepository 未能成功初始化。绑定相关功能将不可用。");
        }
        commandManager = new CommandManager(this);
        PluginCommand mckookCommand = getCommand("mckook");
        Objects.requireNonNull(mckookCommand, "命令 'mckook' 未在 plugin.yml 中定义!");
        mckookCommand.setExecutor(commandManager);
        mckookCommand.setTabCompleter(commandManager);

        metrics = new Metrics(this, 20690);

        getLogger().info("McKook 插件正在启动, 尝试连接 Kook 服务器...");

        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                KookBot newBotInstance = new KookBot(this);
                getServer().getScheduler().runTask(this, () -> {
                    this.kookBot = newBotInstance;
                    getLogger().info("KookBot 异步初始化成功。");

                    getLogger().info("KookBot 已就绪, 初始化消息处理器和命令...");
                    setupMessageHandlers();
                    commandManager.init();

                    this.kookCommandManager = new KookCommandManager(this, this.kookBot);
                    this.kookCommandManager.registerCommands();

                    getLogger().info("感谢你的使用！McKook 插件已成功加载并连接到 Kook。");
                    getLogger().info("插件问题或建议请加群反馈 653440235");
                });
            } catch (Exception e) {
                this.kookBot = null;
                getLogger().log(Level.SEVERE, "KookBot 异步初始化失败。与 Kook 相关的功能将不可用。", e);
                getLogger().info("McKook 插件已加载, 但未能连接到 Kook。请检查配置和网络。");
            }
        });
    }

    public void reload() {
        getLogger().info("开始插件重载流程...");
        reloadPluginConfig();    // 1. 重载所有配置 (config.yml, message/*.yml, setting.roles)
        reloadKookBot();
        getLogger().info("插件重载流程已启动 (KookBot 和相关系统将异步完成)。");
    }

    public void reloadKookBot() {
        getLogger().info("正在重新加载 Kook 机器人...");
        if (this.kookBot != null) {
            getLogger().info("正在关闭旧的 KookBot 实例...");
            try {
                if (abstractKookMessages != null) {
                    abstractKookMessages.forEach(AbstractKookMessage::unRegister);
                }
                if (!this.kookBot.isInvalid()) {
                    this.kookBot.unRegisterKookListener();
                }
                this.kookBot.close();
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "关闭旧 KookBot 实例时出错: ", e);
            }
            this.kookBot = null;
            getLogger().info("旧 KookBot 实例已关闭。");
        }
        this.kookCommandManager = null;

        getLogger().info("正在异步创建新的 KookBot 实例...");
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                KookBot newBotInstance = new KookBot(this);
                getServer().getScheduler().runTask(this, () -> {
                    this.kookBot = newBotInstance;
                    getLogger().info("Kook 机器人已异步重新加载。");

                    getLogger().info("KookBot 已更新, 重新加载消息系统和相关命令组件...");
                    reloadMessageSystem();
                    if (commandManager != null) {
                        commandManager.init();
                    }

                    this.kookCommandManager = new KookCommandManager(this, this.kookBot);
                    this.kookCommandManager.registerCommands();

                    getLogger().info("依赖 KookBot 的组件已使用新实例重新加载。");
                });
            } catch (Exception e) {
                this.kookBot = null;
                getLogger().log(Level.SEVERE, "KookBot 异步重新加载失败。与 Kook 相关的功能可能无法使用。", e);
            }
        });
    }

    private void loadMessageConfigurations() {
        getLogger().info("正在加载消息配置文件...");
        messageConfigurations.clear();
        for (String messageName : MESSAGE_FILE_NAMES) {
            String filePath = "message/" + messageName + ".yml";
            File file = new File(getDataFolder(), filePath);
            if (!file.exists()) {
                saveResource(filePath, false);
                getLogger().info("已保存默认消息配置文件: " + filePath);
            }
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            messageConfigurations.put(messageName, config);
            getLogger().info("已加载消息配置文件: " + filePath);
        }
        getLogger().info("所有消息配置文件加载完毕。");
    }

    // 新增方法：加载角色配置
    private void loadRoleConfigurations() {
        getLogger().info("正在加载角色配置 (setting.roles)...");
        this.configuredRoles.clear(); // 清除旧的角色配置
        ConfigurationSection rolesSection = getConfig().getConfigurationSection("setting.roles");
        if (rolesSection != null) {
            for (String roleName : rolesSection.getKeys(false)) {
                String idStr = rolesSection.getString(roleName);
                if (idStr != null && !idStr.isEmpty()) {
                    try {
                        // 移除可能存在的单引号
                        idStr = idStr.replace("'", "");
                        this.configuredRoles.put(roleName, Integer.parseInt(idStr.trim()));
                        getLogger().info("已加载角色: " + roleName + " -> ID: " + idStr);
                    } catch (NumberFormatException e) {
                        getLogger().warning("角色 '" + roleName + "' 的ID '" + idStr + "' 格式无效，已跳过。请确保为纯数字。");
                    }
                } else {
                    getLogger().warning("角色 '" + roleName + "' 的ID为空，已跳过。");
                }
            }
        } else {
            getLogger().info("'setting.roles' 配置节未找到，无自定义角色信息加载。");
        }
        getLogger().info("角色配置加载完毕。");
    }


    public void reloadPluginConfig() {
        super.reloadConfig();
        BaseConfig.init(this);
        getLogger().info("主配置文件 (config.yml) 已重新加载。");

        loadMessageConfigurations();
        getLogger().info("所有消息配置文件已重新加载。");

        loadRoleConfigurations(); // 新增：调用加载角色配置的方法
        getLogger().info("角色配置 (setting.roles) 已重新加载。");
    }

    // 新增方法：获取已配置的角色映射
    public Map<String, Integer> getConfiguredRoles() {
        return Collections.unmodifiableMap(this.configuredRoles); // 返回不可修改的副本，保证安全
    }

    public void reloadMessageSystem() {
        getLogger().info("正在重新加载消息系统...");
        if (abstractKookMessages != null) {
            abstractKookMessages.forEach(AbstractKookMessage::unRegister);
            abstractKookMessages.clear();
        } else {
            abstractKookMessages = new ArrayList<>();
        }
        setupMessageHandlers();
        getLogger().info("消息系统已重新加载。");
    }

    private void setupMessageHandlers() {
        if (this.abstractKookMessages == null) {
            this.abstractKookMessages = new ArrayList<>();
        }
        this.abstractKookMessages.clear();

        if (this.kookBot == null || this.kookBot.isInvalid()) {
            getLogger().warning("KookBot 未就绪，无法设置消息处理器。");
            return;
        }

        try {
            YamlConfiguration joinConfig = messageConfigurations.get(PLAYER_JOIN_MESSAGE_FILE);
            if (joinConfig != null) {
                PlayerJoinMessage playerJoinMessage = new PlayerJoinMessage(this, joinConfig);
                abstractKookMessages.add(playerJoinMessage);
                playerJoinMessage.register();
            } else {
                getLogger().warning("未能找到 " + PLAYER_JOIN_MESSAGE_FILE + " 的已加载配置，该消息功能可能无法正常工作。");
            }

            YamlConfiguration chatConfig = messageConfigurations.get(PLAYER_CHAT_MESSAGE_FILE);
            if (chatConfig != null) {
                PlayerChatMessage playerChatMessage = new PlayerChatMessage(this, chatConfig);
                abstractKookMessages.add(playerChatMessage);
                playerChatMessage.register();
            } else {
                getLogger().warning("未能找到 " + PLAYER_CHAT_MESSAGE_FILE + " 的已加载配置，该消息功能可能无法正常工作。");
            }

            YamlConfiguration linkConfig = messageConfigurations.get(PLAYER_LINK_MESSAGE_FILE);
            if (linkConfig != null) {
                PlayerLinkMessage playerLinkMessage = new PlayerLinkMessage(this, linkConfig);
                abstractKookMessages.add(playerLinkMessage);
                playerLinkMessage.register();
            } else {
                getLogger().warning("未能找到 " + PLAYER_LINK_MESSAGE_FILE + " 的已加载配置，该消息功能可能无法正常工作。");
            }
            YamlConfiguration whitelistConfig = messageConfigurations.get(PLAYER_WHITELIST_MESSAGE_FILE);
            if (whitelistConfig != null) {
                WhitelistMessage playerLinkMessage = new WhitelistMessage(this, whitelistConfig);
                abstractKookMessages.add(playerLinkMessage);
                playerLinkMessage.register();
            } else {
                getLogger().warning("未能找到 " + PLAYER_WHITELIST_MESSAGE_FILE + " 的已加载配置，该消息功能可能无法正常工作。");
            }

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "创建和注册消息处理器时发生错误:", e);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info(getName() + " 正在卸载...");

        if (this.kookBot != null) {
            try {
                this.kookBot.close();
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "关闭 KookBot 时出错: ", e);
            }
            this.kookBot = null;
        }

        if (abstractKookMessages != null) {
            try {
                abstractKookMessages.forEach(AbstractKookMessage::unRegister);
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "注销消息处理器时出错: ", e);
            }
            abstractKookMessages.clear();
            abstractKookMessages = null;
        }
        messageConfigurations.clear();
        configuredRoles.clear(); // 清理角色配置

        if (DataManager.instance != null) {
            try {
                DataManager.instance.close();
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "关闭 DataManager 时出错: ", e);
            }
        }

        commandManager = null;
        kookCommandManager = null;
        metrics = null;
        getServer().getScheduler().cancelTasks(this);
        getLogger().info(getName() + " 插件已卸载。");
    }
}