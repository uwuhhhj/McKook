package com.meteor.mckook.kook.command;

import com.meteor.mckook.McKook;
import com.meteor.mckook.kook.KookBot;
import com.meteor.mckook.kook.command.cmds.BindCmd;
import com.meteor.mckook.kook.command.cmds.InfoCmd;
import snw.jkook.command.JKookCommand;
// User and Message are no longer directly used here if InfoCmd handles them
// import snw.jkook.entity.User;
// import snw.jkook.message.Message;
// import snw.jkook.message.component.TextComponent; // Not used if InfoCmd handles its own messages
import snw.jkook.message.component.MarkdownComponent;
import snw.jkook.plugin.Plugin;
import com.meteor.mckook.util.BaseConfig;

import java.util.logging.Level;

public class KookCommandManager {

    private final McKook mcKookPlugin;
    private KookBot kookBot;

    public KookCommandManager(McKook mcKookPlugin, KookBot kookBot) {
        this.mcKookPlugin = mcKookPlugin;
        this.kookBot = kookBot;
    }

    public void registerCommands() {
        if (kookBot == null || kookBot.isInvalid()) {
            mcKookPlugin.getLogger().warning("[KookCommandManager] KookBot 未就绪，无法注册 Kook 命令。");
            return;
        }

        if (kookBot.getKbcClient() == null || kookBot.getKbcClient().getInternalPlugin() == null) {
            mcKookPlugin.getLogger().warning("[KookCommandManager] KBCClient 或其内部插件未初始化，无法注册 Kook 命令。");
            return;
        }

        Plugin kbcInternalPlugin = kookBot.getKbcClient().getInternalPlugin();

        try {
            // 1. 定义 "info" 子命令 (通过 InfoCmd 类)
            InfoCmd infoCmdHandler = new InfoCmd(this.mcKookPlugin, this.kookBot);
            JKookCommand infoSubCommand = infoCmdHandler.buildCommand();

            // 2. 定义 "bind" 子命令及其操作 (通过 BindCmd 类)
            BindCmd bindCmdHandler = new BindCmd(this.mcKookPlugin, this.kookBot);
            JKookCommand bindSubCommand = bindCmdHandler.buildCommand();

            // 3. 注册主 "/mckook" 命令并添加 "info" 和 "bind" 作为其子命令
            new JKookCommand("mckook")
                    .setDescription("McKook 插件在 Kook 平台的主命令。")
                    .addSubcommand(infoSubCommand)
                    .addSubcommand(bindSubCommand)
                    .executesUser((sender, arguments, message) -> {
                        String helpMessage = BaseConfig.instance.getMessageBox().getMessage(null, "message.kook_message.help");
                        message.reply(new MarkdownComponent(helpMessage));
                    })
                    .register(kbcInternalPlugin);

            mcKookPlugin.getLogger().info("[KookCommandManager] 已成功注册 Kook 命令: /mckook (包含 info, bind 子命令)");

        } catch (Exception e) {
            mcKookPlugin.getLogger().log(Level.SEVERE, "[KookCommandManager] 注册 Kook 命令时发生严重错误", e);
        }
    }
}