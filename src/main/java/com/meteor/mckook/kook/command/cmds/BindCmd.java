package com.meteor.mckook.kook.command.cmds;

import com.meteor.mckook.McKook;
import com.meteor.mckook.kook.KookBot;
import com.meteor.mckook.kook.service.LinkService;
import com.meteor.mckook.util.BaseConfig;
import snw.jkook.command.JKookCommand;
import snw.jkook.entity.Guild;
import snw.jkook.entity.User;
import snw.jkook.message.Message;
import snw.jkook.message.component.MarkdownComponent;
import snw.jkook.message.component.TextComponent;

import java.util.Collection;
import java.util.Map;

public class BindCmd {

    private final McKook mcKookPlugin;
    private final KookBot kookBot;

    // 定义了配置文件中管理员角色的键名
    private static final String ADMIN_ROLE_KEY = "管理员";

    public BindCmd(McKook mcKookPlugin, KookBot kookBot) {
        this.mcKookPlugin = mcKookPlugin;
        this.kookBot = kookBot;
    }

    private String getMessageFromConfig(String key) {
        return BaseConfig.instance.getMessageBox().getMessage(null, "message.kook_message.bind." + key);
    }

    private String getMessageWithPlaceholders(String key, Map<String, String> placeholders) {
        String message = getMessageFromConfig(key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return message;
    }

    /**
     * 检查用户是否拥有执行绑定命令所需的管理员权限。
     * @param sender 指令发送者
     * @param message 用于回复消息的对象
     * @return 如果用户拥有权限则返回 true，否则返回 false 并发送拒绝消息。
     */
    private boolean checkAdminPermission(User sender, Message message) {
        if (kookBot == null || kookBot.isInvalid()) {
            message.reply(new TextComponent(getMessageFromConfig("bot_unavailable")));
            mcKookPlugin.getLogger().warning("[KookBindCmd] KookBot is null or invalid when checking admin permission.");
            return false;
        }

        Guild primaryGuild = kookBot.getGuild();
        if (primaryGuild == null) {
            message.reply(new TextComponent(getMessageFromConfig("guild_error")));
            return false;
        }

        Map<String, Integer> configuredRolesMap = mcKookPlugin.getConfiguredRoles();
        if (configuredRolesMap == null || configuredRolesMap.isEmpty()) {
            message.reply(new TextComponent(getMessageFromConfig("roles_config_error")));
            mcKookPlugin.getLogger().warning("[KookBindCmd] Configured roles map is null or empty when checking admin permission. This might indicate a config loading issue.");
            return false;
        }

        Integer adminRoleId = configuredRolesMap.get(ADMIN_ROLE_KEY);
        if (adminRoleId == null) {
            message.reply(new TextComponent(getMessageFromConfig("admin_role_undefined")));
            mcKookPlugin.getLogger().warning("[KookBindCmd] Admin role ID for key '" + ADMIN_ROLE_KEY + "' not found in configuration. Please ensure it's defined in config.yml under setting.roles.");
            return false;
        }

        Collection<Integer> userRoles = sender.getRoles(primaryGuild);

        if (userRoles != null && userRoles.contains(adminRoleId)) {
            return true;
        }

        message.reply(new MarkdownComponent(getMessageFromConfig("no_permission")));
        return false;
    }

    // 辅助方法：获取 LinkService 实例
    private LinkService getLinkService(User sender, Message message) {
        if (kookBot == null || kookBot.isInvalid()) {
            message.reply(new TextComponent(getMessageFromConfig("bot_unavailable")));
            mcKookPlugin.getLogger().warning("[KookBindCmd] KookBot is null or invalid when trying to get LinkService for Kook command.");
            return null;
        }
        LinkService linkService = kookBot.getService(LinkService.class);
        if (linkService == null || linkService.linkRepository == null) {
            message.reply(new TextComponent(getMessageFromConfig("service_unavailable")));
            mcKookPlugin.getLogger().warning("[KookBindCmd] LinkService or its LinkRepository is null when requested by Kook command.");
            return null;
        }
        return linkService;
    }

    // --- 创建 "bind" 子命令的各个操作 ---

    private JKookCommand createBindAddSubCommand() {
        return new JKookCommand("add")
                .setDescription("添加一个新的玩家-Kook绑定。 (仅限管理员)")
                .executesUser((sender, args, message) -> {
                    if (!checkAdminPermission(sender, message)) return;

                    if (args.length < 2) {
                        message.reply(new TextComponent(getMessageWithPlaceholders("add.usage", Map.of())));
                        return;
                    }
                    String playerName = args[0].toString();
                    String kookId = args[1].toString();

                    LinkService linkService = getLinkService(sender, message);
                    if (linkService == null) return;

                    Map<String, String> placeholders = Map.of(
                        "player", playerName,
                        "kookId", kookId
                    );
                    message.reply(new TextComponent(getMessageWithPlaceholders("add.processing", placeholders)));

                    linkService.linkRepository.bind(
                            playerName,
                            kookId,
                            successMsg -> message.reply(new MarkdownComponent(getMessageWithPlaceholders("operation.success", Map.of("operation", "绑定", "message", successMsg)))),
                            errorMsg -> message.reply(new MarkdownComponent(getMessageWithPlaceholders("operation.error", Map.of("operation", "绑定", "message", errorMsg))))
                    );
                });
    }

    private JKookCommand createBindGetPlayerSubCommand() {
        return new JKookCommand("getplayer")
                .setDescription("查询指定玩家名绑定的Kook ID。 (仅限管理员)")
                .executesUser((sender, args, message) -> {
                    if (!checkAdminPermission(sender, message)) return;

                    if (args.length < 1) {
                        message.reply(new TextComponent(getMessageWithPlaceholders("getplayer.usage", Map.of())));
                        return;
                    }
                    String playerName = args[0].toString();

                    LinkService linkService = getLinkService(sender, message);
                    if (linkService == null) return;

                    message.reply(new TextComponent(getMessageWithPlaceholders("getplayer.processing", Map.of("player", playerName))));

                    linkService.linkRepository.bindgetKookIdByPlayerName(
                            playerName,
                            kookId -> message.reply(new MarkdownComponent(getMessageWithPlaceholders("getplayer.result", Map.of("player", playerName, "kookId", kookId)))),
                            errorMsg -> message.reply(new MarkdownComponent(getMessageWithPlaceholders("operation.error", Map.of("operation", "查询", "message", errorMsg))))
                    );
                });
    }

    private JKookCommand createBindGetKookSubCommand() {
        return new JKookCommand("getkook")
                .setDescription("查询指定Kook ID绑定的玩家名。 (仅限管理员)")
                .executesUser((sender, args, message) -> {
                    if (!checkAdminPermission(sender, message)) return;

                    if (args.length < 1) {
                        message.reply(new TextComponent(getMessageWithPlaceholders("getkook.usage", Map.of())));
                        return;
                    }
                    String kookId = args[0].toString();

                    LinkService linkService = getLinkService(sender, message);
                    if (linkService == null) return;

                    message.reply(new TextComponent(getMessageWithPlaceholders("getkook.processing", Map.of("kookId", kookId))));

                    linkService.linkRepository.bindgetPlayerNameByKookId(
                            kookId,
                            playerName -> message.reply(new MarkdownComponent(getMessageWithPlaceholders("getkook.result", Map.of("kookId", kookId, "player", playerName)))),
                            errorMsg -> message.reply(new MarkdownComponent(getMessageWithPlaceholders("operation.error", Map.of("operation", "查询", "message", errorMsg))))
                    );
                });
    }

    private JKookCommand createBindRemovePlayerSubCommand() {
        return new JKookCommand("removeplayer")
                .setDescription("移除一个玩家的绑定。 (仅限管理员)")
                .executesUser((sender, args, message) -> {
                    if (!checkAdminPermission(sender, message)) return;

                    if (args.length < 1) {
                        message.reply(new TextComponent(getMessageWithPlaceholders("removeplayer.usage", Map.of())));
                        return;
                    }
                    String playerName = args[0].toString();

                    LinkService linkService = getLinkService(sender, message);
                    if (linkService == null) return;

                    message.reply(new TextComponent(getMessageWithPlaceholders("removeplayer.processing", Map.of("player", playerName))));

                    linkService.linkRepository.bindremoveByplayerName(
                            playerName,
                            successMsg -> message.reply(new MarkdownComponent(getMessageWithPlaceholders("operation.success", Map.of("operation", "移除", "message", successMsg)))),
                            errorMsg -> message.reply(new MarkdownComponent(getMessageWithPlaceholders("operation.error", Map.of("operation", "移除", "message", errorMsg))))
                    );
                });
    }

    private JKookCommand createBindRemoveKookSubCommand() {
        return new JKookCommand("removekook")
                .setDescription("移除一个Kook ID的绑定。 (仅限管理员)")
                .executesUser((sender, args, message) -> {
                    if (!checkAdminPermission(sender, message)) return;

                    if (args.length < 1) {
                        message.reply(new TextComponent(getMessageWithPlaceholders("removekook.usage", Map.of())));
                        return;
                    }
                    String kookId = args[0].toString();

                    LinkService linkService = getLinkService(sender, message);
                    if (linkService == null) return;

                    message.reply(new TextComponent(getMessageWithPlaceholders("removekook.processing", Map.of("kookId", kookId))));

                    linkService.linkRepository.bindremoveByKookId(
                            kookId,
                            successMsg -> message.reply(new MarkdownComponent(getMessageWithPlaceholders("operation.success", Map.of("operation", "移除", "message", successMsg)))),
                            errorMsg -> message.reply(new MarkdownComponent(getMessageWithPlaceholders("operation.error", Map.of("operation", "移除", "message", errorMsg))))
                    );
                });
    }

    /**
     * 构建 "bind" 主命令及其所有子操作。
     * @return 构建好的 JKookCommand 对象，用于注册。
     */
    public JKookCommand buildCommand() {
        return new JKookCommand("bind")
                .setDescription("管理 Minecraft 账户和 Kook 账户的绑定。 (仅限管理员)")
                .addSubcommand(createBindAddSubCommand())
                .addSubcommand(createBindGetPlayerSubCommand())
                .addSubcommand(createBindGetKookSubCommand())
                .addSubcommand(createBindRemovePlayerSubCommand())
                .addSubcommand(createBindRemoveKookSubCommand())
                .executesUser((sender, args, message) -> {
                    if (!checkAdminPermission(sender, message)) return;

                    // 当用户只输入 /mckook bind 时，显示帮助信息
                    message.reply(new MarkdownComponent(getMessageFromConfig("help")));
                });
    }
}