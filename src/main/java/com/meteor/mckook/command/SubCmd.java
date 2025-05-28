package com.meteor.mckook.command;

import com.meteor.mckook.McKook;
import org.bukkit.ChatColor; // 引入 ChatColor 用于消息
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public abstract class SubCmd {
    protected final McKook plugin; // 设为 final

    public SubCmd(McKook plugin) {
        this.plugin = plugin;
    }

    /**
     * @return 子命令的标签 (例如 "link", "reload")
     */
    public abstract String label();

    /**
     * @return 子命令所需的权限节点，如果为 null 则表示无需权限
     */
    public abstract String getPermission();

    /**
     * @return 此命令是否仅限玩家执行
     */
    public abstract boolean playersOnly();

    /**
     * @return 命令的用法提示 (例如 "/mckook link <player> <kook_id>")
     */
    public abstract String usage(); // 可以考虑在 perform 中当参数错误时发送此消息

    /**
     * 为子命令提供 Tab 补全建议。
     *
     * @param sender 命令发送者
     * @param args   用户已输入的完整参数数组 (args[0] 是子命令标签)
     * @return Tab 补全建议列表
     */
    public List<String> getTab(CommandSender sender, String[] args) {
        // 默认实现，子类可以覆盖此方法以提供具体的补全逻辑
        // 例如，如果 args.length == 2 (正在输入子命令的第一个参数)
        // if (args.length == 2 && args[0].equalsIgnoreCase(label())) {
        //    // 提供第一个参数的补全
        // }
        return Collections.emptyList();
    }

    /**
     * 执行子命令的核心逻辑。
     * 此时已经通过了 playersOnly 和权限检查。
     *
     * @param sender 命令发送者
     * @param args   用户输入的参数 (args[0] 是子命令标签)
     */
    public abstract void perform(CommandSender sender, String[] args);

    /**
     * 执行命令的入口，包含通用检查。
     *
     * @param sender 命令发送者
     * @param args   用户输入的参数 (args[0] 是子命令标签)
     */
    public void execute(CommandSender sender, String[] args) {
        if (this.playersOnly() && !(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "此命令只能由玩家执行。"); // 示例消息
            return;
        }

        // 权限检查现在由 CommandManager 处理，或者如果希望每个 SubCmd 单独处理消息，可以在这里保留
        // 但 CommandManager 已经有了一层父命令的权限检查和消息发送
        // 如果这里的 getPermission() 是更细粒度的权限，那么这里的检查和消息是必要的
        if (!hasPerm(sender)) {
            String permMessage = plugin.getCommand("mckook").getPermissionMessage(); // 尝试获取父命令的无权限消息
            if (permMessage == null || permMessage.isEmpty()) {
                permMessage = ChatColor.RED + "你没有权限执行此子命令 (" + getPermission() + ")。"; // 默认消息
            } else {
                // 可以考虑替换占位符，如果 plugin.yml 中的消息支持的话
                permMessage = permMessage.replace("<permission>", getPermission() != null ? getPermission() : "N/A");
            }
            sender.sendMessage(permMessage);
            return;
        }
        this.perform(sender, args);
    }

    /**
     * 检查发送者是否拥有执行此子命令的权限。
     *
     * @param sender 命令发送者
     * @return 如果有权限则为 true，否则为 false
     */
    public boolean hasPerm(CommandSender sender) {
        String permission = this.getPermission();
        if (permission == null || permission.isEmpty()) { // 权限节点为空字符串也视为无权限要求
            return true;
        }
        // 对于控制台，如果定义了权限节点，通常认为其拥有权限
        // 或者可以根据需要修改为 sender.hasPermission(permission) 对控制台也生效
        return sender.hasPermission(permission);
    }

    /**
     * @return 子命令的别名数组，如果没有别名则返回 null 或空数组
     */
    public String[] aliases() {
        return null; // 或者 return new String[0];
    }
}