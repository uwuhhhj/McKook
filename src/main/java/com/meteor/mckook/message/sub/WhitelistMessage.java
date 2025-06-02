package com.meteor.mckook.message.sub;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meteor.mckook.McKook;
import com.meteor.mckook.kook.service.LinkService;
import com.meteor.mckook.message.AbstractKookMessage;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection; // 新增导入
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level; // 新增导入

public class WhitelistMessage extends AbstractKookMessage {

    /**
     * 被拦截者缓存 - 用于临时存储需要进行行为限制的未绑定玩家
     */
    private final HashMap<Player, Boolean> unlinkedPlayerCache = new HashMap<>(); // 重命名 cache 为 unlinkedPlayerCache 以明确用途

    // 新增：Title提醒相关字段
    private final boolean titleReminderEnabled; // 从 config.yml 的 setting.whitelist.title-reminder.enabled 读取
    private final long titleReminderIntervalTicks; // 从 config.yml 的 setting.whitelist.title-reminder.interval-seconds 计算
    private final Map<Player, Integer> titleReminderTasks = new HashMap<>(); // 存储每个玩家的Title提醒任务ID

    private final String channelName; // 从 WhitelistMessage.yml 读取
    private final boolean whitelistModuleEnabled; // 从 config.yml 的 setting.whitelist.enable 读取
    private final boolean actionCheckEnabled; // 新增：从 config.yml 的 setting.whitelist.check-range.action 读取

    private final List<String> promptLinkMessageTemplate; // 从 WhitelistMessage.yml 读取
    private final String kickForUnlinkedMessageTemplate; // 从 WhitelistMessage.yml 读取
    private final long kickDelayTicks; // 从 config.yml 计算

    private final LinkService linkService;
    private final Cache<String, String> playerToVerifyCodeCache;
    // ... 其他字段 ...
    private final YamlConfiguration messageFileConfiguration; // 新增字段
    private static final String LOG_PREFIX = "[白名单检测 PlayerJoinEvent] ";

    public WhitelistMessage(McKook plugin, YamlConfiguration yamlConfiguration) { // yamlConfiguration 是 WhitelistMessage.yml
        super(plugin, yamlConfiguration);
        // 从 WhitelistMessage.yml 读取频道名称

        this.messageFileConfiguration = yamlConfiguration; // 存储传入的配置对象
        this.channelName = yamlConfiguration.getString("channel", "白名单申请");

        // 从主配置文件 config.yml 读取白名单设置
        ConfigurationSection whitelistSettings = plugin.getConfig().getConfigurationSection("setting.whitelist");
        if (whitelistSettings != null) {
            this.whitelistModuleEnabled = whitelistSettings.getBoolean("enable", false);
            int kickDelaySeconds = whitelistSettings.getInt("kick-delay-seconds", 10);
            this.kickDelayTicks = kickDelaySeconds * 20L;
            // 读取 action 设置
            ConfigurationSection checkRangeSettings = whitelistSettings.getConfigurationSection("check-range");
            if (checkRangeSettings != null) {
                this.actionCheckEnabled = checkRangeSettings.getBoolean("action", false);
            } else {
                this.actionCheckEnabled = false; // 默认禁用 action 检查
                plugin.getLogger().warning(LOG_PREFIX + "主配置文件 config.yml 中 'setting.whitelist.check-range' 部分未找到。移动限制功能将禁用。");
            }

            // 新增：读取Title提醒设置
            ConfigurationSection titleReminderSettings = whitelistSettings.getConfigurationSection("title-reminder");
            if (titleReminderSettings != null) {
                this.titleReminderEnabled = titleReminderSettings.getBoolean("enabled", true);
                int intervalSeconds = titleReminderSettings.getInt("interval-seconds", 30);
                this.titleReminderIntervalTicks = intervalSeconds * 20L;
                plugin.getLogger().info(LOG_PREFIX + "Title提醒功能已" + (this.titleReminderEnabled ? "启用" : "禁用") + 
                    "，间隔: " + intervalSeconds + "秒");
            } else {
                this.titleReminderEnabled = true; // 默认启用
                this.titleReminderIntervalTicks = 30 * 20L; // 默认30秒
                plugin.getLogger().warning(LOG_PREFIX + "主配置文件 config.yml 中 'setting.whitelist.title-reminder' 部分未找到。使用默认设置。");
            }
        } else {
            plugin.getLogger().warning(LOG_PREFIX + "主配置文件 config.yml 中未找到 'setting.whitelist' 部分。将使用默认设置禁用白名单功能。");
            this.whitelistModuleEnabled = false;
            this.actionCheckEnabled = false;
            this.kickDelayTicks = 10 * 20L; // 默认10秒
            this.titleReminderEnabled = true; // 默认启用
            this.titleReminderIntervalTicks = 30 * 20L; // 默认30秒
        }

        // 从 WhitelistMessage.yml 读取消息模板
        this.promptLinkMessageTemplate = yamlConfiguration.getStringList("message.prompt-link-on-join");
        this.kickForUnlinkedMessageTemplate = yamlConfiguration.getString("message.kick-for-unlinked",
                "&cYou have been kicked because your account is not linked. Your code: {verifyCode}. Please use it in Kook channel: {channel_name}.");

        if (plugin.getKookBot() != null) {
            try {
                this.linkService = plugin.getKookBot().getService(LinkService.class);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, LOG_PREFIX + "获取 LinkService 失败: " + e.getMessage(), e);
                throw new IllegalStateException("LinkService could not be initialized for WhitelistMessage.", e);
            }
        } else {
            this.linkService = null; // linkService 将为 null
            plugin.getLogger().severe(LOG_PREFIX + "KookBot 未初始化。白名单验证功能将不可用。");
            // 如果 LinkService 对此模块至关重要，可以考虑在此处抛出异常或强制禁用模块
            // this.whitelistModuleEnabled = false; // 如果 LinkService 必须，则强制禁用
        }

        this.playerToVerifyCodeCache = Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(500)
                .build();

        if (this.whitelistModuleEnabled) {
            plugin.getLogger().info(LOG_PREFIX + "白名单模块已启用 (根据 config.yml)。");
            if (this.actionCheckEnabled) {
                plugin.getLogger().info(LOG_PREFIX + "未绑定玩家移动限制功能已启用。");
            } else {
                plugin.getLogger().info(LOG_PREFIX + "未绑定玩家移动限制功能已禁用。");
            }
            if (this.linkService == null) {
                plugin.getLogger().warning(LOG_PREFIX + "警告: 白名单模块已启用，但 LinkService 不可用！此功能可能无法正常工作。");
            }
        } else {
            plugin.getLogger().info(LOG_PREFIX + "白名单模块已禁用 (根据 config.yml)。");
        }
    }

    @Override
    public String getName() {
        return "服务器白名单处理";
    }

    @org.bukkit.event.EventHandler
    public void onJoin(PlayerJoinEvent joinEvent) {
        Player player = joinEvent.getPlayer();
        String playerName = player.getName();

        // 检查1: 白名单总模块是否启用
        if (!this.whitelistModuleEnabled) {
            return;
        }
        // 检查2: LinkService 是否可用
        if (this.linkService == null) {
            getPlugin().getLogger().warning(LOG_PREFIX + "LinkService 未初始化，无法对玩家 " + playerName + " 执行白名单检查。");
            return;
        }

        // 检查3: 玩家是否已经绑定
        if (linkService.isLinked(playerName)) {
            getPlugin().getLogger().info(LOG_PREFIX + "玩家 " + playerName + " 已绑定KOOK账户。");
            // 如果玩家之前因为某些原因在缓存中，确保移除
            if (this.unlinkedPlayerCache.containsKey(player)) {
                this.unlinkedPlayerCache.remove(player);
                getPlugin().getLogger().info(LOG_PREFIX + "玩家 " + playerName + " 已绑定，从临时限制缓存中移除。");
            }
            return;
        }
        // --- 如果执行到这里，说明玩家未绑定 ---
        // 检查4: 如果 action 限制已启用，则将未绑定玩家加入缓存
        if (this.actionCheckEnabled) {
            this.unlinkedPlayerCache.put(player, true);
            getPlugin().getLogger().info(LOG_PREFIX + "玩家 " + playerName + " 未绑定且 action 检查已启用，已添加到临时行为限制缓存。");
        } else {
            // 如果 action 检查未启用，确保玩家不在缓存中（以防万一，例如配置重载后状态不一致）
            if (this.unlinkedPlayerCache.containsKey(player)) {
                this.unlinkedPlayerCache.remove(player);
                getPlugin().getLogger().info(LOG_PREFIX + "玩家 " + playerName + " 未绑定但 action 检查已禁用，从临时限制缓存中移除（如果存在）。");
            }
        }
        // 处理未绑定玩家
        processUnlinkedPlayerOnJoin(player, playerName);
    }
    private void processUnlinkedPlayerOnJoin(Player player, String playerName) {
        // 1. 生成/获取验证码
        String verifyCode = playerToVerifyCodeCache.getIfPresent(playerName);
        if (verifyCode == null || linkService.getLinkCache(verifyCode) == null) {
            verifyCode = linkService.buildVerifyCode(playerName);
            playerToVerifyCodeCache.put(playerName, verifyCode);
            getPlugin().getLogger().info(LOG_PREFIX + "为玩家 " + playerName + " 生成了新的验证码: " + verifyCode);
        } else {
            getPlugin().getLogger().info(LOG_PREFIX + "玩家 " + playerName + " 复用已存在的验证码: " + verifyCode);
        }

        // 2. 发送绑定提示消息
        sendLinkPromptMessage(player, verifyCode);
        getPlugin().getLogger().info(LOG_PREFIX + "玩家 " + playerName + " 未绑定KOOK账户，已发送绑定提示。");

        // 3. 如果启用了Title提醒，开始循环发送Title
        if (this.titleReminderEnabled) {
            startTitleReminder(player, verifyCode);
        }

        // 4. 根据 joinKickEnabledInConfig 决定是否计划踢出
        boolean joinKickEnabledInConfig = getPlugin().getConfig().getBoolean("setting.whitelist.check-range.join", false);
        if (joinKickEnabledInConfig) {
            getPlugin().getLogger().info(LOG_PREFIX + "玩家 " + playerName + " 的 join 踢出检查已启用，将计划踢出。");
            String kickMessage = ChatColor.translateAlternateColorCodes('&',
                    this.kickForUnlinkedMessageTemplate
                            .replace("{player}", playerName)
                            .replace("{channel_name}", this.channelName)
                            .replace("{verifyCode}", verifyCode)
            );
            scheduleKickForUnlinkedPlayer(player, playerName, kickMessage, verifyCode);
        } else {
            getPlugin().getLogger().finer(LOG_PREFIX + "玩家 " + playerName + " 的 'setting.whitelist.check-range.join' (踢出逻辑) 为 false，不执行踢出。");
        }
    }
    private void sendLinkPromptMessage(Player player, String verifyCode) {
        if (promptLinkMessageTemplate.isEmpty()) {
            player.sendMessage(ChatColor.RED + "错误: 白名单提示消息模板未在 WhitelistMessage.yml 中配置。");
            getPlugin().getLogger().warning(LOG_PREFIX + "WhitelistMessage.yml 中的 'message.prompt-link-on-join' 为空。");
            return;
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("{player}", player.getName());
        placeholders.put("{channel_name}", this.channelName);
        placeholders.put("{kick_delay_seconds}", String.valueOf(this.kickDelayTicks / 20L));

        // 发送聊天栏消息
        for (String rawLine : this.promptLinkMessageTemplate) {
            String lineWithPlaceholders = rawLine;
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                lineWithPlaceholders = lineWithPlaceholders.replace(entry.getKey(), entry.getValue());
            }

            String[] parts = lineWithPlaceholders.split("\\{verifyCode\\}", -1);
            TextComponent fullMessageLine = new TextComponent("");

            for (int i = 0; i < parts.length; i++) {
                if (!parts[i].isEmpty()) {
                    BaseComponent[] textComponents = TextComponent.fromLegacyText(
                            ChatColor.translateAlternateColorCodes('&', parts[i])
                    );
                    for (BaseComponent component : textComponents) {
                        fullMessageLine.addExtra(component);
                    }
                }

                if (i < parts.length - 1) {
                    TextComponent codeComponent = new TextComponent(verifyCode);
                    String hoverText = ChatColor.translateAlternateColorCodes('&', "&7点击复制验证码: &b" + verifyCode);
                    codeComponent.setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, verifyCode));
                    codeComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(TextComponent.fromLegacyText(hoverText))));
                    fullMessageLine.addExtra(codeComponent);
                }
            }
            player.spigot().sendMessage(fullMessageLine);
        }

        // 新增：发送 Title 提示
        YamlConfiguration msgConfig = this.messageFileConfiguration; // 修改后的代码
        String titleText = msgConfig.getString("message.prompt-title.title", "&c请绑定KOOK账户！");
        String subtitleText = msgConfig.getString("message.prompt-title.subtitle", "&f您的验证码是: &b&l{verifyCode}");
        int fadeIn = msgConfig.getInt("message.prompt-title.fadeIn", 20);
        int stay = msgConfig.getInt("message.prompt-title.stay", 100);
        int fadeOut = msgConfig.getInt("message.prompt-title.fadeOut", 20);

        // 替换占位符
        titleText = ChatColor.translateAlternateColorCodes('&', titleText.replace("{player}", player.getName()));
        subtitleText = ChatColor.translateAlternateColorCodes('&', subtitleText.replace("{verifyCode}", verifyCode).replace("{player}", player.getName()));

        // 直接使用 API 发送 Title
        player.sendTitle(titleText, subtitleText, fadeIn, stay, fadeOut);
    }
    /**
     * 计划一个任务，在指定延迟后检查并踢出未绑定的玩家。
     * @param player 目标玩家
     * @param playerName 玩家名称，用于日志
     * @param kickMessage 踢出时显示的消息
     * @param verifyCodeForLog 验证码，用于日志记录
     */
    private void scheduleKickForUnlinkedPlayer(Player player, String playerName, String kickMessage, String verifyCodeForLog) {
        Bukkit.getScheduler().runTaskLater(getPlugin(), () -> {
            if (player.isOnline() && !linkService.isLinked(playerName)) {
                player.kickPlayer(kickMessage);
                getPlugin().getLogger().info(LOG_PREFIX + "玩家 " + playerName + " 因未在 " + (this.kickDelayTicks / 20L) + " 秒内绑定KOOK而被踢出。验证码: " + verifyCodeForLog);
            } else if (player.isOnline() && linkService.isLinked(playerName)) {
                getPlugin().getLogger().info(LOG_PREFIX + "玩家 " + playerName + " 在计划踢出前已成功绑定。");
            }
        }, this.kickDelayTicks);
    }
    /**
     * 当玩家离开服务器时，如果其仍在未绑定缓存中，则将其移除。
     * @param event 玩家退出事件
     */
    @org.bukkit.event.EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        // 停止Title提醒任务
        stopTitleReminder(player);
        // 从未绑定缓存中移除
        if (this.unlinkedPlayerCache.remove(player) != null) {
            getPlugin().getLogger().info(LOG_PREFIX + "玩家 " + player.getName() + " 离线，已从临时行为限制缓存中移除。");
        }
    }
    /**
     * 新增：处理玩家移动事件，用于 action 限制
     * @param event 玩家移动事件
     */
    @org.bukkit.event.EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // 检查1: 白名单总模块是否启用
        if (!this.whitelistModuleEnabled) {
            return;
        }
        // 检查2: action 限制是否启用
        if (!this.actionCheckEnabled) {
            return;
        }

        Player player = event.getPlayer();
        if (this.unlinkedPlayerCache.containsKey(player)) {
            // 玩家在未绑定缓存中，且 action 限制已启用
            // 仅当玩家实际从一个方块移动到另一个方块时取消事件，允许视角转动
            if (event.getFrom().getBlockX() != event.getTo().getBlockX() ||
                    event.getFrom().getBlockY() != event.getTo().getBlockY() || // 考虑到Y轴移动
                    event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
                event.setCancelled(true);
            }
        }
    }

    // 新增：开始Title提醒任务
    private void startTitleReminder(Player player, String verifyCode) {
        // 如果玩家已有提醒任务，先取消
        stopTitleReminder(player);

        // 创建新的循环任务
        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(getPlugin(), () -> {
            if (player.isOnline() && !linkService.isLinked(player.getName())) {
                // 从配置文件获取Title消息
                String titleText = messageFileConfiguration.getString("message.prompt-title.title", "&c请绑定KOOK账户！");
                String subtitleText = messageFileConfiguration.getString("message.prompt-title.subtitle", "&f您的验证码是: &b&l{verifyCode}");
                int fadeIn = messageFileConfiguration.getInt("message.prompt-title.fadeIn", 20);
                int stay = messageFileConfiguration.getInt("message.prompt-title.stay", 100);
                int fadeOut = messageFileConfiguration.getInt("message.prompt-title.fadeOut", 20);

                // 替换占位符并发送Title
                titleText = ChatColor.translateAlternateColorCodes('&', titleText.replace("{player}", player.getName()));
                subtitleText = ChatColor.translateAlternateColorCodes('&', 
                    subtitleText.replace("{verifyCode}", verifyCode).replace("{player}", player.getName()));
                player.sendTitle(titleText, subtitleText, fadeIn, stay, fadeOut);
            } else {
                // 如果玩家已经绑定或离线，停止提醒
                stopTitleReminder(player);
            }
        }, this.titleReminderIntervalTicks, this.titleReminderIntervalTicks);

        // 保存任务ID
        titleReminderTasks.put(player, taskId);
        getPlugin().getLogger().info(LOG_PREFIX + "已为玩家 " + player.getName() + " 启动Title提醒任务 (TaskID: " + taskId + ")");
    }

    // 新增：停止Title提醒任务
    private void stopTitleReminder(Player player) {
        Integer taskId = titleReminderTasks.remove(player);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
            getPlugin().getLogger().info(LOG_PREFIX + "已停止玩家 " + player.getName() + " 的Title提醒任务 (TaskID: " + taskId + ")");
        }
    }
}