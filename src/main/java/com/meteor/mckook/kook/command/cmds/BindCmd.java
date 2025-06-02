package com.meteor.mckook.kook.command.cmds;

import com.meteor.mckook.McKook;
import com.meteor.mckook.kook.KookBot;
import com.meteor.mckook.kook.service.LinkService;
import snw.jkook.command.JKookCommand;
import snw.jkook.entity.Guild;
import snw.jkook.entity.User;
import snw.jkook.message.Message;

import java.util.Collection;
import java.util.Map;

public class BindCmd extends BaseCommand {

    // 定义了配置文件中管理员角色的键名
    private static final String ADMIN_ROLE_KEY = "管理员";

    public BindCmd(McKook mcKookPlugin, KookBot kookBot) {
        super(mcKookPlugin, kookBot);
    }

    @Override
    public String getCommandName() {
        return "bind";
    }

    @Override
    public String getDescription() {
        return "管理 Minecraft 账户和 Kook 账户的绑定。 (仅限管理员)";
    }

    /**
     * 检查用户是否拥有执行绑定命令所需的管理员权限。
     * @param sender 指令发送者
     * @param message 用于回复消息的对象
     * @return 如果用户拥有权限则返回 true，否则返回 false 并发送拒绝消息。
     */
    @Override
    protected boolean checkAdminPermission(User sender, Message message) {
        if (!checkBotAvailable(message)) {
            return false;
        }

        Guild primaryGuild = kookBot.getGuild();
        if (primaryGuild == null) {
            sendErrorMessage(message, "guild_error");
            return false;
        }

        Map<String, Integer> configuredRolesMap = mcKookPlugin.getConfiguredRoles();
        if (configuredRolesMap == null || configuredRolesMap.isEmpty()) {
            sendErrorMessage(message, "roles_config_error");
            logWarning("Configured roles map is null or empty when checking admin permission. This might indicate a config loading issue.");
            return false;
        }

        Integer adminRoleId = configuredRolesMap.get(ADMIN_ROLE_KEY);
        if (adminRoleId == null) {
            sendErrorMessage(message, "admin_role_undefined");
            logWarning("Admin role ID for key '" + ADMIN_ROLE_KEY + "' not found in configuration. Please ensure it's defined in config.yml under setting.roles.");
            return false;
        }

        Collection<Integer> userRoles = sender.getRoles(primaryGuild);

        if (userRoles != null && userRoles.contains(adminRoleId)) {
            return true;
        }

        sendErrorMessage(message, "no_permission");
        return false;
    }

    // 辅助方法：获取 LinkService 实例
    private LinkService getLinkService(User sender, Message message) {
        if (!checkBotAvailable(message)) {
            return null;
        }
        
        LinkService linkService = kookBot.getService(LinkService.class);
        if (linkService == null || linkService.linkRepository == null) {
            sendErrorMessage(message, "service_unavailable");
            logWarning("LinkService or its LinkRepository is null when requested by Kook command.");
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

                    if (!checkArguments(args, 2, message, "add.usage")) {
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
                    sendSuccessMessage(message, "add.processing", placeholders);

                    linkService.linkRepository.bind(
                            playerName,
                            kookId,
                            successMsg -> sendSuccessMessage(message, "operation.success", Map.of("operation", "绑定", "message", successMsg)),
                            errorMsg -> sendSuccessMessage(message, "operation.error", Map.of("operation", "绑定", "message", errorMsg))
                    );
                });
    }

    private JKookCommand createBindGetPlayerSubCommand() {
        return new JKookCommand("getplayer")
                .setDescription("查询指定玩家名绑定的Kook ID。 (仅限管理员)")
                .executesUser((sender, args, message) -> {
                    if (!checkAdminPermission(sender, message)) return;

                    if (!checkArguments(args, 1, message, "getplayer.usage")) {
                        return;
                    }

                    String playerName = args[0].toString();
                    LinkService linkService = getLinkService(sender, message);
                    if (linkService == null) return;

                    sendSuccessMessage(message, "getplayer.processing", Map.of("player", playerName));

                    linkService.linkRepository.bindgetKookIdByPlayerName(
                            playerName,
                            kookId -> sendSuccessMessage(message, "getplayer.result", Map.of("player", playerName, "kookId", kookId)),
                            errorMsg -> sendSuccessMessage(message, "operation.error", Map.of("operation", "查询", "message", errorMsg))
                    );
                });
    }

    private JKookCommand createBindGetKookSubCommand() {
        return new JKookCommand("getkook")
                .setDescription("查询指定Kook ID绑定的玩家名。 (仅限管理员)")
                .executesUser((sender, args, message) -> {
                    if (!checkAdminPermission(sender, message)) return;

                    if (!checkArguments(args, 1, message, "getkook.usage")) {
                        return;
                    }

                    String kookId = args[0].toString();
                    LinkService linkService = getLinkService(sender, message);
                    if (linkService == null) return;

                    sendSuccessMessage(message, "getkook.processing", Map.of("kookId", kookId));

                    linkService.linkRepository.bindgetPlayerNameByKookId(
                            kookId,
                            playerName -> sendSuccessMessage(message, "getkook.result", Map.of("kookId", kookId, "player", playerName)),
                            errorMsg -> sendSuccessMessage(message, "operation.error", Map.of("operation", "查询", "message", errorMsg))
                    );
                });
    }

    private JKookCommand createBindRemovePlayerSubCommand() {
        return new JKookCommand("removeplayer")
                .setDescription("移除一个玩家的绑定。 (仅限管理员)")
                .executesUser((sender, args, message) -> {
                    if (!checkAdminPermission(sender, message)) return;

                    if (!checkArguments(args, 1, message, "removeplayer.usage")) {
                        return;
                    }

                    String playerName = args[0].toString();
                    LinkService linkService = getLinkService(sender, message);
                    if (linkService == null) return;

                    sendSuccessMessage(message, "removeplayer.processing", Map.of("player", playerName));

                    linkService.linkRepository.bindremoveByplayerName(
                            playerName,
                            successMsg -> sendSuccessMessage(message, "operation.success", Map.of("operation", "移除", "message", successMsg)),
                            errorMsg -> sendSuccessMessage(message, "operation.error", Map.of("operation", "移除", "message", errorMsg))
                    );
                });
    }

    private JKookCommand createBindRemoveKookSubCommand() {
        return new JKookCommand("removekook")
                .setDescription("移除一个Kook ID的绑定。 (仅限管理员)")
                .executesUser((sender, args, message) -> {
                    if (!checkAdminPermission(sender, message)) return;

                    if (!checkArguments(args, 1, message, "removekook.usage")) {
                        return;
                    }

                    String kookId = args[0].toString();
                    LinkService linkService = getLinkService(sender, message);
                    if (linkService == null) return;

                    sendSuccessMessage(message, "removekook.processing", Map.of("kookId", kookId));

                    linkService.linkRepository.bindremoveByKookId(
                            kookId,
                            successMsg -> sendSuccessMessage(message, "operation.success", Map.of("operation", "移除", "message", successMsg)),
                            errorMsg -> sendSuccessMessage(message, "operation.error", Map.of("operation", "移除", "message", errorMsg))
                    );
                });
    }

    @Override
    public JKookCommand buildCommand() {
        return new JKookCommand(getCommandName())
                .setDescription(getDescription())
                .addSubcommand(createBindAddSubCommand())
                .addSubcommand(createBindGetPlayerSubCommand())
                .addSubcommand(createBindGetKookSubCommand())
                .addSubcommand(createBindRemovePlayerSubCommand())
                .addSubcommand(createBindRemoveKookSubCommand())
                .executesUser((sender, args, message) -> {
                    if (!checkAdminPermission(sender, message)) return;
                    sendSuccessMessage(message, "help", null);
                });
    }
}