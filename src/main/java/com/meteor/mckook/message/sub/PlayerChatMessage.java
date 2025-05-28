package com.meteor.mckook.message.sub;

import com.meteor.mckook.McKook;
import com.meteor.mckook.message.AbstractKookMessage;
import com.meteor.mckook.util.BaseConfig;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import snw.jkook.entity.Guild;
import snw.jkook.entity.User;
import snw.jkook.entity.channel.Channel;
import snw.jkook.entity.channel.TextChannel;
import snw.jkook.event.EventHandler;
import snw.jkook.event.channel.ChannelMessageEvent;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class PlayerChatMessage extends AbstractKookMessage {

    private final ConfigurationSection channel;
    private final String minecraftMessage;
    String serverstar_message = "服务器已上线";
    public PlayerChatMessage(McKook plugin, YamlConfiguration yamlConfiguration) {
        super(plugin, yamlConfiguration);

        this.minecraftMessage = yamlConfiguration.getString("message_to_minecraft");
        ConfigurationSection setting = plugin.getConfig().getConfigurationSection("setting");
        if (setting == null) {
            throw new IllegalStateException("config.yml 中缺少 setting 节点！");
        }

        this.channel = setting.getConfigurationSection("channel");
        if (this.channel == null) {
            throw new IllegalStateException("config.yml 中缺少 setting.channel 节点！");
        }

        // Debug 输出所有可见频道
        getPlugin().getLogger().info("[DEBUG] 初始化 PlayerChatMessage");
        getPlugin().getLogger().info("[DEBUG] 可见 KOOK 频道列表：");
        getPlugin().getKookBot().getChannelMap().forEach((id, ch) ->
                getPlugin().getLogger().info("[DEBUG] 频道 ID: " + id + " -> 名称: " + ch.getName())
                );

        getPlugin().getKookBot().sendPlainText(getUseChannelList(), serverstar_message);
    }

    @Override
    public String getName() {
        return "聊天消息";
    }

    private boolean isInPassWorld(Player player) {
        List<String> blackWorlds = BaseConfig.instance.getConfig().getStringList("setting.black-worlds");
        String worldName = player.getWorld().getName();
        boolean allowed = blackWorlds == null || !blackWorlds.contains(worldName);
        getPlugin().getLogger().info("[DEBUG] 玩家 " + player.getName() + " 当前世界: " + worldName + " -> " + (allowed ? "允许同步" : "在黑名单中，跳过"));
        return allowed;
    }

    private String getMinecraftMessage(ChannelMessageEvent event) {
        User sender = event.getMessage().getSender();
        String messageContent = (event.getMessage().getComponent() != null)
                ? event.getMessage().getComponent().toString()
                : "(空消息)";
        return ChatColor.translateAlternateColorCodes('&',
                minecraftMessage.replace("{user}", sender.getName())
                        .replace("{message}", messageContent));
    }

    @EventHandler
    public void onChannelMessage(ChannelMessageEvent event) {
        getPlugin().getLogger().info("[DEBUG] 收到 KOOK -> Minecraft 消息事件");

        try {
            String configuredId = channel.getString("闲聊频道");
            if (configuredId == null) {
                getPlugin().getLogger().warning("[McKook] 未配置“闲聊频道”的 ID！");
                return;
            }


            if (! event.getChannel().getId().equals(configuredId)) {
                getPlugin().getLogger()
                        .warning("[McKook] 当前频道 ID="
                                + event.getChannel().getId()
                                + " 不是闲聊频道（应为 " + configuredId + "）！");
                return;
            }


            String msg = getMinecraftMessage(event);
            getPlugin().getLogger().info("[DEBUG] 转发 KOOK 消息至在线玩家: " + msg);

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (isInPassWorld(player)) {
                    player.sendMessage(msg);
                }
            }

        } catch (Exception e) {
            getPlugin().getLogger().severe("处理 KOOK 消息转发时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @org.bukkit.event.EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        getPlugin().getLogger().info("[DEBUG] 玩家聊天事件触发: " + event.getPlayer().getName());

        Player player = event.getPlayer();
        if (!isInPassWorld(player)) return;
        String formatted = "[远征] " + player.getName() + " >> " + event.getMessage();

        getPlugin().getLogger().info("[DEBUG] 同步消息至 KOOK 频道 name=" + getUseChannelList() + " 内容: " + formatted);
        getPlugin().getKookBot().sendPlainText(getUseChannelList(), formatted);
    }
}
