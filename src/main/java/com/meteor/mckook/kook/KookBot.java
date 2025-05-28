package com.meteor.mckook.kook;


import com.meteor.mckook.McKook;
import com.meteor.mckook.kook.service.LinkService;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import snw.jkook.HttpAPI;
import snw.jkook.config.file.YamlConfiguration;
import snw.jkook.entity.Guild;
import snw.jkook.entity.channel.Channel;
import snw.jkook.entity.channel.TextChannel;
import snw.jkook.event.Listener;
import snw.jkook.message.component.BaseComponent;
import snw.kookbc.impl.CoreImpl;
import snw.kookbc.impl.KBCClient;


import java.io.File;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level; // Import Level for logging

public class KookBot {

    private McKook plugin;

    private KBCClient kbcClient;
    private String guild;
    private boolean invalid;

    /**
     * 使用频道
     */
    private Map<String,Channel> channelMap;

    private Map<Class<? extends KookService>,KookService> kookServiceMap;

    public KookBot(McKook plugin){
        this.plugin = plugin;

        try {
            InputStreamReader reader = new InputStreamReader(plugin.getResource(
                    "kbc.yml"
            ));
            kbcClient = new KBCClient(new CoreImpl(), YamlConfiguration
                    .loadConfiguration(reader),
                    new File(plugin.getDataFolder(),"plugins"),
                    plugin.getConfig().getString("kook.bot-token")
            );
            kbcClient.start();
            this.guild = plugin.getConfig().getString("setting.guild");
            channelMap = new HashMap<>();
            ConfigurationSection channelConfig = plugin.getConfig().getConfigurationSection("setting.channel");
            if (channelConfig != null) { // Add null check for channelConfig
                channelConfig.getKeys(false).forEach(name -> {
                    String channelId = channelConfig.getString(name);
                    if (channelId != null && !channelId.isEmpty()) {
                        try {
                            Channel channel = httpAPI().getChannel(channelId);
                            if (channel != null) {
                                channelMap.put(name, channel);
                            } else {
                                plugin.getLogger().warning("[KookBot] Failed to get channel '" + name + "' with ID: " + channelId + ". It might be invalid or inaccessible.");
                            }
                        } catch (Exception ex) {
                            plugin.getLogger().log(Level.WARNING, "[KookBot] Error fetching channel '" + name + "' with ID: " + channelId, ex);
                        }
                    } else {
                        plugin.getLogger().warning("[KookBot] Channel ID for '" + name + "' is null or empty in config.");
                    }
                });
            } else {
                plugin.getLogger().warning("[KookBot] 'setting.channel' configuration section not found.");
            }
            plugin.getLogger().info("已连接kook bot");
        } catch (Exception e) {
            invalid = true;
            plugin.getLogger().log(Level.SEVERE, "KookBot 初始化失败 (可能是token错误或网络问题)，插件相关功能将不可用。检查配置正确后使用 /mkook reload 重载", e);
        }

        this.registerService();

    }

    private void registerService(){
        this.kookServiceMap = new HashMap<>();
        this.kookServiceMap.put(LinkService.class,new LinkService(this));
    }

    public <T extends KookService> T getService(Class<T> kookService){
        return kookService.cast(kookServiceMap.get(kookService));
    }


    /**
     * 获取使用服务器
     */
    public Guild getGuild(){
        plugin.getLogger().info("[DEBUG] KookBot: 尝试获取 Guild，ID: " + this.guild);

        if (isInvalid()) {
            plugin.getLogger().warning("[KookBot] 无法获取 Guild: KookBot 实例无效。");
            return null;
        }
        if (this.guild == null || this.guild.isEmpty()) {
            plugin.getLogger().warning("[KookBot] 无法获取 Guild: Guild ID 未配置或为空 (setting.guild)。");
            return null;
        }

        try {
            HttpAPI api = httpAPI();
            if (api == null) {
                plugin.getLogger().severe("[KookBot] 无法获取 Guild: HttpAPI 实例为 null。机器人可能未正确初始化。");
                return null;
            }
            return api.getGuild(this.guild);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[KookBot] 获取 Guild (ID: " + this.guild + ") 时发生异常: " + e.getMessage(), e);
            return null; // Return null to indicate failure
        }
    }

    public HttpAPI httpAPI(){
        if (isInvalid() || kbcClient == null || kbcClient.getCore() == null) {
            plugin.getLogger().warning("[KookBot] Attempted to get HttpAPI when KBCClient or its core is not available or bot is invalid.");
            return null;
        }
        return kbcClient.getCore().getHttpAPI();
    }

    /**
     * 是否不可用
     * @return
     */
    public boolean isInvalid() {
        return invalid;
    }

    public void registerKookListener(Listener listener){
        if (isInvalid()) {
            plugin.getLogger().warning("[KookBot] 无法注册 KOOK 监听器: KookBot 实例无效。");
            return;
        }
        plugin.getLogger().info("[DEBUG] 正在注册 KOOK 事件监听器: " + listener.getClass().getSimpleName());
        kbcClient.getCore().getEventManager().registerHandlers(kbcClient.getInternalPlugin(),listener);
    }

    public void unRegisterKookListener(){
        if (isInvalid() || kbcClient == null || kbcClient.getCore() == null) {
            // Log a warning or simply return if there's nothing to unregister
            // plugin.getLogger().fine("[KookBot] KookBot is invalid or KBCClient/Core is null, cannot unregister listeners.");
            return;
        }
        try {
            kbcClient.getCore().getEventManager().unregisterAllHandlers(kbcClient.getInternalPlugin());
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[KookBot] 注销 Kook 监听器时发生错误: ", e);
        }
    }

    public Map<String, Channel> getChannelMap() {
        return channelMap;
    }

    /**
     * 关闭链接
     */
    public void close(){
        if(isInvalid()) return;
        if (kbcClient != null) { // Add null check for kbcClient
            try {
                kbcClient.shutdown();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[KookBot] 关闭 KBCClient 时发生错误: ", e);
            }
        }
    }

    public KBCClient getKbcClient() {
        return kbcClient;
    }



    /**
     * 发送消息
     * @param channels 指定频道名称列表 (从配置中读取的键名)
     * @param baseComponent 消息
     */
    public void sendMessage(List<String> channels, BaseComponent baseComponent) {
        if (isInvalid()) {
            plugin.getLogger().warning("[KookBot] 无法发送消息: KookBot 实例无效。");
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (String channelName : channels) { // Iterate by channel name
                Channel channel = channelMap.get(channelName); // Get channel by name from map
                if (channel instanceof TextChannel textChannel) {
                    try {
                        textChannel.sendComponent(baseComponent);
                    } catch (Exception e) {
                        plugin.getLogger().warning("[McKook] 向频道 '" + channelName + "' (ID: " + textChannel.getId() + ") 发送消息失败: " + e.getMessage());
                        // e.printStackTrace(); // Consider logging the full stack trace to server logs if needed for debugging
                    }
                } else if (channel == null) {
                    plugin.getLogger().warning("[McKook] 尝试向未找到或未初始化的频道 '" + channelName + "' 发送消息。请检查配置。");
                } else {
                    plugin.getLogger().warning("[McKook] 频道 '" + channelName + "' (ID: " + channel.getId() + ") 不是文本频道，无法发送消息。类型: " + channel.getClass().getSimpleName());
                }
            }
        });
    }

    /**
     * 发送纯文本消息
     * @param channels 指定频道名称列表 (从配置中读取的键名)
     * @param message 消息文本
     */
    public void sendPlainText(List<String> channels, String message) {
        if (isInvalid()) {
            plugin.getLogger().warning("[KookBot] 无法发送纯文本消息: KookBot 实例无效。");
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin,()->{
            for (String channelName : channels) { // Iterate by channel name
                Channel channel = channelMap.get(channelName); // Get channel by name from map
                if(channel instanceof TextChannel textChannel){
                    try {
                        textChannel.sendComponent(message); // TextChannel can directly send string as plain text
                    } catch (Exception e) {
                        plugin.getLogger().warning("[McKook] 向频道 '" + channelName + "' (ID: " + textChannel.getId() + ") 发送纯文本消息失败: " + e.getMessage());
                    }
                } else if (channel == null) {
                    plugin.getLogger().warning("[McKook] 尝试向未找到或未初始化的频道 '" + channelName + "' 发送纯文本消息。请检查配置。");
                } else {
                    plugin.getLogger().warning("[McKook] 频道 '" + channelName + "' (ID: " + channel.getId() + ") 不是文本频道，无法发送纯文本消息。类型: " + channel.getClass().getSimpleName());
                }
            }
        });
    }
}
