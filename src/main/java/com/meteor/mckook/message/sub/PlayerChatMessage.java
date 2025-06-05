package com.meteor.mckook.message.sub;

import com.meteor.mckook.McKook;
import com.meteor.mckook.message.AbstractKookMessage;
import com.meteor.mckook.config.Config;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import snw.jkook.entity.User;
import snw.jkook.event.EventHandler;
import snw.jkook.event.channel.ChannelMessageEvent;

import java.util.List;
import java.util.logging.Level;

public class PlayerChatMessage extends AbstractKookMessage {
    private final boolean serverToKookEnabled;
    private final boolean kookToServerEnabled;
    private final ConfigurationSection kookChannelSettings; // 用于获取Kook频道ID，例如 "闲聊频道"
    private final String minecraftMessageFormat; // 重命名以更清晰
    private static final String LOG_PREFIX = "[KOOK消息] ";

    // String serverstar_message = "服务器已上线"; // 这条消息似乎只在构造函数中发送一次，可以考虑移到插件启动逻辑中

    public PlayerChatMessage(McKook plugin, YamlConfiguration messageFileConfig) { // messageFileConfig 是 PlayerChatMessage.yml
        super(plugin, messageFileConfig);

        // 从主配置文件 config.yml 读取启用状态
        this.serverToKookEnabled = Config.get().isServerChatToKookEnabled();
        this.kookToServerEnabled = Config.get().isKookChatToServerEnabled();

        // 从 PlayerChatMessage.yml 读取 Kook->服务器 的消息格式
        this.minecraftMessageFormat = messageFileConfig.getString("message_to_minecraft");
        if (this.minecraftMessageFormat == null || this.minecraftMessageFormat.isEmpty()) {
            plugin.getLogger().warning(LOG_PREFIX + "PlayerChatMessage.yml 中的 'message_to_minecraft' 未配置或为空。");
        }

        ConfigurationSection channelSection = Config.get().getChannelSection();
        if (channelSection == null) {
            plugin.getLogger().severe(LOG_PREFIX + "config.yml 中缺少 setting.channel 节点！Kook频道配置可能无法加载。");
            this.kookChannelSettings = null;
        } else {
            this.kookChannelSettings = channelSection;
        }

        // 记录实际启用的功能
        plugin.getLogger().info(LOG_PREFIX + "服务器聊天转发到Kook功能: " + (this.serverToKookEnabled ? "已启用" : "已禁用") + " (根据 config.yml)");
        plugin.getLogger().info(LOG_PREFIX + "Kook聊天转发到服务器功能: " + (this.kookToServerEnabled ? "已启用" : "已禁用") + " (根据 config.yml)");


        // 这条服务器上线消息建议移到插件 onEnable 逻辑中，确保只在插件真正启动且Bot连接后发送
        // 并且应该检查 KookBot 是否为 null
        if (getPlugin().getKookBot() != null) {
            // String serverstar_message = "Minecraft 服务器已连接至 Kook。";
            // getPlugin().getKookBot().sendPlainText(getUseChannelList(), serverstar_message);
            // getUseChannelList() 会从 PlayerChatMessage.yml 读取 "channels"
            // 确保 PlayerChatMessage.yml 中的 channels 配置正确用于此初始消息
            // 或者为这条特定消息定义一个专门的频道
        } else {
            plugin.getLogger().warning(LOG_PREFIX + "KookBot 未完全初始化，无法发送服务器上线消息。");
        }
    }

    @Override
    public String getName() {
        return "聊天消息桥接";
    }

    private boolean isInAllowedWorld(Player player) { // 重命名以更清晰
        List<String> blackWorlds = Config.get().getBlackWorlds();
        String worldName = player.getWorld().getName();
        return !blackWorlds.contains(worldName); // 如果黑名单包含该世界，则不允许
    }

    private String formatMinecraftMessage(ChannelMessageEvent event) {
        if (this.minecraftMessageFormat == null || this.minecraftMessageFormat.isEmpty()) {
            // 提供一个默认格式以防万一
            return "[KOOK] " + event.getMessage().getSender().getName() + ": " + event.getMessage().getComponent().toString();
        }

        User sender = event.getMessage().getSender();
        String messageContent = (event.getMessage().getComponent() != null)
                ? event.getMessage().getComponent().toString() // 注意：这可能包含Kook的特定格式代码
                : "(空消息)";
        return ChatColor.translateAlternateColorCodes('&',
                minecraftMessageFormat.replace("{user}", sender.getName())
                        .replace("{message}", messageContent));
    }

    @EventHandler // Kook SDK 的事件处理器
    public void onKookChannelMessage(ChannelMessageEvent event) {
        if (!this.kookToServerEnabled) {
            return;
        }
        // 确保 kookChannelSettings 已加载
        if (this.kookChannelSettings == null) {
            getPlugin().getLogger().warning(LOG_PREFIX + "Kook频道配置 (setting.channel) 未加载，无法处理Kook消息转发。");
            return;
        }

        // 确保消息来自配置的“闲聊频道”
        String configuredChatChannelId = this.kookChannelSettings.getString("闲聊频道");
        if (configuredChatChannelId == null || configuredChatChannelId.isEmpty()) {
            getPlugin().getLogger().warning(LOG_PREFIX + "config.yml 中 'setting.channel.闲聊频道' 未配置ID，无法处理Kook消息转发。");
            return;
        }

        if (!event.getChannel().getId().equals(configuredChatChannelId)) {
            return; // 不是来自我们关心的闲聊频道
        }

        try {
            String msgToMinecraft = formatMinecraftMessage(event);
            // getPlugin().getLogger().info("[DEBUG] 转发 KOOK 消息至在线玩家: " + msgToMinecraft); // Debug 日志

            Bukkit.getScheduler().runTask(getPlugin(), () -> { // 确保在主线程发送消息给玩家
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (isInAllowedWorld(player)) {
                        player.sendMessage(msgToMinecraft);
                    }
                }
            });

        } catch (Exception e) {
            getPlugin().getLogger().log(Level.SEVERE, LOG_PREFIX + "处理Kook消息转发到服务器时出错: " + e.getMessage(), e);
        }
    }

    @org.bukkit.event.EventHandler // Bukkit 的事件处理器
    public void onMinecraftChat(AsyncPlayerChatEvent event) {
        if (!this.serverToKookEnabled) {
            return;
        }

        Player player = event.getPlayer();
        if (!isInAllowedWorld(player)) {
            return;
        }

        // 简单的消息格式，可以考虑也做成可配置的
        String messageToKook = player.getName() + ": " + event.getMessage();

        // getUseChannelList() 从 PlayerChatMessage.yml 的 "channels" 读取频道列表
        List<String> targetKookChannels = getUseChannelList();
        if (targetKookChannels == null || targetKookChannels.isEmpty()) {
            getPlugin().getLogger().warning(LOG_PREFIX + "PlayerChatMessage.yml 中未配置 'channels'，无法将服务器聊天转发到Kook。");
            return;
        }

        if (getPlugin().getKookBot() != null) {
            try {
                getPlugin().getKookBot().sendPlainText(targetKookChannels, messageToKook);
            } catch (Exception e) {
                getPlugin().getLogger().log(Level.SEVERE, LOG_PREFIX + "发送服务器聊天消息到Kook时出错: " + e.getMessage(), e);
            }
        } else {
            getPlugin().getLogger().warning(LOG_PREFIX + "KookBot 未初始化，无法将服务器聊天转发到Kook。");
        }
    }
}