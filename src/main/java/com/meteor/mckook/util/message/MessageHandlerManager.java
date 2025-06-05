package com.meteor.mckook.util.message;

import com.meteor.mckook.McKook;
import com.meteor.mckook.message.AbstractKookMessage;
import com.meteor.mckook.message.sub.PlayerChatMessage;
import com.meteor.mckook.message.sub.PlayerJoinMessage;
import com.meteor.mckook.message.sub.PlayerLinkMessage;
import com.meteor.mckook.message.sub.WhitelistMessage;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

public class MessageHandlerManager extends AbstractMessageManager {

    private final Map<String, SubMessageManager> subManagers = new HashMap<>();
    private final List<AbstractKookMessage> handlers = new ArrayList<>();

    private static final String PLAYER_JOIN_MESSAGE_FILE = "PlayerJoinKookMessage";
    private static final String PLAYER_CHAT_MESSAGE_FILE = "PlayerChatMessage";
    private static final String PLAYER_LINK_MESSAGE_FILE = "PlayerLinkKookMessage";
    private static final String PLAYER_WHITELIST_MESSAGE_FILE = "WhitelistMessage";
    private static final List<String> MESSAGE_FILE_NAMES = Arrays.asList(
            PLAYER_JOIN_MESSAGE_FILE,
            PLAYER_CHAT_MESSAGE_FILE,
            PLAYER_LINK_MESSAGE_FILE,
            PLAYER_WHITELIST_MESSAGE_FILE
    );

    public MessageHandlerManager(McKook plugin) {
        super(plugin);
    }

    public void loadMessageConfigurations() {
        getPlugin().getLogger().info("正在加载消息配置文件...");
        subManagers.clear();
        for (String messageName : MESSAGE_FILE_NAMES) {
            String filePath = "message/" + messageName + ".yml";
            File file = new File(getPlugin().getDataFolder(), filePath);
            if (!file.exists()) {
                getPlugin().saveResource(filePath, false);
                getPlugin().getLogger().info("已保存默认消息配置文件: " + filePath);
            }
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            subManagers.put(messageName, new SubMessageManager(config));
            getPlugin().getLogger().info("已加载消息配置文件: " + filePath);
        }
        getPlugin().getLogger().info("所有消息配置文件加载完毕。");
    }

    public void setupMessageHandlers() {
        handlers.forEach(AbstractKookMessage::unRegister);
        handlers.clear();

        if (getPlugin().getKookBot() == null || getPlugin().getKookBot().isInvalid()) {
            getPlugin().getLogger().warning("KookBot 未就绪，无法设置消息处理器。");
            return;
        }

        try {
            YamlConfiguration joinConfig = getConfig(PLAYER_JOIN_MESSAGE_FILE);
            if (joinConfig != null) {
                PlayerJoinMessage playerJoinMessage = new PlayerJoinMessage(getPlugin(), joinConfig);
                handlers.add(playerJoinMessage);
                playerJoinMessage.register();
            } else {
                getPlugin().getLogger().warning("未能找到 " + PLAYER_JOIN_MESSAGE_FILE + " 的已加载配置，该消息功能可能无法正常工作。");
            }

            YamlConfiguration chatConfig = getConfig(PLAYER_CHAT_MESSAGE_FILE);
            if (chatConfig != null) {
                PlayerChatMessage playerChatMessage = new PlayerChatMessage(getPlugin(), chatConfig);
                handlers.add(playerChatMessage);
                playerChatMessage.register();
            } else {
                getPlugin().getLogger().warning("未能找到 " + PLAYER_CHAT_MESSAGE_FILE + " 的已加载配置，该消息功能可能无法正常工作。");
            }

            YamlConfiguration linkConfig = getConfig(PLAYER_LINK_MESSAGE_FILE);
            if (linkConfig != null) {
                PlayerLinkMessage playerLinkMessage = new PlayerLinkMessage(getPlugin(), linkConfig);
                handlers.add(playerLinkMessage);
                playerLinkMessage.register();
            } else {
                getPlugin().getLogger().warning("未能找到 " + PLAYER_LINK_MESSAGE_FILE + " 的已加载配置，该消息功能可能无法正常工作。");
            }

            YamlConfiguration whitelistConfig = getConfig(PLAYER_WHITELIST_MESSAGE_FILE);
            if (whitelistConfig != null) {
                WhitelistMessage whitelistMessage = new WhitelistMessage(getPlugin(), whitelistConfig);
                handlers.add(whitelistMessage);
                whitelistMessage.register();
            } else {
                getPlugin().getLogger().warning("未能找到 " + PLAYER_WHITELIST_MESSAGE_FILE + " 的已加载配置，该消息功能可能无法正常工作。");
            }
        } catch (Exception e) {
            getPlugin().getLogger().log(Level.SEVERE, "创建和注册消息处理器时发生错误:", e);
        }
    }

    private YamlConfiguration getConfig(String key) {
        SubMessageManager manager = subManagers.get(key);
        return manager == null ? null : manager.getConfig();
    }

    public void unloadHandlers() {
        handlers.forEach(AbstractKookMessage::unRegister);
        handlers.clear();
    }
}
