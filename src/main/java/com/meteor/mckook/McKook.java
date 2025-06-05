package com.meteor.mckook;

import com.meteor.mckook.command.CommandManager;
import com.meteor.mckook.kook.KookBot;
import com.meteor.mckook.kook.command.KookCommandManager;
import com.meteor.mckook.util.message.MessageHandlerManager;
import com.meteor.mckook.storage.DataManager;
import com.meteor.mckook.storage.mapper.LinkRepository;
import com.meteor.mckook.util.BaseConfig;
import com.meteor.mckook.config.Config;
import lombok.Getter;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Objects;
import java.util.logging.Level;

public final class McKook extends JavaPlugin {

    @Getter
    private KookBot kookBot;
    @Getter
    private LinkRepository linkRepository;
    private CommandManager commandManager;
    private KookCommandManager kookCommandManager;
    private Metrics metrics;

    private MessageHandlerManager messageHandlerManager;



    @Override
    public void onEnable() {
        saveDefaultConfig();
        Config.init(this);
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
                    if (messageHandlerManager == null) {
                        messageHandlerManager = new MessageHandlerManager(this);
                        messageHandlerManager.loadMessageConfigurations();
                    }
                    messageHandlerManager.setupMessageHandlers();
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
                if (messageHandlerManager != null) {
                    messageHandlerManager.unloadHandlers();
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

    public void reloadPluginConfig() {
        Config.get().reload();
        BaseConfig.init(this);
        getLogger().info("主配置文件 (config.yml) 已重新加载。");

        if (messageHandlerManager == null) {
            messageHandlerManager = new MessageHandlerManager(this);
        }
        messageHandlerManager.loadMessageConfigurations();
        getLogger().info("所有消息配置文件已重新加载。");

        getLogger().info("角色配置 (setting.roles) 已重新加载。");
    }



    public void reloadMessageSystem() {
        getLogger().info("正在重新加载消息系统...");
        if (messageHandlerManager == null) {
            messageHandlerManager = new MessageHandlerManager(this);
            messageHandlerManager.loadMessageConfigurations();
        }
        messageHandlerManager.unloadHandlers();
        messageHandlerManager.setupMessageHandlers();
        getLogger().info("消息系统已重新加载。");
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

        if (messageHandlerManager != null) {
            try {
                messageHandlerManager.unloadHandlers();
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "注销消息处理器时出错: ", e);
            }
        }

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