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
            channelConfig.getKeys(false).forEach(name->channelMap.put(name,httpAPI().getChannel(channelConfig.getString(name))));
            plugin.getLogger().info("已连接kook bot");
        } catch (Exception e) {
            invalid = true;
            plugin.getLogger().info("token填写错误,当前插件不可用.检查配置正确后使用 /mkook reload 重载");
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
        this.plugin.getServer().getLogger().info("[DEBUG] 尝试获取 Guild，ID: " + this.guild);
        return httpAPI().getGuild(this.guild);
    }


    /**
     * 是否不可用
     * @return
     */
    public boolean isInvalid() {
        return invalid;
    }

    public void registerKookListener(Listener listener){
        plugin.getLogger().info("[DEBUG] 正在注册 KOOK 事件监听器: " + listener.getClass().getSimpleName());
        kbcClient.getCore().getEventManager().registerHandlers(kbcClient.getInternalPlugin(),listener);
    }

    public void unRegisterKookListener(){
        kbcClient.getCore().getEventManager().unregisterAllHandlers(kbcClient.getInternalPlugin());
    }

    public Map<String, Channel> getChannelMap() {
        return channelMap;
    }

    /**
     * 关闭链接
     */
    public void close(){
        if(isInvalid()) return;
        kbcClient.shutdown();
    }

    public KBCClient getKbcClient() {
        return kbcClient;
    }

    public HttpAPI httpAPI(){
        return getKbcClient().getCore().getHttpAPI();
    }

    /**
     * 发送消息
     * @param channels 指定频道
     * @param baseComponent 消息
     */
    public void sendMessage(List<String> channels, BaseComponent baseComponent) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            channels.forEach(channelId -> {
                Channel channel = channelMap.get(channelId);
                if (channel instanceof TextChannel textChannel) {
                    try {
                        textChannel.sendComponent(baseComponent);
                    } catch (Exception e) {
                        plugin.getLogger().warning("[McKook] 向频道 " + channelId + " 发送消息失败: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            });
        });
    }

    public void sendPlainText(List<String> channels, String message) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin,()->{
            channels.forEach(channelId->{
                Channel channel = channelMap.get(channelId);
                if(channel instanceof TextChannel textChannel){
                    textChannel.sendComponent(message);
                }
            });
        });

    }
}
