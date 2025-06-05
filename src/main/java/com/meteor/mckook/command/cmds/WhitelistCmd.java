package com.meteor.mckook.command.cmds;

import com.meteor.mckook.McKook;
import com.meteor.mckook.command.SubCmd;
import com.meteor.mckook.config.Config;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class WhitelistCmd extends SubCmd {

    // 建议的权限节点前缀
    private static final String PERM_BASE = "mckook.command.whitelist.";
    private static final String PERMISSION_TOGGLE = "mckook.admin.whitelist.toggle";
    private final String NO_PERMISSION_MESSAGE;
    private static final String CONFIG_PATH_ENABLE = "setting.whitelist.enable";
    private static final String FRIENDLY_NAME = "白名单验证功能";

    public WhitelistCmd(McKook plugin) {
        super(plugin);
        String mainPermMsg = plugin.getCommand("mckook").getPermissionMessage();
        this.NO_PERMISSION_MESSAGE = (mainPermMsg != null && !mainPermMsg.isEmpty()) ?
                ChatColor.translateAlternateColorCodes('&', mainPermMsg) :
                ChatColor.RED + "你没有权限执行此命令。";
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
    public String label() {
        return "whitelist";
    }

    @Override
    public String getPermission() {
        return PERMISSION_TOGGLE; // 权限节点，控制谁能用这个指令
    }

    @Override
    public boolean playersOnly() {
        return false; // 控制台和玩家都可以使用
    }

    @Override
    public String usage() {
        return ChatColor.GOLD + "用法: /mckook whitelist <on|off>";
    }

    @Override
    public String[] aliases() {
        return new String[0]; // 此命令暂无别名
    }

    @Override
    public boolean hasPerm(CommandSender sender) {
        return sender.hasPermission(getPermission());
    }

    @Override
    public void perform(CommandSender sender, String[] args) {
        // args[0] 应该是 "whitelist" (如果通过主命令分发)
        // 我们期望的结构是 /mckook whitelist <on|off>
        // 所以 args[0] 是 "whitelist", args[1] 是 "on" 或 "off"

        if (args.length < 2) { // args[0] is subcommand label, args[1] is on/off
            sender.sendMessage(usage());
            return;
        }
        
        String action = args[1].toLowerCase();
        // 检查特定 action 的权限
        if (!hasSubPerm(sender, action)) {
            sender.sendMessage(NO_PERMISSION_MESSAGE);
            return;
        }
        String stateStr = args[1].toLowerCase();
        boolean newState;

        if ("on".equals(stateStr)) {
            newState = true;
        } else if ("off".equals(stateStr)) {
            newState = false;
        } else {
            sender.sendMessage(ChatColor.RED + "无效的状态 '" + args[1] + "'。请使用 'on' 或 'off'。");
            sender.sendMessage(usage());
            return;
        }

        try {
            Config.get().set(CONFIG_PATH_ENABLE, newState);
            Config.get().save();
            // 重新加载消息系统以应用更改
            // 这需要确保 McKook#reloadMessageSystem 会重新创建 WhitelistMessage 实例
            plugin.reloadMessageSystem();

            sender.sendMessage(ChatColor.GREEN + FRIENDLY_NAME + " 已" + (newState ? "开启" : "关闭") + "。配置已保存并重载。");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "处理命令时发生错误: " + e.getMessage());
            plugin.getLogger().severe("处理 WhitelistCmd 时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public List<String> getTab(CommandSender sender, String[] args) {
        // args 示例:
        // /mckook whitelist -> args = ["whitelist", ""] (补全 on/off)
        // /mckook whitelist o -> args = ["whitelist", "o"] (补全 on/off)
        if (args.length == 2) { // 正在输入 on/off
            String currentArg = args[1].toLowerCase();
            List<String> suggestions = new ArrayList<>();
            if ("on".startsWith(currentArg)) {
                suggestions.add("on");
            }
            if ("off".startsWith(currentArg)) {
                suggestions.add("off");
            }
            return suggestions.stream().sorted().collect(Collectors.toList());
        }
        return Collections.emptyList(); // 其他情况不提供补全
    }
}