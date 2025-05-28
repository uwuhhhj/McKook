package com.meteor.mckook.command.cmds;

import com.meteor.mckook.McKook;
import com.meteor.mckook.command.CommandManager; // 需要引入 CommandManager 来使用 getSugg 方法
import com.meteor.mckook.command.SubCmd;
import org.bukkit.ChatColor; // 引入 ChatColor 用于消息
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ReloadCmd extends SubCmd {

    // 定义可用的重载选项常量
    private static final String OPTION_CONFIG = "config";
    private static final String OPTION_BOT = "bot";
    private static final String OPTION_MESSAGES = "messages";
    private static final String OPTION_ALL = "all";
    // 如果 DataManager 需要独立重载，可以添加一个选项
    // private static final String OPTION_DATABASE = "database";


    public ReloadCmd(McKook plugin) {
        super(plugin);
    }

    @Override
    public String label() {
        return "reload";
    }

    @Override
    public String getPermission() {
        return "mckook.admin.reload"; // 重载命令通常需要管理员权限
    }

    @Override
    public boolean playersOnly() {
        return false; // 控制台和玩家都可以执行重载
    }

    @Override
    public String usage() {
        // 更新用法提示，包含所有选项
        return "/mckook reload [" + OPTION_CONFIG + "|" + OPTION_BOT + "|" + OPTION_MESSAGES + "|" + OPTION_ALL + "]";
        // 如果有数据库选项:
        // return "/mckook reload [" + OPTION_CONFIG + "|" + OPTION_BOT + "|" + OPTION_MESSAGES + "|" + OPTION_DATABASE + "|" + OPTION_ALL + "]";
    }

    @Override
    public void perform(CommandSender sender, String[] args) { // 参数名已修正为 sender, args

        // 检查参数数量
        if (args.length < 2) {
            // 如果只输入 /mckook reload，默认执行完全重载
            plugin.reload(); // 调用 McKook 中的 performFullReload() 方法 (如果 reload() 转发到它)
            // 或者直接调用 plugin.performFullReload(); 如果你移除了旧的 reload() 方法
            // plugin.performFullReload(); // 推荐直接调用 performFullReload()
            sender.sendMessage(ChatColor.GREEN + "McKook 插件已完全重载。");
            return;
        }

        // 获取重载选项
        String option = args[1].toLowerCase();

        switch (option) {
            case OPTION_CONFIG:
                plugin.reloadPluginConfig();
                sender.sendMessage(ChatColor.GREEN + "主配置文件 (config.yml) 已重新加载。");
                sender.sendMessage(ChatColor.YELLOW + "提示: Kook机器人和消息系统可能需要分别重载以应用所有更改 (/mckook reload bot, /mckook reload messages)。");
                break;

            case OPTION_BOT:
                plugin.reloadKookBot(); // 这个方法现在是异步的
                sender.sendMessage(ChatColor.GREEN + "Kook 机器人重载任务已启动。请稍候，机器人将异步连接。");
                break;

            case OPTION_MESSAGES:
                plugin.reloadMessageSystem();
                sender.sendMessage(ChatColor.GREEN + "消息系统及其配置文件已重新加载。");
                break;

            // case OPTION_DATABASE: // 如果你为 DataManager 添加了重载逻辑
            //    // plugin.reloadDataManager();
            //    sender.sendMessage(ChatColor.GREEN + "数据管理器已重新加载。");
            //    break;

            case OPTION_ALL:
                plugin.reload(); // 调用 McKook 中的 performFullReload() 方法
                // 或者直接调用 plugin.performFullReload();
                // plugin.performFullReload(); // 推荐
                sender.sendMessage(ChatColor.GREEN + "McKook 插件已完全重载。");
                break;

            default:
                sender.sendMessage(ChatColor.RED + "未知的重载选项: " + args[1]);
                sender.sendMessage(ChatColor.YELLOW + "用法: " + usage()); // 显示正确的用法
                break;
        }

        // 移除 BaseConfig.instance.reload();
        // 因为 plugin.reloadPluginConfig() 或 plugin.reload() 已经包含了 BaseConfig 的重载
        // BaseConfig.instance.reload(); // <-- 移除这行，避免重复或不一致
        // 移除发送静态重载消息的代码
        // p0.sendMessage(BaseConfig.instance.getMessageBox().getMessage(null,"message.reload")); // <-- 移除这行
    }

    @Override
    public List<String> getTab(CommandSender sender, String[] args) {
        // 只有当用户输入了 "reload" 并且正在输入第二个参数时才提供补全
        if (args.length == 2 && args[0].equalsIgnoreCase(label())) {
            // 定义所有可能的重载选项
            List<String> options = Arrays.asList(OPTION_CONFIG, OPTION_BOT, OPTION_MESSAGES, OPTION_ALL);
            // 如果有数据库选项:
            // List<String> options = Arrays.asList(OPTION_CONFIG, OPTION_BOT, OPTION_MESSAGES, OPTION_DATABASE, OPTION_ALL);

            // 使用 CommandManager 中的 getSugg 方法进行过滤和排序
            // args[1] 是当前正在输入的第二个参数
            return CommandManager.getSugg(args[1], options);
        }

        // 对于其他参数数量，不提供补全
        return Collections.emptyList();
    }
}