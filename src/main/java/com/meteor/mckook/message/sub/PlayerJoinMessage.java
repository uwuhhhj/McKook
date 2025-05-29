package com.meteor.mckook.message.sub;

import com.meteor.mckook.McKook;
import com.meteor.mckook.kook.service.LinkService; // 新增导入
import com.meteor.mckook.message.AbstractKookMessage;
import com.meteor.mckook.util.TextComponentHelper;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;

/**
 * 加入事件
 */
public class PlayerJoinMessage extends AbstractKookMessage {

    private String joinMessage;
    private String quitMessage;
    private LinkService linkService;

    public PlayerJoinMessage(McKook plugin, YamlConfiguration yamlConfiguration) {
        super(plugin, yamlConfiguration);
        this.joinMessage = yamlConfiguration.getString("message.join");
        this.quitMessage = yamlConfiguration.getString("message.quit");

        // 初始化 LinkService
        if (plugin.getKookBot() != null) {
            this.linkService = plugin.getKookBot().getService(LinkService.class);
        } else {
            plugin.getLogger().warning("[PlayerJoinMessage] KookBot 未初始化，无法获取 LinkService。Kook 绑定状态将不会显示。");
            this.linkService = null;
        }
    }

    @Override
    public String getName() {
        return "玩家加入消息";
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent joinEvent) {
        // context(joinEvent) 应该已经包含了 {player} -> playerName 的映射
        Map<String, String> context = context(joinEvent);
        String playerName = joinEvent.getPlayer().getName();

        String kookStatusText;
        if (this.linkService != null) {
            boolean isLinked = this.linkService.isLinked(playerName);
            kookStatusText = isLinked ? "已绑定" : "未绑定";
        } else {
            kookStatusText = "状态未知 (服务异常)";
        }
        context.put("kook_status", kookStatusText); // 将绑定状态添加到上下文中

        if (this.joinMessage != null && !this.joinMessage.isEmpty()) {
            getPlugin().getKookBot().sendMessage(getUseChannelList(), TextComponentHelper.json2CardComponent(this.joinMessage, context));
        } else {
            getPlugin().getLogger().warning("[PlayerJoinMessage]配置文件中 'message.join' 未配置或为空。");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent quitEvent){
        Map<String,String> context = context(quitEvent);
        getPlugin().getKookBot().sendMessage(getUseChannelList(), TextComponentHelper.json2CardComponent(this.quitMessage,context));
    }

}
