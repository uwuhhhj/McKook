package com.meteor.mckook;

import com.meteor.mckook.command.CommandManager;
import com.meteor.mckook.kook.KookBot;
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
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap; // 新增导入
import java.util.List;
import java.util.Map; // 新增导入
import java.util.Objects;
import java.util.logging.Level;

public final class McKook extends JavaPlugin {

    @Getter
    private KookBot kookBot;
    private List<AbstractKookMessage> abstractKookMessages = new ArrayList<>();
    @Getter
    private LinkRepository linkRepository;
    private CommandManager commandManager;
    private Metrics metrics;

    // 新增字段：用于存储已加载的消息配置文件
    private Map<String, YamlConfiguration> messageConfigurations = new HashMap<>();
    // 建议将消息文件名定义为常量
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


    @Override
    public void onEnable() {
        // 1. 同步进行不依赖 KookBot 的初始化
        saveDefaultConfig(); // 确保 config.yml 存在
        reloadPluginConfig(); // 加载 config.yml, BaseConfig, 以及所有 message/*.yml 文件到内存

        DataManager.init(this);
        if (DataManager.getInstance() != null) {
            this.linkRepository = DataManager.getInstance().getLinkRepository();
        }

        if (this.linkRepository == null) {
            getLogger().log(Level.SEVERE, "LinkRepository 未能成功初始化。绑定相关功能将不可用。");
            // getServer().getPluginManager().disablePlugin(this);
            // return;
        }
        commandManager = new CommandManager(this);
        PluginCommand mckookCommand = getCommand("mckook");
        Objects.requireNonNull(mckookCommand, "命令 'mckook' 未在 plugin.yml 中定义!");
        mckookCommand.setExecutor(commandManager);
        mckookCommand.setTabCompleter(commandManager);

        metrics = new Metrics(this, 20690);

        getLogger().info("McKook 插件正在启动, 尝试连接 Kook 服务器...");

        // 2. 异步初始化 KookBot
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                KookBot newBotInstance = new KookBot(this);
                getServer().getScheduler().runTask(this, () -> {
                    this.kookBot = newBotInstance;
                    getLogger().info("KookBot 异步初始化成功。");

                    // 3. KookBot 已就绪，现在初始化依赖 KookBot 的组件
                    getLogger().info("KookBot 已就绪, 初始化消息处理器和命令...");
                    // setupMessageHandlers 会使用 reloadPluginConfig 中加载的 messageConfigurations
                    setupMessageHandlers();
                    commandManager.init();

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
        reloadPluginConfig();    // 1. 重载所有配置 (config.yml 和 message/*.yml)
        // DataManager.init(this); // 如果 DataManager 的配置也需要重载

        reloadKookBot(); // 2. 重载 Kook 机器人 (异步), 其回调会调用 reloadMessageSystem
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

        getLogger().info("正在异步创建新的 KookBot 实例...");
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                KookBot newBotInstance = new KookBot(this);
                getServer().getScheduler().runTask(this, () -> {
                    this.kookBot = newBotInstance;
                    getLogger().info("Kook 机器人已异步重新加载。");

                    getLogger().info("KookBot 已更新, 重新加载消息系统和相关命令组件...");
                    // reloadMessageSystem 会调用 setupMessageHandlers,
                    // setupMessageHandlers 会使用已由 reloadPluginConfig 加载的 messageConfigurations
                    reloadMessageSystem();
                    if (commandManager != null) {
                        commandManager.init();
                    }
                    getLogger().info("依赖 KookBot 的组件已使用新实例重新加载。");
                });
            } catch (Exception e) {
                this.kookBot = null;
                getLogger().log(Level.SEVERE, "KookBot 异步重新加载失败。与 Kook 相关的功能可能无法使用。", e);
            }
        });
    }

    // 新增方法：加载所有消息配置文件到内存
    private void loadMessageConfigurations() {
        getLogger().info("正在加载消息配置文件...");
        messageConfigurations.clear(); // 清除旧的配置对象
        for (String messageName : MESSAGE_FILE_NAMES) {
            String filePath = "message/" + messageName + ".yml";
            File file = new File(getDataFolder(), filePath);
            if (!file.exists()) {
                saveResource(filePath, false); // 保存默认文件
                getLogger().info("已保存默认消息配置文件: " + filePath);
            }
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            messageConfigurations.put(messageName, config); // 按文件名存储配置对象
            getLogger().info("已加载消息配置文件: " + filePath);
        }
        getLogger().info("所有消息配置文件加载完毕。");
    }

    public void reloadPluginConfig() {
        super.reloadConfig(); // 重载 config.yml
        BaseConfig.init(this); // 初始化 BaseConfig
        getLogger().info("主配置文件 (config.yml) 已重新加载。");

        loadMessageConfigurations(); // 加载/重载所有 message/*.yml 文件
        getLogger().info("所有消息配置文件已重新加载。");
    }

    public void reloadMessageSystem() {
        getLogger().info("正在重新加载消息系统...");
        if (abstractKookMessages != null) { // 确保列表存在
            abstractKookMessages.forEach(AbstractKookMessage::unRegister);
            abstractKookMessages.clear();
        } else {
            abstractKookMessages = new ArrayList<>(); // 以防万一
        }

        // setupMessageHandlers 现在会使用 this.messageConfigurations 中的配置
        setupMessageHandlers();
        getLogger().info("消息系统已重新加载。");
    }

    private void setupMessageHandlers() {
        // 确保 abstractKookMessages 列表是干净的
        if (this.abstractKookMessages == null) {
            this.abstractKookMessages = new ArrayList<>();
        }
        this.abstractKookMessages.clear();

        // KookBot 必须存在才能注册处理器
        if (this.kookBot == null || this.kookBot.isInvalid()) {
            getLogger().warning("KookBot 未就绪，无法设置消息处理器。");
            return;
        }

        try {
            // 从已加载的配置中获取并创建 PlayerJoinMessage 处理器
            YamlConfiguration joinConfig = messageConfigurations.get(PLAYER_JOIN_MESSAGE_FILE);
            if (joinConfig != null) {
                PlayerJoinMessage playerJoinMessage = new PlayerJoinMessage(this, joinConfig);
                abstractKookMessages.add(playerJoinMessage);
                playerJoinMessage.register();
            } else {
                getLogger().warning("未能找到 " + PLAYER_JOIN_MESSAGE_FILE + " 的已加载配置，该消息功能可能无法正常工作。");
            }

            // 从已加载的配置中获取并创建 PlayerChatMessage 处理器
            YamlConfiguration chatConfig = messageConfigurations.get(PLAYER_CHAT_MESSAGE_FILE);
            if (chatConfig != null) {
                PlayerChatMessage playerChatMessage = new PlayerChatMessage(this, chatConfig);
                abstractKookMessages.add(playerChatMessage);
                playerChatMessage.register();
            } else {
                getLogger().warning("未能找到 " + PLAYER_CHAT_MESSAGE_FILE + " 的已加载配置，该消息功能可能无法正常工作。");
            }

            // 从已加载的配置中获取并创建 PlayerLinkMessage 处理器
            YamlConfiguration linkConfig = messageConfigurations.get(PLAYER_LINK_MESSAGE_FILE);
            if (linkConfig != null) {
                PlayerLinkMessage playerLinkMessage = new PlayerLinkMessage(this, linkConfig);
                abstractKookMessages.add(playerLinkMessage);
                playerLinkMessage.register();
            } else {
                getLogger().warning("未能找到 " + PLAYER_LINK_MESSAGE_FILE + " 的已加载配置，该消息功能可能无法正常工作。");
            }
            // 从已加载的配置中获取并创建 WhitelistMessage 处理器
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
        messageConfigurations.clear(); // 清理已加载的消息配置

        if (DataManager.instance != null) {
            try {
                DataManager.instance.close();
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "关闭 DataManager 时出错: ", e);
            }
        }

        commandManager = null;
        metrics = null;
        getServer().getScheduler().cancelTasks(this);
        getLogger().info(getName() + " 插件已卸载。");
    }
}