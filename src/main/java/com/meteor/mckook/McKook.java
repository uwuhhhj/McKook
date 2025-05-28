package com.meteor.mckook;

import com.meteor.mckook.command.CommandManager;
import com.meteor.mckook.kook.KookBot;
import com.meteor.mckook.message.AbstractKookMessage;
import com.meteor.mckook.message.sub.PlayerChatMessage;
import com.meteor.mckook.message.sub.PlayerJoinMessage;
import com.meteor.mckook.message.sub.PlayerLinkMessage;
import com.meteor.mckook.storage.DataManager;
import com.meteor.mckook.util.BaseConfig;
import lombok.Getter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

public final class McKook extends JavaPlugin {

    @Getter
    private KookBot kookBot;
    // 在声明时初始化，避免 NullPointerException
    private List<AbstractKookMessage> abstractKookMessages = new ArrayList<>();

    private CommandManager commandManager;
    private Metrics metrics;

    @Override
    public void onEnable() {
        // 1. 同步进行不依赖 KookBot 的初始化
        saveDefaultConfig();
        reloadPluginConfig(); // 加载 config.yml 并初始化 BaseConfig
        DataManager.init(this);

        // 实例化 CommandManager，但其依赖 KookBot 的 init() 方法会稍后调用
        commandManager = new CommandManager(this);
        // 尽早设置命令执行器和 Tab 补全器，这样 /mckook 命令本身会被注册
        Objects.requireNonNull(getCommand("mckook"), "命令 'mckook' 未在 plugin.yml 中定义!")
                .setExecutor(commandManager);
        Objects.requireNonNull(getCommand("mckook"), "命令 'mckook' 未在 plugin.yml 中定义!")
                .setTabCompleter(commandManager);

        // 尽早初始化 Metrics
        metrics = new Metrics(this, 20690);

        getLogger().info("McKook 插件正在启动, 尝试连接 Kook 服务器...");

        // 2. 异步初始化 KookBot
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                KookBot newBotInstance = new KookBot(this); // 这是一个阻塞调用，在异步线程中执行
                // 当 KookBot 成功创建后，切换回主线程来赋值并初始化依赖组件
                getServer().getScheduler().runTask(this, () -> {
                    this.kookBot = newBotInstance;
                    getLogger().info("KookBot 异步初始化成功。");

                    // 3. KookBot 已就绪，现在初始化依赖 KookBot 的组件
                    getLogger().info("KookBot 已就绪, 初始化消息处理器和命令...");
                    setupMessageHandlers(); // 注册监听器，需要 kookBot
                    commandManager.init();  // 初始化命令，其中 LinkCmd 需要 kookBot

                    getLogger().info("感谢你的使用！McKook 插件已成功加载并连接到 Kook。");
                    getLogger().info("插件问题或建议请加群反馈 653440235");
                });
            } catch (Exception e) {
                this.kookBot = null; // 如果初始化失败，确保 kookBot 为 null
                getLogger().log(Level.SEVERE, "KookBot 异步初始化失败。与 Kook 相关的功能将不可用。", e);
                getLogger().info("McKook 插件已加载, 但未能连接到 Kook。请检查配置和网络。");
                // 此时，setupMessageHandlers() 和 commandManager.init() 不会被调用，
                // 避免了它们尝试使用 null 的 kookBot。
                // 依赖 Kook 的功能将无法使用，这是预期的。
            }
        });
    }

    public void reload() {
        getLogger().info("开始插件重载流程...");
        reloadPluginConfig();    // 1. 重载主配置 (同步)
        // DataManager 如果配置在 config.yml 中更改，也可能需要重载
        // DataManager.init(this); // 或者 DataManager 特定的重载方法

        // 2. 重载 Kook 机器人 (异步).
        //    依赖 KookBot 的系统 (消息处理, 命令) 将在其成功回调中被重新加载.
        reloadKookBot();
        getLogger().info("插件重载流程已启动 (KookBot 和相关系统将异步完成)。");
    }

    public void reloadKookBot() {
        getLogger().info("正在重新加载 Kook 机器人...");

        // 先关闭并清理旧的 KookBot 实例及其监听器
        if (this.kookBot != null) {
            getLogger().info("正在关闭旧的 KookBot 实例...");
            try {
                // 注销与旧 KookBot 实例相关的消息监听器
                // AbstractKookMessage.unRegister() 可能需要旧的 kookBot 实例来正确注销 Kook 平台的监听器
                if (abstractKookMessages != null) {
                    abstractKookMessages.forEach(AbstractKookMessage::unRegister);
                }
                // 如果 KookBot 本身有需要注销的通用监听器
                if (!this.kookBot.isInvalid()) { // 假设 isInvalid 是一个快速检查
                    this.kookBot.unRegisterKookListener(); // 假设 KookBot 类有此方法
                }
                this.kookBot.close(); // 关闭 KookBot 连接和资源
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "关闭旧 KookBot 实例时出错: ", e);
            }
            this.kookBot = null; // 立即将 kookBot 设为 null
            getLogger().info("旧 KookBot 实例已关闭。");
        }


        getLogger().info("正在异步创建新的 KookBot 实例...");
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                KookBot newBotInstance = new KookBot(this);
                getServer().getScheduler().runTask(this, () -> {
                    this.kookBot = newBotInstance;
                    getLogger().info("Kook 机器人已异步重新加载。");

                    // KookBot 已重新加载, 现在重新加载依赖它的系统
                    getLogger().info("KookBot 已更新, 重新加载消息系统和相关命令组件...");
                    reloadMessageSystem(); // 这会调用 setupMessageHandlers，使用新的 kookBot 注册监听器
                    if (commandManager != null) {
                        commandManager.init(); // 重新初始化命令, LinkCmd 将获取新的 kookBot
                    }
                    getLogger().info("依赖 KookBot 的组件已使用新实例重新加载。");
                });
            } catch (Exception e) {
                this.kookBot = null; // 如果重载失败，确保 kookBot 为 null
                getLogger().log(Level.SEVERE, "KookBot 异步重新加载失败。与 Kook 相关的功能可能无法使用。", e);
            }
        });
    }

    public void reloadPluginConfig() {
        super.reloadConfig();
        BaseConfig.init(this);
        getLogger().info("主配置文件 (config.yml) 已重新加载。");
    }

    public void reloadMessageSystem() {
        getLogger().info("正在重新加载消息系统...");
        // 1. 注销旧的消息处理器 (abstractKookMessages 在声明时已初始化, 不会为 null)
        // unRegister 可能需要 kookBot 实例 (通常是旧的实例) 来注销 Kook 平台的监听器
        abstractKookMessages.forEach(AbstractKookMessage::unRegister);
        abstractKookMessages.clear(); // 清空列表

        // 2. 重新设置消息处理器 (会使用当前 this.kookBot，此时应为新的 KookBot 实例)
        setupMessageHandlers();
        getLogger().info("消息系统已重新加载。");
    }

    private void setupMessageHandlers() {
        // abstractKookMessages 列表应由调用者 (如 reloadMessageSystem 或 onEnable) 确保是空的或已清理
        // 此处不再需要 abstractKookMessages = new ArrayList<>();
        // 但为了确保此方法的独立性和健壮性，可以保留 clear()
        if (this.abstractKookMessages == null) { // 理论上不应发生，因为已在字段声明时初始化
            this.abstractKookMessages = new ArrayList<>();
        }
        this.abstractKookMessages.clear(); // 确保从一个干净的列表开始添加

        // 确保消息配置文件存在
        Arrays.asList("PlayerJoinMessage", "PlayerChatMessage", "PlayerLinkKookMessage").forEach(message -> {
            String filePath = "message/" + message + ".yml"; // 建议使用常量定义文件名和路径
            File file = new File(getDataFolder(), filePath);
            if (!file.exists()) {
                saveResource(filePath, false);
            }
        });
        try {
            // 加载并注册 PlayerJoinMessage 处理器
            PlayerJoinMessage playerJoinMessage = new PlayerJoinMessage(this, YamlConfiguration.loadConfiguration(
                    new File(getDataFolder(), "message/PlayerJoinMessage.yml")
            ));
            abstractKookMessages.add(playerJoinMessage);
            playerJoinMessage.register(); // register() 内部会使用 getKookBot()

            // 加载并注册 PlayerChatMessage 处理器
            PlayerChatMessage playerChatMessage = new PlayerChatMessage(this, YamlConfiguration.loadConfiguration(
                    new File(getDataFolder(), "message/PlayerChatMessage.yml")
            ));
            abstractKookMessages.add(playerChatMessage);
            playerChatMessage.register();

            // 加载并注册 PlayerLinkMessage 处理器
            PlayerLinkMessage playerLinkMessage = new PlayerLinkMessage(this, YamlConfiguration.loadConfiguration(
                    new File(getDataFolder(), "message/PlayerLinkKookMessage.yml")
            ));
            abstractKookMessages.add(playerLinkMessage);
            playerLinkMessage.register();

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "加载消息处理器时发生错误:", e);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info(getName() + " 正在卸载...");

        // 1. 关闭 KookBot
        if (this.kookBot != null) {
            try {
                this.kookBot.close();
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "关闭 KookBot 时出错: ", e);
            }
            this.kookBot = null;
        }

        // 2. 注销并清理消息处理器
        if (abstractKookMessages != null) {
            try {
                abstractKookMessages.forEach(AbstractKookMessage::unRegister);
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "注销消息处理器时出错: ", e);
            }
            abstractKookMessages.clear();
            abstractKookMessages = null;
        }

        // 3. 关闭数据管理器
        if (DataManager.instance != null) {
            try {
                DataManager.instance.close();
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "关闭 DataManager 时出错: ", e);
            }
            // DataManager.instance = null; // 如果它是可重置的单例
        }

        // 4. 清理其他资源
        commandManager = null;
        metrics = null;

        // 5. 取消插件所有计划任务
        getServer().getScheduler().cancelTasks(this);

        getLogger().info(getName() + " 插件已卸载。");
    }
}