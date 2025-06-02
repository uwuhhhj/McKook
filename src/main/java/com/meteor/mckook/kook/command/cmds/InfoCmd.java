package com.meteor.mckook.kook.command.cmds;

import com.meteor.mckook.McKook;
import com.meteor.mckook.kook.KookBot;
import snw.jkook.command.JKookCommand;
import snw.jkook.entity.Guild;
import snw.jkook.entity.User;
import snw.jkook.message.component.MarkdownComponent;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class InfoCmd extends BaseCommand {

    // 定义配置文件中可能的角色键名，用于优先级判断或特定处理
    // 你可以根据需要调整或增加这些常量
    private static final String ADMIN_ROLE_KEY = "管理员";
    private static final String PLAYER_ROLE_KEY = "玩家";
    // 如果有其他特殊角色，也可以在这里定义

    public InfoCmd(McKook mcKookPlugin, KookBot kookBot) {
        super(mcKookPlugin, kookBot);
    }

    @Override
    public String getCommandName() {
        return "info";
    }

    @Override
    public String getDescription() {
        return "获取您在插件主服务器的 Kook 用户信息。";
    }

    /**
     * 获取用户在指定服务器中的主要角色名称
     */
    private Optional<String> getUserPrimaryConfiguredRoleName(User user, Guild guild, Map<String, Integer> configuredRolesMap) {
        if (user == null || guild == null || configuredRolesMap == null || configuredRolesMap.isEmpty()) {
            return Optional.empty();
        }

        Collection<Integer> userRoleIdsOnServer = user.getRoles(guild);
        if (userRoleIdsOnServer == null || userRoleIdsOnServer.isEmpty()) {
            return Optional.empty();
        }

        // 优先检查 "管理员" 角色
        Integer adminRoleIdFromConfig = configuredRolesMap.get(ADMIN_ROLE_KEY);
        if (adminRoleIdFromConfig != null && userRoleIdsOnServer.contains(adminRoleIdFromConfig)) {
            return Optional.of(ADMIN_ROLE_KEY);
        }

        // 其次检查 "玩家" 角色
        Integer playerRoleIdFromConfig = configuredRolesMap.get(PLAYER_ROLE_KEY);
        if (playerRoleIdFromConfig != null && userRoleIdsOnServer.contains(playerRoleIdFromConfig)) {
            return Optional.of(PLAYER_ROLE_KEY);
        }

        // 检查其他角色
        for (Map.Entry<String, Integer> entry : configuredRolesMap.entrySet()) {
            if (entry.getKey().equals(ADMIN_ROLE_KEY) || entry.getKey().equals(PLAYER_ROLE_KEY)) {
                continue;
            }
            if (userRoleIdsOnServer.contains(entry.getValue())) {
                return Optional.of(entry.getKey());
            }
        }

        return Optional.empty();
    }

    @Override
    public JKookCommand buildCommand() {
        return new JKookCommand(getCommandName())
                .setDescription(getDescription())
                .executesUser((sender, arguments, message) -> {
                    if (message == null) {
                        logWarning("Message object was null for /mckook info command.");
                        return;
                    }

                    if (!checkBotAvailable(message)) {
                        return;
                    }

                    Guild primaryGuild = kookBot.getGuild();
                    if (primaryGuild == null) {
                        logWarning("无法获取主服务器信息");
                        sendErrorMessage(message, "guild_error");
                        return;
                    }

                    StringBuilder replyContent = new StringBuilder();
                    String globalName = sender.getName();
                    String userId = sender.getId();
                    int identifyNum = sender.getIdentifyNumber();

                    Map<String, Integer> configuredRolesMap = mcKookPlugin.getConfiguredRoles();
                    Optional<String> userPrimaryRoleNameOpt = Optional.empty();
                    String greetingPrefix = "你好，";

                    if (primaryGuild != null && configuredRolesMap != null && !configuredRolesMap.isEmpty()) {
                        userPrimaryRoleNameOpt = getUserPrimaryConfiguredRoleName(sender, primaryGuild, configuredRolesMap);

                        if (userPrimaryRoleNameOpt.isPresent()) {
                            String roleName = userPrimaryRoleNameOpt.get();
                            if (ADMIN_ROLE_KEY.equals(roleName)) {
                                greetingPrefix = "你好尊贵的" + roleName + " ";
                            } else {
                                greetingPrefix = "你好" + roleName + " ";
                            }
                        }
                    }

                    replyContent.append(greetingPrefix).append(globalName).append("！\n");
                    replyContent.append("以下是你的 Kook 用户信息：\n");
                    replyContent.append("--------------------------\n");
                    replyContent.append("Kook 全局昵称: `").append(globalName).append("`\n");
                    replyContent.append("Kook 用户 ID: `").append(userId).append("`\n");
                    replyContent.append("识别号: `#").append(String.format("%04d", identifyNum)).append("`\n");

                    if (primaryGuild != null) {
                        replyContent.append("在主服务器 (`").append(primaryGuild.getName()).append("` - `")
                                .append(primaryGuild.getId()).append("`) 中的信息:\n");

                        if (userPrimaryRoleNameOpt.isPresent()) {
                            replyContent.append("  你的主要身份: `").append(userPrimaryRoleNameOpt.get()).append("`\n");
                        } else if (configuredRolesMap != null && !configuredRolesMap.isEmpty()) {
                            replyContent.append("  你的主要身份: `未识别 (无匹配的配置角色)`\n");
                        }

                        String serverNickname = sender.getNickName(primaryGuild);
                        if (serverNickname != null && !serverNickname.isEmpty() && !serverNickname.equals(globalName)) {
                            replyContent.append("  服务器昵称: `").append(serverNickname).append("`\n");
                        }
                        replyContent.append("  完整名称: `").append(sender.getFullName(primaryGuild)).append("`\n");

                        Collection<Integer> roleIds = sender.getRoles(primaryGuild);
                        if (roleIds != null && !roleIds.isEmpty()) {
                            String rolesString = roleIds.stream()
                                    .map(String::valueOf)
                                    .collect(Collectors.joining(", "));
                            replyContent.append("  服务器角色 ID 列表: `").append(rolesString).append("`\n");
                        } else {
                            replyContent.append("  服务器角色 ID 列表: `无`\n");
                        }
                    } else {
                        replyContent.append("未能获取到插件配置的主服务器信息，部分信息可能无法显示。\n");
                        replyContent.append("全局完整名称: `").append(sender.getFullName(null)).append("`\n");
                    }

                    replyContent.append("--------------------------\n");
                    replyContent.append("是否为 VIP: ").append(sender.isVip() ? "是" : "否").append("\n");
                    replyContent.append("是否为机器人: ").append(sender.isBot() ? "是" : "否").append("\n");
                    replyContent.append("是否在线: ").append(sender.isOnline() ? "是" : "否").append("\n");
                    replyContent.append("--------------------------");

                    message.reply(new MarkdownComponent(replyContent.toString()));
                });
    }
}