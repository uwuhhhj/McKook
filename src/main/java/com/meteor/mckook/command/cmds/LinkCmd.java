package com.meteor.mckook.command.cmds;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meteor.mckook.McKook;
import com.meteor.mckook.command.SubCmd;
import com.meteor.mckook.kook.service.LinkService;
import com.meteor.mckook.util.BaseConfig;
import net.md_5.bungee.api.chat.*;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.md_5.bungee.api.chat.hover.content.Text; // 更推荐使用新的 HoverEvent.Action.SHOW_TEXT 构造方式

// import java.awt.*; // 未使用，可以移除
import java.util.*;
import java.util.concurrent.TimeUnit;

public class LinkCmd extends SubCmd {

    // linkService 可能会在 KookBot 未就绪时为 null
    private LinkService linkService;

    private final Cache<String,String> cache; // 设为 final


    public LinkCmd(McKook plugin) {
        super(plugin);

        // 不在此处直接初始化 linkService，因为它依赖的 KookBot 可能是异步加载的
        // this.linkService = plugin.getKookBot().getService(LinkService.class); // <--- 移除或注释掉

        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(500)
                .build();
    }

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


    @Override
    public String label() {
        return "link";
    }

    @Override
    public String getPermission() {
        return "mckook.command.use.link";
    }

    @Override
    public boolean playersOnly() {
        return true; // 绑定命令仅限玩家
    }

    @Override
    public String usage() {
        return "/mckook link"; // 清晰的用法
    }

    @Override
    public void perform(CommandSender sender, String[] args) { // 参数名 p0, p1 已修正为 sender, args

        Player player = (Player) sender; // 因为 playersOnly() 是 true，这里强制转换是安全的

        LinkService currentLinkService = getLinkService(); // 获取 LinkService 实例

        if (currentLinkService == null) {
            player.sendMessage(ChatColor.RED + "绑定服务当前不可用，Kook机器人可能未连接。请稍后再试或联系管理员。");
            return;
        }
        if(currentLinkService.isLinked(player.getName())){
            // 假设 getMessage 返回的是处理过的字符串
            player.sendMessage(BaseConfig.instance.getMessageBox().getMessage(null,"message.link.already-link"));
            return;
        }

        String playerName = player.getName();

        // 存在未过期验证码
        String existingCode = cache.getIfPresent(playerName); // 只获取一次
        if(existingCode != null){
            Map<String,String> params = new HashMap<>(); // 仅在需要时创建
            params.put("@verify-code@", existingCode);
            player.sendMessage(BaseConfig.instance.getMessageBox().getMessage(params,"message.link.exist-verify"));
            return;
        }

        String verifyCode = currentLinkService.buildVerifyCode(playerName);
        cache.put(playerName, verifyCode);

        // 从配置中读取模板
        List<String> template = BaseConfig.instance.getMessageBox()
                .getMessageList(Collections.emptyMap(), "message.link.build-verify");

        // 发送可点击复制的组件消息
        sendVerifyMessageWithCopy(player, verifyCode, template);
    }

    public void sendVerifyMessageWithCopy(Player player, String code, List<String> templateLines) {
        if (templateLines == null || templateLines.isEmpty()) {
            // 创建一个基础的可点击组件作为备用
            TextComponent fallbackMessage = new TextComponent(ChatColor.YELLOW + "你的验证码是: ");
            TextComponent codeComponent = new TextComponent(code);
            codeComponent.setColor(net.md_5.bungee.api.ChatColor.AQUA); // 给验证码本身也加个颜色
            codeComponent.setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, code));
            codeComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new Text("§7点击复制验证码"))); // 使用新的 Text content
            fallbackMessage.addExtra(codeComponent);
            player.spigot().sendMessage(fallbackMessage);

            plugin.getLogger().warning("消息模板 'message.link.build-verify' 未找到或为空，已发送备用消息。");
            return;
        }

        for (String rawLine : templateLines) {
            String[] parts = rawLine.split("@verify-code@", -1); // 使用原始行进行 split
            TextComponent fullMessageLine = new TextComponent(""); // 为每一行创建一个新的 TextComponent

            for (int i = 0; i < parts.length; i++) {
                if (!parts[i].isEmpty()) {
                    // 使用 fromLegacyText 来正确处理颜色代码和格式
                    // fromLegacyText 返回 BaseComponent[]，需要遍历并添加
                    BaseComponent[] textComponents = TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', parts[i]));
                    for (BaseComponent component : textComponents) {
                        fullMessageLine.addExtra(component);
                    }
                }

                // 如果不是最后一个部分（即 @verify-code@ 之后还有文本，或者 @verify-code@ 是末尾），则添加验证码组件
                if (i < parts.length - 1) {
                    TextComponent copyComponent = new TextComponent(code);
                    // 可以给验证码本身设置颜色
                    copyComponent.setColor(net.md_5.bungee.api.ChatColor.GOLD); // 例如金色
                    copyComponent.setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, code));
                    copyComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            new Text("§7点击复制验证码"))); // 使用新的 Text content API
                    fullMessageLine.addExtra(copyComponent);
                }
            }
            player.spigot().sendMessage(fullMessageLine);
        }
    }
    // getTab 方法会继承 SubCmd 中的默认实现 (返回 Collections.emptyList())
    // 对于一个不接受额外参数的 link 命令，这是合适的。
}