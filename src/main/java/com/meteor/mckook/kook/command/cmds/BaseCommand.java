package com.meteor.mckook.kook.command.cmds;

import com.meteor.mckook.McKook;
import com.meteor.mckook.config.Config;
import com.meteor.mckook.kook.KookBot;
import com.meteor.mckook.util.BaseConfig;
import snw.jkook.command.JKookCommand;
import snw.jkook.entity.Guild;
import snw.jkook.entity.User;
import snw.jkook.message.Message;
import snw.jkook.message.component.MarkdownComponent;
import snw.jkook.message.component.TextComponent;

import java.util.Collection;
import java.util.Map;
import java.util.HashMap;

/**
 * Kook 命令的抽象基类，提供通用的命令处理功能
 */
public abstract class BaseCommand {
    protected final McKook mcKookPlugin;
    protected final KookBot kookBot;

    // 定义常用的角色键名
    protected static final String ADMIN_ROLE_KEY = "管理员";
    protected static final String PLAYER_ROLE_KEY = "玩家";

    public BaseCommand(McKook mcKookPlugin, KookBot kookBot) {
        this.mcKookPlugin = mcKookPlugin;
        this.kookBot = kookBot;
    }

    /**
     * 构建命令的抽象方法，子类必须实现
     * @return 构建好的 JKookCommand 对象
     */
    public abstract JKookCommand buildCommand();

    /**
     * 获取命令名称
     * @return 命令名称
     */
    public abstract String getCommandName();

    /**
     * 获取命令描述
     * @return 命令描述
     */
    public abstract String getDescription();

    /**
     * 从配置文件获取消息
     * @param key 消息键
     * @return 消息内容
     */
    protected String getMessageFromConfig(String key) {
        return BaseConfig.instance.getMessageBox().getMessage(null, "message.kook_message." + getCommandName() + "." + key);
    }

    /**
     * 获取带有占位符的消息
     * @param key 消息键
     * @param placeholders 占位符映射
     * @return 处理后的消息
     */
    protected String getMessageWithPlaceholders(String key, Map<String, String> placeholders) {
        String message = getMessageFromConfig(key);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                message = message.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return message;
    }

    /**
     * 检查 KookBot 是否可用
     * @param message 用于回复的消息对象
     * @return 如果可用返回 true
     */
    protected boolean checkBotAvailable(Message message) {
        if (kookBot == null || kookBot.isInvalid()) {
            sendErrorMessage(message, "bot_unavailable");
            mcKookPlugin.getLogger().warning("[" + getCommandName() + "] KookBot is null or invalid.");
            return false;
        }
        return true;
    }

    /**
     * 检查用户是否拥有管理员权限
     * @param sender 命令发送者
     * @param message 用于回复的消息对象
     * @return 如果有权限返回 true
     */
    protected boolean checkAdminPermission(User sender, Message message) {
        if (!checkBotAvailable(message)) {
            return false;
        }

        Guild primaryGuild = kookBot.getGuild();
        if (primaryGuild == null) {
            sendErrorMessage(message, "guild_error");
            return false;
        }

        Map<String, Integer> configuredRolesMap = Config.get().getConfiguredRoles();
        if (configuredRolesMap == null || configuredRolesMap.isEmpty()) {
            sendErrorMessage(message, "roles_config_error");
            mcKookPlugin.getLogger().warning("[" + getCommandName() + "] Configured roles map is null or empty.");
            return false;
        }

        Integer adminRoleId = configuredRolesMap.get(ADMIN_ROLE_KEY);
        if (adminRoleId == null) {
            sendErrorMessage(message, "admin_role_undefined");
            mcKookPlugin.getLogger().warning("[" + getCommandName() + "] Admin role ID not found in configuration.");
            return false;
        }

        Collection<Integer> userRoles = sender.getRoles(primaryGuild);
        if (userRoles != null && userRoles.contains(adminRoleId)) {
            return true;
        }

        sendErrorMessage(message, "no_permission");
        return false;
    }

    /**
     * 发送错误消息
     * @param message 用于回复的消息对象
     * @param key 错误消息的键
     */
    protected void sendErrorMessage(Message message, String key) {
        message.reply(new TextComponent(getMessageFromConfig(key)));
    }

    /**
     * 发送成功消息
     * @param message 用于回复的消息对象
     * @param key 成功消息的键
     * @param placeholders 占位符映射
     */
    protected void sendSuccessMessage(Message message, String key, Map<String, String> placeholders) {
        message.reply(new MarkdownComponent(getMessageWithPlaceholders(key, placeholders)));
    }

    /**
     * 检查参数数量是否足够
     * @param args 参数数组
     * @param required 所需参数数量
     * @param message 用于回复的消息对象
     * @param usageKey 使用方法消息的键
     * @return 如果参数足够返回 true
     */
    protected boolean checkArguments(Object[] args, int required, Message message, String usageKey) {
        if (args.length < required) {
            sendErrorMessage(message, usageKey);
            return false;
        }
        return true;
    }

    /**
     * 记录警告日志
     * @param warning 警告消息
     */
    protected void logWarning(String warning) {
        mcKookPlugin.getLogger().warning("[" + getCommandName() + "] " + warning);
    }

    /**
     * 记录错误日志
     * @param error 错误消息
     * @param e 异常对象
     */
    protected void logError(String error, Exception e) {
        mcKookPlugin.getLogger().severe("[" + getCommandName() + "] " + error + ": " + e.getMessage());
    }
} 