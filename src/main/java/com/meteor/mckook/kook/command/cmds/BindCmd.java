package com.meteor.mckook.kook.command.cmds;

import com.meteor.mckook.McKook;
import com.meteor.mckook.kook.KookBot;
import com.meteor.mckook.kook.service.LinkService;
import snw.jkook.command.JKookCommand;
import snw.jkook.entity.Guild; // 新增导入
import snw.jkook.entity.User;
import snw.jkook.message.Message;
import snw.jkook.message.component.MarkdownComponent;
import snw.jkook.message.component.TextComponent;

import java.util.Collection; // 新增导入
import java.util.Map;       // 新增导入

public class BindCmd {

    private final McKook mcKookPlugin;
    private final KookBot kookBot;

    // 定义了配置文件中管理员角色的键名
    private static final String ADMIN_ROLE_KEY = "管理员";

    public BindCmd(McKook mcKookPlugin, KookBot kookBot) {
        this.mcKookPlugin = mcKookPlugin;
        this.kookBot = kookBot;
    }

    /**
     * 检查用户是否拥有执行绑定命令所需的管理员权限。
     * @param sender 指令发送者
     * @param message 用于回复消息的对象
     * @return 如果用户拥有权限则返回 true，否则返回 false 并发送拒绝消息。
     */
    private boolean checkAdminPermission(User sender, Message message) {
        if (kookBot == null || kookBot.isInvalid()) {
            message.reply(new TextComponent("错误：Kook机器人服务当前不可用，无法验证权限。"));
            mcKookPlugin.getLogger().warning("[KookBindCmd] KookBot is null or invalid when checking admin permission.");
            return false;
        }

        Guild primaryGuild = kookBot.getGuild(); // kookBot.getGuild() 内部已处理 KookBot 无效或 Guild ID 未配置的情况
        if (primaryGuild == null) {
            message.reply(new TextComponent("错误：无法获取主服务器信息，无法验证权限。请检查插件配置 (setting.guild)。"));
            // KookBot.getGuild() 内部会记录更详细的日志
            return false;
        }

        Map<String, Integer> configuredRolesMap = mcKookPlugin.getConfiguredRoles();
        if (configuredRolesMap == null || configuredRolesMap.isEmpty()) {
            message.reply(new TextComponent("错误：插件角色配置未加载，无法验证权限。请联系管理员检查后台日志。"));
            mcKookPlugin.getLogger().warning("[KookBindCmd] Configured roles map is null or empty when checking admin permission. This might indicate a config loading issue.");
            return false;
        }

        Integer adminRoleId = configuredRolesMap.get(ADMIN_ROLE_KEY);
        if (adminRoleId == null) {
            message.reply(new TextComponent("错误：管理员角色 (" + ADMIN_ROLE_KEY + ") 未在插件配置 (setting.roles) 中正确定义，无法执行此操作。"));
            mcKookPlugin.getLogger().warning("[KookBindCmd] Admin role ID for key '" + ADMIN_ROLE_KEY + "' not found in configuration. Please ensure it's defined in config.yml under setting.roles.");
            return false;
        }

        // --- 修正开始 ---
        // 获取用户在主服务器上的角色列表
        Collection<Integer> userRoles = sender.getRoles(primaryGuild);
        // --- 修正结束 ---

        if (userRoles != null && userRoles.contains(adminRoleId)) {
            return true;
        }

        message.reply(new MarkdownComponent("🚫 **权限不足**：您需要拥有 **" + ADMIN_ROLE_KEY + "** 身份才能执行此操作。"));
        return false;
    }

    // 辅助方法：获取 LinkService 实例
    private LinkService getLinkService(User sender, Message message) {
        if (kookBot == null || kookBot.isInvalid()) {
            message.reply(new TextComponent("错误：Kook机器人服务当前不可用。"));
            mcKookPlugin.getLogger().warning("[KookBindCmd] KookBot is null or invalid when trying to get LinkService for Kook command.");
            return null;
        }
        LinkService linkService = kookBot.getService(LinkService.class);
        if (linkService == null || linkService.linkRepository == null) {
            message.reply(new TextComponent("错误：绑定服务 (LinkService) 未初始化，无法执行操作。请联系管理员。"));
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
                    if (!checkAdminPermission(sender, message)) return; // 权限检查

                    if (args.length < 2) {
                        message.reply(new TextComponent("用法: /mckook bind add <玩家名> <Kook用户ID>"));
                        return;
                    }
                    String playerName = args[0].toString();
                    String kookId = args[1].toString();

                    LinkService linkService = getLinkService(sender, message);
                    if (linkService == null) return;

                    message.reply(new TextComponent("正在尝试为玩家 `" + playerName + "` 和 Kook ID `" + kookId + "` 添加绑定..."));

                    linkService.linkRepository.bind(
                            playerName,
                            kookId,
                            successMsg -> message.reply(new MarkdownComponent("✅ **绑定成功**: " + successMsg)),
                            errorMsg -> message.reply(new MarkdownComponent("⚠️ **绑定失败**: " + errorMsg))
                    );
                });
    }

    private JKookCommand createBindGetPlayerSubCommand() {
        return new JKookCommand("getplayer")
                .setDescription("查询指定玩家名绑定的Kook ID。 (仅限管理员)")
                .executesUser((sender, args, message) -> {
                    if (!checkAdminPermission(sender, message)) return; // 权限检查

                    if (args.length < 1) {
                        message.reply(new TextComponent("用法: /mckook bind getplayer <玩家名>"));
                        return;
                    }
                    String playerName = args[0].toString();

                    LinkService linkService = getLinkService(sender, message);
                    if (linkService == null) return;

                    message.reply(new TextComponent("正在查询玩家 `" + playerName + "` 绑定的 Kook ID..."));

                    linkService.linkRepository.bindgetKookIdByPlayerName(
                            playerName,
                            kookId -> message.reply(new MarkdownComponent("玩家 `" + playerName + "` 绑定的 Kook ID 是: `" + kookId + "`")),
                            errorMsg -> message.reply(new MarkdownComponent("⚠️ **查询失败**: " + errorMsg))
                    );
                });
    }

    private JKookCommand createBindGetKookSubCommand() {
        return new JKookCommand("getkook")
                .setDescription("查询指定Kook ID绑定的玩家名。 (仅限管理员)")
                .executesUser((sender, args, message) -> {
                    if (!checkAdminPermission(sender, message)) return; // 权限检查

                    if (args.length < 1) {
                        message.reply(new TextComponent("用法: /mckook bind getkook <Kook用户ID>"));
                        return;
                    }
                    String kookId = args[0].toString();

                    LinkService linkService = getLinkService(sender, message);
                    if (linkService == null) return;

                    message.reply(new TextComponent("正在查询 Kook ID `" + kookId + "` 绑定的玩家名..."));

                    linkService.linkRepository.bindgetPlayerNameByKookId(
                            kookId,
                            playerName -> message.reply(new MarkdownComponent("Kook ID `" + kookId + "` 绑定的玩家名是: `" + playerName + "`")),
                            errorMsg -> message.reply(new MarkdownComponent("⚠️ **查询失败**: " + errorMsg))
                    );
                });
    }

    private JKookCommand createBindRemovePlayerSubCommand() {
        return new JKookCommand("removeplayer")
                .setDescription("移除一个玩家的绑定。 (仅限管理员)")
                .executesUser((sender, args, message) -> {
                    if (!checkAdminPermission(sender, message)) return; // 权限检查

                    if (args.length < 1) {
                        message.reply(new TextComponent("用法: /mckook bind removeplayer <玩家名>"));
                        return;
                    }
                    String playerName = args[0].toString();

                    LinkService linkService = getLinkService(sender, message);
                    if (linkService == null) return;

                    message.reply(new TextComponent("正在尝试移除玩家 `" + playerName + "` 的绑定..."));

                    linkService.linkRepository.bindremoveByplayerName(
                            playerName,
                            successMsg -> message.reply(new MarkdownComponent("✅ **移除成功**: " + successMsg)),
                            errorMsg -> message.reply(new MarkdownComponent("⚠️ **移除失败**: " + errorMsg))
                    );
                });
    }

    private JKookCommand createBindRemoveKookSubCommand() {
        return new JKookCommand("removekook")
                .setDescription("移除一个Kook ID的绑定。 (仅限管理员)")
                .executesUser((sender, args, message) -> {
                    if (!checkAdminPermission(sender, message)) return; // 权限检查

                    if (args.length < 1) {
                        message.reply(new TextComponent("用法: /mckook bind removekook <Kook用户ID>"));
                        return;
                    }
                    String kookId = args[0].toString();

                    LinkService linkService = getLinkService(sender, message);
                    if (linkService == null) return;

                    message.reply(new TextComponent("正在尝试移除 Kook ID `" + kookId + "` 的绑定..."));

                    linkService.linkRepository.bindremoveByKookId(
                            kookId,
                            successMsg -> message.reply(new MarkdownComponent("✅ **移除成功**: " + successMsg)),
                            errorMsg -> message.reply(new MarkdownComponent("⚠️ **移除失败**: " + errorMsg))
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
                    if (!checkAdminPermission(sender, message)) return; // 权限检查

                    // 当用户只输入 /mckook bind 时，显示帮助信息
                    message.reply(new MarkdownComponent(
                            "**McKook 绑定命令帮助 (仅限管理员):**\n\n" +
                                    "使用以下子命令来管理绑定:\n" +
                                    "- `/mckook bind add <玩家名> <Kook用户ID>` - 添加一个新的绑定。\n" +
                                    "- `/mckook bind getplayer <玩家名>` - 查询指定玩家名绑定的Kook ID。\n" +
                                    "- `/mckook bind getkook <Kook用户ID>` - 查询指定Kook ID绑定的玩家名。\n" +
                                    "- `/mckook bind removeplayer <玩家名>` - 按玩家名移除绑定。\n" +
                                    "- `/mckook bind removekook <Kook用户ID>` - 按Kook ID移除绑定。\n\n" +
                                    "**提示:** `<Kook用户ID>` 是用户在Kook平台的用户ID。您可以通过 `/mckook info` 命令查看用户自己的Kook用户ID。"
                    ));
                });
    }
}