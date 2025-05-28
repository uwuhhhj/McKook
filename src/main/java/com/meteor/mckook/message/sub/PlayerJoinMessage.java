package com.meteor.mckook.message.sub;

import com.meteor.mckook.McKook;
import com.meteor.mckook.kook.service.LinkService;
import com.meteor.mckook.message.AbstractKookMessage;
import com.meteor.mckook.util.TextComponentHelper;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import snw.jkook.message.component.card.MultipleCardComponent;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * 处理玩家加入和离开服务器事件，并发送相应的Kook消息。
 * 启用状态由主配置文件 config.yml 控制。
 */
public class PlayerJoinMessage extends AbstractKookMessage implements Listener {

    // 配置键常量，用于 PlayerJoinKookMessage.yml
    private static final String CONFIG_KEY_MESSAGE_JOIN = "message.join";
    private static final String CONFIG_KEY_MESSAGE_QUIT = "message.quit";
    private static final String LOG_PREFIX = "[玩家加入消息] ";

    // 从主配置文件 config.yml 读取的启用状态
    private final boolean joinMessagesEnabled;
    private final boolean quitMessagesEnabled;

    private final String joinMessageFormat;
    private final String quitMessageFormat;
    private final LinkService linkService;

    public PlayerJoinMessage(McKook plugin, YamlConfiguration messageFileConfig) {
        super(plugin, messageFileConfig); // messageFileConfig 是 PlayerJoinKookMessage.yml

        // 从主配置文件 config.yml 读取启用状态
        this.joinMessagesEnabled = plugin.getConfig().getBoolean("setting.message-bridge.player-join.enabled", true);
        this.quitMessagesEnabled = plugin.getConfig().getBoolean("setting.message-bridge.player-quit.enabled", true);

        // 从 PlayerJoinKookMessage.yml 读取消息格式
        this.joinMessageFormat = messageFileConfig.getString(CONFIG_KEY_MESSAGE_JOIN);
        this.quitMessageFormat = messageFileConfig.getString(CONFIG_KEY_MESSAGE_QUIT);

        LinkService linkService1; // 局部变量以避免遮蔽字段
        if (plugin.getKookBot() != null) {
            try {
                linkService1 = plugin.getKookBot().getService(LinkService.class);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, LOG_PREFIX + "获取 LinkService 失败: " + e.getMessage(), e);
                linkService1 = null;
            }
        } else {
            plugin.getLogger().warning(LOG_PREFIX + "KookBot 未初始化，无法获取 LinkService。Kook 绑定状态将不会显示。");
            linkService1 = null;
        }
        this.linkService = linkService1;

        // 记录实际启用的功能
        if (this.joinMessagesEnabled) {
            plugin.getLogger().info(LOG_PREFIX + "玩家加入消息已启用 (根据 config.yml)。");
        } else {
            plugin.getLogger().info(LOG_PREFIX + "玩家加入消息已禁用 (根据 config.yml)。");
        }
        if (this.quitMessagesEnabled) {
            plugin.getLogger().info(LOG_PREFIX + "玩家离开消息已启用 (根据 config.yml)。");
        } else {
            plugin.getLogger().info(LOG_PREFIX + "玩家离开消息已禁用 (根据 config.yml)。");
        }
    }

    @Override
    public String getName() {
        return "玩家加入与离开消息";
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent joinEvent) {
        if (!this.joinMessagesEnabled) { // 使用独立的启用标志
            return;
        }

        Map<String, String> context = context(joinEvent);
        String playerName = joinEvent.getPlayer().getName();
        String kookStatusText;

        if (this.linkService != null) {
            try {
                boolean isLinked = this.linkService.isLinked(playerName);
                kookStatusText = isLinked ? "已绑定Kook" : "未绑定Kook";
            } catch (Exception e) {
                getPlugin().getLogger().log(Level.WARNING, LOG_PREFIX + "查询玩家 " + playerName + " 的Kook绑定状态时出错: " + e.getMessage(), e);
                kookStatusText = "Kook绑定状态查询异常";
            }
        } else {
            kookStatusText = "Kook绑定状态未知 (服务未加载)";
        }
        context.put("kook_status", kookStatusText);

        if (this.joinMessageFormat != null && !this.joinMessageFormat.isEmpty()) {
            sendMessageToKook(this.joinMessageFormat, context);
        } else {
            getPlugin().getLogger().warning(LOG_PREFIX + "配置文件 PlayerJoinKookMessage.yml 中 '" + CONFIG_KEY_MESSAGE_JOIN + "' 未配置或为空。");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent quitEvent) {
        if (!this.quitMessagesEnabled) { // 使用独立的启用标志
            return;
        }

        Map<String, String> context = context(quitEvent);

        if (this.quitMessageFormat != null && !this.quitMessageFormat.isEmpty()) {
            sendMessageToKook(this.quitMessageFormat, context);
        } else {
            getPlugin().getLogger().warning(LOG_PREFIX + "配置文件 PlayerJoinKookMessage.yml 中 '" + CONFIG_KEY_MESSAGE_QUIT + "' 未配置或为空。");
        }
    }

    private void sendMessageToKook(String messageTemplate, Map<String, String> context) {
        if (getPlugin().getKookBot() == null) {
            getPlugin().getLogger().warning(LOG_PREFIX + "KookBot 实例为空，无法发送消息。");
            return;
        }

        // getUseChannelList() 仍然从传递给父类的 messageFileConfig (PlayerJoinKookMessage.yml) 中读取 "channels"
        List<String> channelIds = getUseChannelList();
        if (channelIds == null || channelIds.isEmpty()) {
            getPlugin().getLogger().warning(LOG_PREFIX + "没有在 PlayerJoinKookMessage.yml 中配置可用的Kook频道ID，无法发送消息。");
            return;
        }

        MultipleCardComponent cardComponent;
        try {
            cardComponent = TextComponentHelper.json2CardComponent(messageTemplate, context);
        } catch (Exception e) {
            getPlugin().getLogger().log(Level.WARNING, LOG_PREFIX + "将JSON消息模板转换为CardComponent时发生错误: " + e.getMessage() + "。模板: " + messageTemplate, e);
            return;
        }

        if (cardComponent == null) {
            getPlugin().getLogger().warning(LOG_PREFIX + "无法将JSON消息模板转换为CardComponent (返回为null)，消息未发送。模板: " + messageTemplate);
            return;
        }

        try {
            getPlugin().getKookBot().sendMessage(channelIds, cardComponent);
        } catch (Exception e) {
            getPlugin().getLogger().log(Level.SEVERE, LOG_PREFIX + "发送Kook消息时发生错误: " + e.getMessage(), e);
        }
    }
}