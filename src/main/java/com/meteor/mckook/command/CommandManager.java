package com.meteor.mckook.command;

import com.meteor.mckook.McKook;
import com.meteor.mckook.command.cmds.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import java.util.*;
import java.util.stream.Collectors;

public class CommandManager implements CommandExecutor, TabCompleter {
    private final McKook plugin; // 声明为 final，在构造函数中初始化后不可变
    private final Map<String, SubCmd> commands = new HashMap<>(); // 声明时初始化，或在构造函数中

    public CommandManager(McKook plugin) {
        this.plugin = plugin;
        // this.commands = new HashMap<>(); // 可以在这里初始化，或者在字段声明时
    }

    /**
     * 初始化并注册所有子命令。
     * 这个方法应该在 KookBot 成功初始化后被调用。
     */
    public void init() {
        // commands.clear(); // 如果 init 可能被多次调用，先清空
        register(new HelpCmd(plugin));
        register(new LinkCmd(plugin)); // LinkCmd 内部需要处理 plugin.getKookBot() 可能为 null 的情况
        register(new ReloadCmd(plugin));
        register(new BindCmd(plugin));
        register(new MessageBridgeCmd(plugin));
        register(new WhitelistCmd(plugin));
        // 注册其他子命令...
    }

    private void register(SubCmd cmd) {
        this.commands.put(cmd.label().toLowerCase(), cmd); // 建议将命令标签统一转为小写存储，便于查找
        if (cmd.aliases() != null) {
            for (String alias : cmd.aliases()) {
                this.commands.put(alias.toLowerCase(), cmd); // 别名也转为小写
            }
        }
    }

    /**
     * 辅助方法，用于过滤和排序Tab补全建议。
     * @param arg 当前输入的参数
     * @param source 可能的补全项列表
     * @return 过滤和排序后的补全建议列表
     */
    public static List<String> getSugg(final String arg, final List<String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList(); // 返回空列表而不是null，更安全
        }
        // 使用 Stream API 进行过滤和收集，更简洁
        return source.stream()
                .filter(s -> StringUtil.startsWithIgnoreCase(s, arg))
                .sorted(String.CASE_INSENSITIVE_ORDER) // 按字母顺序排序（不区分大小写）
                .collect(Collectors.toList());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String subCommandLabel = "help"; // 默认执行 help 命令

        if (args.length > 0) {
            // 优先使用 args[0] 作为子命令标签，并转为小写进行匹配
            String inputLabel = args[0].toLowerCase();
            if (this.commands.containsKey(inputLabel)) {
                subCommandLabel = inputLabel;
            } else {
                // 如果输入的第一个参数不是已知的子命令，
                // 仍然可以执行默认的 help 命令，或者提示未知命令
                // 此处保持原逻辑，如果找不到则 help 命令的 execute 会处理
            }
        }

        SubCmd executedCmd = this.commands.get(subCommandLabel);
        if (executedCmd == null) {
            // 理论上 "help" 命令应该总是存在的，但作为防御性编程可以处理一下
            sender.sendMessage("§c错误: 无法找到命令处理器。请联系管理员。");
            return true;
        }

        // 在执行前检查权限
        if (!executedCmd.hasPerm(sender)) {
            sender.sendMessage(plugin.getCommand("mckook").getPermissionMessage() != null ?
                    plugin.getCommand("mckook").getPermissionMessage() : "§c你没有权限执行此命令。");
            return true;
        }

        executedCmd.execute(sender, args);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // 玩家和控制台都可以有 Tab 补全
        // if (!(sender instanceof Player)) {
        //     return Collections.emptyList(); // 或者为控制台提供特定补全
        // }

        if (args.length == 0) { // 不应该发生，但作为防御
            return Collections.emptyList();
        }

        String currentArg = args[args.length - 1];

        if (args.length == 1) {
            // 补全子命令的标签
            List<String> subCommandLabels = new ArrayList<>();
            for (SubCmd cmd : new HashSet<>(commands.values())) { // 使用 HashSet 去重（如果别名指向同一个 SubCmd 实例）
                if (cmd.hasPerm(sender)) {
                    subCommandLabels.add(cmd.label());
                    // 如果需要，也可以添加别名到补全列表
                    // if (cmd.aliases() != null) {
                    //    subCommandLabels.addAll(cmd.aliases());
                    // }
                }
            }
            return getSugg(currentArg, subCommandLabels);
        }

        // 补全特定子命令的参数
        String subCommandLabel = args[0].toLowerCase();
        SubCmd subCmd = commands.get(subCommandLabel);

        if (subCmd == null || !subCmd.hasPerm(sender)) {
            // 如果第一个参数不是有效的子命令，或者发送者没有权限，则不提供后续参数的补全
            return Collections.emptyList();
        }

        // 调用子命令自己的 Tab 补全逻辑
        // args.length - 1 是因为子命令的 getTab 通常期望的是子命令参数的索引
        // 例如 /mckook link <arg1_for_link> <arg2_for_link>
        // 当补全 <arg1_for_link> 时, args = ["link", "<arg1_for_link_partial>"], args.length = 2
        // 传递给 subCmd.getTab 的参数索引应该是 1 (args.length - 1)
        // 或者，更清晰地，可以调整 SubCmd.getTab 的参数设计
        List<String> subCmdSuggestions = subCmd.getTab(sender, args); // 假设 SubCmd.getTab 接受 (sender, allArgs)

        return getSugg(currentArg, subCmdSuggestions);
    }
}