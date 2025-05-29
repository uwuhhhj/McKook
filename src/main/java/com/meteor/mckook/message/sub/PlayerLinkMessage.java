package com.meteor.mckook.message.sub;

import com.meteor.mckook.McKook;
import com.meteor.mckook.kook.service.LinkService;
import com.meteor.mckook.message.AbstractKookMessage;
import com.meteor.mckook.model.link.KookUser;
import com.meteor.mckook.model.link.LinkCache;
import com.meteor.mckook.util.BaseConfig;
import com.meteor.mckook.util.TextComponentHelper;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import snw.jkook.entity.User;
import snw.jkook.entity.channel.Channel;
import snw.jkook.event.EventHandler;
import snw.jkook.event.channel.ChannelMessageEvent;
import snw.jkook.event.pm.PrivateMessageReceivedEvent;

import java.util.HashMap;
import java.util.Map;

public class PlayerLinkMessage extends AbstractKookMessage {
    private LinkService linkService;

    private ConfigurationSection channel;
    private String successLinkMessage;
    private String successLinkMessageMinecraft;
    private final YamlConfiguration config;
    String verifyCode_Error_message = "你输入的不是验证码哦 " ;
    String alreadybind_Message = "你已经绑定其他玩家id了" ;
    String noneedbind_Message = "服务器未启用绑定功能" ;

    public PlayerLinkMessage(McKook plugin, YamlConfiguration yamlConfiguration) {
        super(plugin, yamlConfiguration);
        ConfigurationSection setting = plugin.getConfig().getConfigurationSection("setting");
        if (setting == null) {
            throw new IllegalStateException("config.yml 中缺少 setting 节点！");
        }
        this.channel = setting.getConfigurationSection("channel");
        this.config = yamlConfiguration;
        this.linkService = plugin.getKookBot().getService(LinkService.class);
        this.successLinkMessage = yamlConfiguration.getString("message.success.kook");
        this.successLinkMessageMinecraft = yamlConfiguration.getString("message.success.minecraft");
    }

    @Override
    public String getName() {
        return "绑定KOOK账号";
    }
    @EventHandler
    public void inputVerifyCode(PrivateMessageReceivedEvent privateMessageReceivedEvent) {
        // Debug：收到私聊事件
        getPlugin().getLogger().info("[DEBUG] 收到私聊验证码事件");

        String verifyCode = privateMessageReceivedEvent.getMessage().getComponent().toString();
        getPlugin().getLogger().info("[DEBUG] 私聊内容（验证码）: " + verifyCode);
        if (verifyCode != null) {
            // Debug：收到私聊事件不做处理直接退出
            return;
        }
        LinkCache linkCache = linkService.getLinkCache(verifyCode);
        if (linkCache != null) {
            String playerName = linkCache.getPlayerName();
            getPlugin().getLogger().info("[DEBUG] 验证码对应玩家: " + playerName);

            Map<String, String> params = new HashMap<>();
            params.put("player", playerName);

            // 绑定操作
            KookUser kookUser = linkService.link(playerName, privateMessageReceivedEvent.getUser());
            getPlugin().getLogger().info("[DEBUG] 完成绑定，Kook 用户 ID: " + kookUser.getKook_id());

            // 给用户发卡片成功消息
            privateMessageReceivedEvent.getUser()
                    .sendPrivateMessage(TextComponentHelper.json2CardComponent(successLinkMessage, params));
            getPlugin().getLogger().info("[DEBUG] 已发送绑定成功卡片消息给 Kook 用户");

            // 如果该玩家在线，发 Minecraft 消息
            Player player = Bukkit.getPlayerExact(playerName);
            if (player != null) {
                getPlugin().getLogger().info("[DEBUG] 玩家在线，准备发送游戏内消息: " + playerName);

                params.put("@user-nickname@", kookUser.getNickName());
                BaseConfig.instance.getMessageBox()
                        .getMessageList(params, this.successLinkMessageMinecraft)
                        .forEach(s -> player.sendMessage(s));

                getPlugin().getLogger().info("[DEBUG] 已发送游戏内绑定成功消息");
            } else {
                getPlugin().getLogger().info("[DEBUG] 玩家不在线，跳过游戏内消息发送: " + playerName);
            }
        } else {
            getPlugin().getLogger().info("[DEBUG] 无效验证码或已过期: " + verifyCode);
        }
    }



    @EventHandler
    public void onVerifyCode(ChannelMessageEvent channelMessageEvent) {
        //配置文件中是否启用了绑定功能？未启用就返回
        if (!config.getBoolean("enable-code-binding", false)) {
            getPlugin().getKookBot().sendPlainText(getUseChannelList(), noneedbind_Message);
            return;
        }

        try {
            String configuredId = channel.getString("白名单申请");
            if (configuredId == null) {
                getPlugin().getLogger().warning("[McKook] 未配置“白名单申请”的 ID！");
                return;
            }

            if (! channelMessageEvent.getChannel().getId().equals(configuredId)) {
                getPlugin().getLogger()
                        .warning("[McKook] 当前频道 ID="
                                + channelMessageEvent.getChannel().getId()
                                + " 不是白名单申请（应为 " + configuredId + "）！");
                return;
            }

            // 1. 解析消息内容
            String verifyCode = channelMessageEvent.getMessage().getComponent().toString();
            getPlugin().getLogger().info("[DEBUG] 消息内容（验证码）: " + verifyCode);
            // 2. 获取验证码对应玩家
            LinkCache linkCache = linkService.getLinkCache(verifyCode);
            if (linkCache == null) {
                getPlugin().getLogger().info("[DEBUG] 无效验证码或已过期");
                getPlugin().getKookBot().sendPlainText(getUseChannelList(), verifyCode_Error_message);
                return;
            }
            String playerName = linkCache.getPlayerName();
            User sender = channelMessageEvent.getMessage().getSender();
            getPlugin().getLogger().info("[DEBUG] 验证码对应玩家: " + playerName);
            getPlugin().getLogger().info("[DEBUG] 消息发送者 ID: " + sender.getId());
            if (linkService.isLinked(playerName)) {
                getPlugin().getLogger().info("[DEBUG] 玩家已绑定: " + playerName);
                return;
            }

            if (linkService.kookUserIsLinked(sender.getId())) {
                getPlugin().getLogger().info("[DEBUG] 该 Kook 用户已绑定其他账号: " + sender.getId());
                getPlugin().getKookBot().sendPlainText(getUseChannelList(), alreadybind_Message);
                return;
            }
            // 3. 执行绑定

            getPlugin().getLogger().info("[DEBUG] 执行绑定: " + playerName + " <-> " + sender.getId());
            KookUser kookUser = linkService.link(playerName, sender);

            // 4. 发送绑定成功消息
            Map<String, String> context = new HashMap<>();
            context.put("player", playerName);
            context.put("@user-nickname@", kookUser.getNickName());

            channelMessageEvent.getMessage().reply(TextComponentHelper.json2CardComponent(successLinkMessage, context));

            Player player = Bukkit.getPlayerExact(playerName);
            if (player != null) {
                BaseConfig.instance.getMessageBox()
                        .getMessageList(context, successLinkMessageMinecraft)
                        .forEach(player::sendMessage);
                getPlugin().getLogger().info("[DEBUG] 绑定完成");
            }else {
                getPlugin().getLogger().info("[DEBUG] 玩家不在线，跳过游戏内消息发送: " + playerName);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
