package com.meteor.mckook.command.cmds;

import com.meteor.mckook.McKook;
import com.meteor.mckook.command.SubCmd;
import com.meteor.mckook.config.Config;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
// import java.util.Arrays; // 未使用
// import java.util.Collections; // 未使用
import java.util.List;
import java.util.stream.Collectors;

public class MessageBridgeCmd extends SubCmd {

    private static final String PERM_BASE = "mckook.command.messagebridge.";
    private final String NO_PERMISSION_MESSAGE;

    public MessageBridgeCmd(McKook plugin) {
        super(plugin);
        String mainPermMsg = plugin.getCommand("mckook").getPermissionMessage();
        this.NO_PERMISSION_MESSAGE = (mainPermMsg != null && !mainPermMsg.isEmpty()) ?
                mainPermMsg : ChatColor.RED + "你没有权限执行此命令。";
    }

    @Override
    public String label() {
        return "messagebridge";
    }

    @Override
    public String getPermission() {
        return "mckook.command.use.messagebridge";
    }

    @Override
    public boolean playersOnly() {
        return false;
    }

    @Override
    public String usage() {
        return ChatColor.GOLD + "/mckook messagebridge <type> <on|off>";
    }

    @Override
    public String[] aliases() {
        return new String[0];
    }

    @Override
    public boolean hasPerm(CommandSender sender) {
        return sender.hasPermission(getPermission());
    }

    private boolean hasActionPermission(CommandSender sender, String actionTypeKey) {
        return sender.hasPermission(PERM_BASE + actionTypeKey.toLowerCase());
    }

    @Override
    public void perform(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendFullUsage(sender);
            return;
        }

        String actionArg = args[1].toLowerCase();
        String stateStr = args[2].toLowerCase();
        boolean newState;

        if ("on".equals(stateStr)) {
            newState = true;
        } else if ("off".equals(stateStr)) {
            newState = false;
        } else {
            sender.sendMessage(ChatColor.RED + "无效的状态: " + args[2] + "。请使用 'on' 或 'off'。");
            sendFullUsage(sender);
            return;
        }

        String configPath;
        String friendlyName;
        String actionPermissionKey;

        switch (actionArg) {
            case "playerjoinmessage":
                actionPermissionKey = "playerjoin";
                configPath = "setting.message-bridge.player-join.enabled";
                friendlyName = "玩家加入消息";
                break;
            case "playerquitmessage":
                actionPermissionKey = "playerquit";
                configPath = "setting.message-bridge.player-quit.enabled";
                friendlyName = "玩家离开消息";
                break;
            case "serverchattokook": // 新增
                actionPermissionKey = "serverchattokook";
                configPath = "setting.message-bridge.server-chat-to-kook.enabled";
                friendlyName = "服务器聊天转发到Kook";
                break;
            case "kookchattoserver": // 新增
                actionPermissionKey = "kookchattoserver";
                configPath = "setting.message-bridge.kook-chat-to-server.enabled";
                friendlyName = "Kook聊天转发到服务器";
                break;
            default:
                sender.sendMessage(ChatColor.RED + "无效的消息桥接类型: " + args[1]);
                sendFullUsage(sender);
                return;
        }

        if (!hasActionPermission(sender, actionPermissionKey)) {
            sender.sendMessage(NO_PERMISSION_MESSAGE);
            return;
        }

        try {
            Config.get().set(configPath, newState);
            Config.get().save();
            plugin.reloadMessageSystem(); // 确保这个方法会重新加载 PlayerChatMessage 并使其读取新配置

            sender.sendMessage(ChatColor.GREEN + friendlyName + " 功能已" + (newState ? "开启" : "关闭") + "。配置已保存并重载。");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "处理命令时发生错误: " + e.getMessage());
            plugin.getLogger().severe("Error processing MessageBridgeCmd for " + actionArg + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void sendFullUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "用法: " + usage());
        sender.sendMessage(ChatColor.YELLOW + "  可用的 <type> 包括:");
        sender.sendMessage(ChatColor.YELLOW + "  - playerjoinmessage  (玩家加入消息)");
        sender.sendMessage(ChatColor.YELLOW + "  - playerquitmessage  (玩家离开消息)");
        sender.sendMessage(ChatColor.YELLOW + "  - serverchattokook (服务器聊天转发到Kook)");
        sender.sendMessage(ChatColor.YELLOW + "  - kookchattoserver (Kook聊天转发到服务器)");
        // 旧的用法提示可以保留或移除
        // sender.sendMessage(ChatColor.YELLOW + "  /mckook messagebridge playerjoinmessage <on|off> - 控制玩家加入消息");
        // sender.sendMessage(ChatColor.YELLOW + "  /mckook messagebridge playerquitmessage <on|off> - 控制玩家离开消息");
    }

    @Override
    public List<String> getTab(CommandSender sender, String[] args) {
        String currentArg = args[args.length - 1].toLowerCase();
        List<String> suggestions = new ArrayList<>();

        if (args.length == 2) { // 补全操作类型 (args[1])
            if (hasActionPermission(sender, "playerjoin") && "playerjoinmessage".startsWith(currentArg)) {
                suggestions.add("playerjoinmessage");
            }
            if (hasActionPermission(sender, "playerquit") && "playerquitmessage".startsWith(currentArg)) {
                suggestions.add("playerquitmessage");
            }
            if (hasActionPermission(sender, "serverchattokook") && "serverchattokook".startsWith(currentArg)) { // 新增
                suggestions.add("serverchattokook");
            }
            if (hasActionPermission(sender, "kookchattoserver") && "kookchattoserver".startsWith(currentArg)) { // 新增
                suggestions.add("kookchattoserver");
            }
        } else if (args.length == 3) { // 补全状态 (args[2])
            String actionType = args[1].toLowerCase();
            boolean canSuggestState = false;

            switch (actionType) {
                case "playerjoinmessage":
                    if (hasActionPermission(sender, "playerjoin")) canSuggestState = true;
                    break;
                case "playerquitmessage":
                    if (hasActionPermission(sender, "playerquit")) canSuggestState = true;
                    break;
                case "serverchattokook": // 新增
                    if (hasActionPermission(sender, "serverchattokook")) canSuggestState = true;
                    break;
                case "kookchattoserver": // 新增
                    if (hasActionPermission(sender, "kookchattoserver")) canSuggestState = true;
                    break;
            }

            if (canSuggestState) {
                if ("on".startsWith(currentArg)) {
                    suggestions.add("on");
                }
                if ("off".startsWith(currentArg)) {
                    suggestions.add("off");
                }
            }
        }
        return suggestions.stream().sorted().collect(Collectors.toList());
    }
}