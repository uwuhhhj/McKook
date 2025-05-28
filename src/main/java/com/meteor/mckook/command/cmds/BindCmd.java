package com.meteor.mckook.command.cmds;
import com.meteor.mckook.kook.service.LinkService;

import com.meteor.mckook.McKook;
import com.meteor.mckook.command.SubCmd;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class BindCmd extends SubCmd {
    private LinkService linkService;
    private final String NO_PERMISSION_MESSAGE;
    // 建议的权限节点前缀
    private static final String PERM_BASE = "mckook.command.bind.";

    public BindCmd(McKook plugin) {
        super(plugin);
        // 从主命令获取默认的无权限提示，或使用自定义的
        String mainPermMsg = plugin.getCommand("mckook").getPermissionMessage();
        this.NO_PERMISSION_MESSAGE = (mainPermMsg != null && !mainPermMsg.isEmpty()) ?
                mainPermMsg : ChatColor.RED + "你没有权限执行此命令。";
    }

    @Override
    public String label() {
        return "bind";
    }

    // 这个权限通常由 CommandManager 在分发前检查，或者用于 /help 等命令
    // SubCmd 的 hasPerm() 方法用于更细致的检查（例如基础的 /mckook bind 权限）
    // 而 perform() / execute() 内部的 hasSubPerm() 用于检查具体 action 的权限
    public String getPermission() {
        return "mckook.command.use.bind"; // 例如，允许使用 /mckook bind 命令的权限
    }

    @Override
    public boolean playersOnly() {
        return false;
    }

    @Override
    public String usage() {
        // 这个通用用法可能在 HelpCmd 中更有用，或者作为基础提示
        return ChatColor.translateAlternateColorCodes('&', "&6/mckook bind <add|getplayer|getkook|removeplayer|removekook> [参数...]");
    }

    @Override
    public String[] aliases() {
        return new String[0]; // 返回空数组比 null 更安全
    }

    /**
     * 检查执行此 'bind' 子命令的基础权限。
     * 如果返回 false，execute/perform 通常不会被调用。
     */
    @Override
    public boolean hasPerm(CommandSender sender) {
        return sender.hasPermission("mckook.command.bind"); // 基础权限，例如 mckook.command.bind
    }

    /**
     * 辅助方法，检查特定操作的权限。
     * @param sender 命令发送者
     * @param action 操作名 (例如 "add", "getplayer")
     * @return 如果有权限则返回 true
     */
    private boolean hasSubPerm(CommandSender sender, String action) {
        return sender.hasPermission(PERM_BASE + action.toLowerCase());
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        // 将实际的命令逻辑委托给 perform 方法
        // SubCmd 框架可能会在此 execute 之前调用 hasPerm()
        this.perform(sender, args);
    }

    @Override
    public void perform(CommandSender sender, String[] args) {
        // args[0] 通常是子命令的标签，例如 "bind"
        // 因此，实际的 <action> 参数从 args[1] 开始

        if (args.length < 2) { // 至少需要 /mckook bind <action>
            sendFullUsage(sender);
            return;
        }

        String action = args[1].toLowerCase();

        // 检查特定 action 的权限
        if (!hasSubPerm(sender, action)) {
            sender.sendMessage(NO_PERMISSION_MESSAGE);
            return;
        }

        switch (action) {
            case "add":
                // /mckook bind add <玩家名> <kook_id>
                // args[0]=bind, args[1]=add, args[2]=<玩家名>, args[3]=<kook_id>
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "用法: /mckook bind add <玩家名> <kook_id>");
                    return;
                }
                String playerNameToAdd = args[2];
                String kookIdToAdd = args[3];
                handleAddBinding(sender, playerNameToAdd, kookIdToAdd);
                break;
            case "getplayer":
                // /mckook bind getplayer <玩家名>
                // args[0]=bind, args[1]=getplayer, args[2]=<玩家名>
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "用法: /mckook bind getplayer <玩家名>");
                    return;
                }
                String playerNameToGet = args[2];
                handleGetKookIdByPlayer(sender, playerNameToGet);
                break;
            case "getkook":
                // /mckook bind getkook <kook_id>
                // args[0]=bind, args[1]=getkook, args[2]=<kook_id>
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "用法: /mckook bind getkook <kook_id>");
                    return;
                }
                String kookIdToGet = args[2];
                handleGetPlayerByKookId(sender, kookIdToGet);
                break;
            case "removeplayer":
                // /mckook bind removeplayer <玩家名>
                // args[0]=bind, args[1]=removeplayer, args[2]=<玩家名>
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "用法: /mckook bind removeplayer <玩家名>");
                    return;
                }
                String playerNameToRemove = args[2];
                handleRemoveBindingByPlayer(sender, playerNameToRemove);
                break;
            case "removekook":
                // /mckook bind removekook <kook_id>
                // args[0]=bind, args[1]=removekook, args[2]=<kook_id>
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "用法: /mckook bind removekook <kook_id>");
                    return;
                }
                String kookIdToRemove = args[2];
                handleRemoveBindingByKookId(sender, kookIdToRemove);
                break;
            default:
                sender.sendMessage(ChatColor.RED + "未知操作: " + args[1]);
                sendFullUsage(sender);
                break;
        }
    }

    private void sendFullUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "Bind 命令用法:");
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6/mckook bind add <玩家名> <kook_id>:&r 为玩家和kook_id添加绑定"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6/mckook bind getplayer <玩家名>:&r 获取指定玩家名绑定的kook_id"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6/mckook bind getkook <kook_id>:&r 获取指定kook_id绑定的玩家名"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6/mckook bind removeplayer <玩家名>:&r 删除一个玩家的绑定"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6/mckook bind removekook <kook_id>:&r 删除一个kook_id的绑定"));
    }

    // --- 处理具体操作的方法 ---
    // 你需要在这里实现实际的逻辑，例如与数据库或配置文件交互
    // 延迟初始化或在使用时获取 LinkService
    private LinkService getLinkService() {
        // 每次都尝试从 plugin.getKookBot() 获取，以应对 KookBot 重载的情况
        if (plugin.getKookBot() != null) {
            try {
                // 如果 linkService 已经获取过且 KookBot 实例未变，可以考虑直接返回
                // 但为了简单和应对 KookBot 实例变化，每次重新获取更安全
                this.linkService = plugin.getKookBot().getService(LinkService.class);
                return this.linkService;
            } catch (Exception e) {
                plugin.getLogger().warning("获取 LinkService 失败: " + e.getMessage());
                this.linkService = null; // 获取失败则置为 null
                return null;
            }
        }
        this.linkService = null; // KookBot 为 null，则 linkService 也为 null
        return null;
    }
    private void handleAddBinding(CommandSender sender, String playerName, String kookId) {
        LinkService currentLinkService = getLinkService();
        if (currentLinkService == null) {
            sender.sendMessage(ChatColor.RED + "LinkService 未初始化，无法执行操作。");
            return;
        }

        sender.sendMessage(ChatColor.AQUA + "正在尝试为玩家 " + playerName + " 和 Kook ID " + kookId + " 添加绑定...");

        currentLinkService.linkRepository.bind(
                playerName,
                kookId,
                successMessage -> { // onSuccess Callback
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(ChatColor.GREEN + successMessage);
                    });
                },
                errorMessage -> { // onFailure Callback
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(ChatColor.YELLOW + errorMessage);
                    });
                }
        );
    }

    private void handleGetKookIdByPlayer(CommandSender sender, String playerName) {
        LinkService currentLinkService = getLinkService();
        if (currentLinkService == null) {
            sender.sendMessage(ChatColor.RED + "LinkService 未初始化，无法执行操作。");
            return;
        }
        // 提示用户操作已开始
        sender.sendMessage(ChatColor.AQUA + "正在异步查询玩家 " + playerName + " 的 Kook ID...");

        currentLinkService.linkRepository.bindgetKookIdByPlayerName(
                playerName,
                kookId -> { // onSuccess 回调
                    // 确保消息在主线程发送给玩家
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(ChatColor.GREEN + "玩家 " + ChatColor.WHITE + playerName +
                                ChatColor.GREEN + " 绑定的 Kook ID 是: " + ChatColor.WHITE + kookId);
                    });
                },
                errorMessage -> { // onFailure 回调
                    // 确保消息在主线程发送给玩家
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(ChatColor.YELLOW + errorMessage);
                    });
                }
        );
    }

    private void handleGetPlayerByKookId(CommandSender sender, String kookId) {
        LinkService currentLinkService = getLinkService();
        if (currentLinkService == null) {
            sender.sendMessage(ChatColor.RED + "LinkService 未初始化，无法执行操作。");
            return;
        }

        // 提示用户操作已开始
        sender.sendMessage(ChatColor.AQUA + "正在异步查询 Kook ID " + kookId + " 绑定的玩家名...");

        currentLinkService.linkRepository.bindgetPlayerNameByKookId(
                kookId,
                playerName -> { // onSuccess 回调
                    // 确保消息在主线程发送给玩家
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(ChatColor.GREEN + "Kook ID " + ChatColor.WHITE + kookId +
                                ChatColor.GREEN + " 绑定的玩家名是: " + ChatColor.WHITE + playerName);
                    });
                },
                errorMessage -> { // onFailure 回调
                    // 确保消息在主线程发送给玩家
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(ChatColor.YELLOW + errorMessage);
                    });
                }
        );
    }

    private void handleRemoveBindingByPlayer(CommandSender sender, String playerName) {
        LinkService currentLinkService = getLinkService();
        if (currentLinkService == null) {
            sender.sendMessage(ChatColor.RED + "LinkService 未初始化，无法执行操作。");
            return;
        }

        sender.sendMessage(ChatColor.AQUA + "正在尝试移除玩家 " + playerName + " 的绑定...");

        currentLinkService.linkRepository.bindremoveByplayerName(
                playerName,
                successMessage -> { // onSuccess Callback
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(ChatColor.GREEN + successMessage);
                    });
                },
                errorMessage -> { // onFailure Callback
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(ChatColor.YELLOW + errorMessage);
                    });
                }
        );
    }

    private void handleRemoveBindingByKookId(CommandSender sender, String kookId) {
        LinkService currentLinkService = getLinkService();
        if (currentLinkService == null) {
            sender.sendMessage(ChatColor.RED + "LinkService 未初始化，无法执行操作。");
            return;
        }

        sender.sendMessage(ChatColor.AQUA + "正在尝试移除 Kook ID " + kookId + " 的绑定...");

        currentLinkService.linkRepository.bindremoveByKookId(
                kookId,
                successMessage -> { // onSuccess Callback
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(ChatColor.GREEN + successMessage);
                    });
                },
                errorMessage -> { // onFailure Callback
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(ChatColor.YELLOW + errorMessage);
                    });
                }
        );
    }

    @Override
    public List<String> getTab(CommandSender sender, String[] args) {
        // args[0] 是 "bind" (子命令标签)
        // args[1] 是 action (或其一部分)
        // args[2] 是 action 的第一个参数 (或其一部分)
        // args[3] 是 action 的第二个参数 (或其一部分) (例如 add 命令的 kook_id)

        String currentArg = args[args.length - 1].toLowerCase();

        // 补全 action: /mckook bind <action_here>
        // 此时 args.length == 2 (args = ["bind", "<partial_action>"])
        if (args.length == 2) {
            List<String> actions = Arrays.asList("add", "getplayer", "getkook", "removeplayer", "removekook");
            return actions.stream()
                    .filter(action -> hasSubPerm(sender, action)) // 仅显示有权限的 action
                    .filter(action -> action.toLowerCase().startsWith(currentArg))
                    .collect(Collectors.toList());
        }

        // 补全 action 的参数
        if (args.length >= 3) {
            String action = args[1].toLowerCase();
            if (!hasSubPerm(sender, action)) {
                return Collections.emptyList(); // 对此 action 无权限，不提供参数补全
            }

            // 补全 action 的第一个参数: /mckook bind <action> <param1_here>
            // 此时 args.length == 3 (args = ["bind", "action_name", "<partial_param1>"])
            if (args.length == 3) {
                List<String> suggestions = new ArrayList<>();
                switch (action) {
                    case "add":
                    case "getplayer":
                    case "removeplayer":
                        // 为这些操作的第一个参数（玩家名）提供在线玩家列表作为建议
                        Bukkit.getOnlinePlayers().stream()
                                .map(Player::getName)
                                .filter(name -> name.toLowerCase().startsWith(currentArg))
                                .forEach(suggestions::add);
                        // 你也可以考虑添加已绑定过的玩家名（如果可以获取列表并进行过滤）
                        break;
                    case "getkook":
                    case "removekook":
                        // 为这些操作的第一个参数（Kook ID）提供建议
                        // 如果你有一个已知/已绑定的 Kook ID 列表，可以在这里添加并过滤
                        // 例如: plugin.getBindingManager().getAllBoundKookIds().stream()
                        //          .filter(id -> id.toLowerCase().startsWith(currentArg))
                        //          .forEach(suggestions::add);
                        break;
                }
                return suggestions;
            }

            // 补全 action 的第二个参数: /mckook bind add <player_name> <kook_id_here>
            // 此时 args.length == 4 (args = ["bind", "add", "player_name", "<partial_kook_id>"])
            if (args.length == 4) {
                if ("add".equals(action)) {
                    // Kook ID 通常是数字，可能没有特别好的通用补全列表，除非你有特定格式或已知 ID
                    // 如果 kook_id 有特定格式或前缀，可以在这里添加补全逻辑
                    // 例如，如果 kook_id 都是 "kook:<数字>" 格式:
                    // if ("kook:".startsWith(currentArg) || currentArg.startsWith("kook:")) {
                    //    suggestions.add("kook:");
                    // }
                }
                // 其他需要第二个参数的 action 可以在此添加逻辑
                // return suggestions; // 如果有建议的话
            }
        }
        return Collections.emptyList(); // 默认无补全
    }
}