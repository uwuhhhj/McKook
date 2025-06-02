package com.meteor.mckook.message.sub;

import com.meteor.mckook.McKook;
import com.meteor.mckook.kook.service.LinkService;
import com.meteor.mckook.message.AbstractKookMessage;
import com.meteor.mckook.model.link.KookUser;
import com.meteor.mckook.model.link.LinkCache;
import com.meteor.mckook.util.TextComponentHelper;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import snw.jkook.entity.User;
import snw.jkook.event.EventHandler;
import snw.jkook.event.channel.ChannelMessageEvent;
import snw.jkook.event.pm.PrivateMessageReceivedEvent;
import org.bukkit.ChatColor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerLinkMessage extends AbstractKookMessage {
    private LinkService linkService;

    private ConfigurationSection channel;
    private String successLinkMessage;
    private List<String> successLinkMessageMinecraft;
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
        this.successLinkMessage = config.getString("message.success.kook");
        this.successLinkMessageMinecraft = config.getStringList("message.success.minecraft");
    }

    @Override
    public String getName() {
        return "玩家验证码绑定KOOK账号";
    }
    //私聊方法已启用了，还是老实用频道绑定吧
    //避免滥发消息频道可以设置乌龟
    @EventHandler
    public void inputVerifyCode(PrivateMessageReceivedEvent privateMessageReceivedEvent) {
        getPlugin().getLogger().info("[DEBUG] 收到私聊验证码事件");
    }



    @EventHandler
    public void onVerifyCode(ChannelMessageEvent channelMessageEvent) {

        try {
            String configuredId = channel.getString("白名单申请");
            if (configuredId == null) {
                getPlugin().getLogger().warning("[McKook] 未配置“白名单申请”的 ID！");
                return;
            }

            if (! channelMessageEvent.getChannel().getId().equals(configuredId)) {
                return;
            }
            //配置文件中是否启用了绑定功能？未启用就返回
            if (!config.getBoolean("enable-code-binding", false)) {
                getPlugin().getKookBot().sendPlainText(getUseChannelList(), noneedbind_Message);
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

            // 4. KOOK内发送绑定成功消息
            Map<String, String> context = new HashMap<>();
            context.put("player", playerName);
            context.put("@user-nickname@", kookUser.getNickName());

            channelMessageEvent.getMessage().reply(TextComponentHelper.json2CardComponent(successLinkMessage, context));
            // 5. 游戏内发送绑定成功消息
            Player player = Bukkit.getPlayerExact(playerName);
            if (player != null) {
                for (String message : successLinkMessageMinecraft) {
                    message = message.replace("@user-nickname@", kookUser.getNickName());
                    message = ChatColor.translateAlternateColorCodes('&', message);
                    player.sendMessage(message);
                }
                getPlugin().getLogger().info("[DEBUG] 绑定完成");
            }else {
                getPlugin().getLogger().info("[DEBUG] 玩家不在线，跳过游戏内消息发送: " + playerName);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
