package com.meteor.mckook.message.sub;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meteor.mckook.McKook;
import com.meteor.mckook.kook.service.LinkService;
import com.meteor.mckook.message.AbstractKookMessage;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class WhitelistMessage extends AbstractKookMessage {

    private final String channelName;
    private final boolean enable;
    private final boolean bypassVanillaWhitelisted;

    private final List<String> promptLinkMessageTemplate;
    private final String kickForUnlinkedMessageTemplate;
    private final long kickDelayTicks;

    private final LinkService linkService;
    private final Cache<String, String> playerToVerifyCodeCache;

    public WhitelistMessage(McKook plugin, YamlConfiguration yamlConfiguration) {
        super(plugin, yamlConfiguration);
        this.channelName = yamlConfiguration.getString("channel", "白名单申请");
        this.enable = yamlConfiguration.getBoolean("setting.enable", false);
        this.bypassVanillaWhitelisted = yamlConfiguration.getBoolean("setting.bypass-whitelisted", false);

        this.promptLinkMessageTemplate = yamlConfiguration.getStringList("message.prompt-link-on-join");
        this.kickForUnlinkedMessageTemplate = yamlConfiguration.getString("message.kick-for-unlinked",
                "&cYou have been kicked because your account is not linked. Your code: {verifyCode}. Please use it in Kook channel: {channel_name}."); // Default updated
        int kickDelaySeconds = yamlConfiguration.getInt("setting.kick-delay-seconds", 10);
        this.kickDelayTicks = kickDelaySeconds * 20L;


        if (plugin.getKookBot() != null) {
            this.linkService = plugin.getKookBot().getService(LinkService.class);
        } else {
            this.linkService = null;
            plugin.getLogger().severe("[WhitelistMessage] KookBot is not available. LinkService could not be initialized!");
        }

        this.playerToVerifyCodeCache = Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(500)
                .build();
    }

    @Override
    public String getName() {
        return "白名单申请";
    }

    @org.bukkit.event.EventHandler
    public void onJoin(PlayerJoinEvent joinEvent) {
        Player player = joinEvent.getPlayer();
        String playerName = player.getName();

        if (!this.enable || this.linkService == null) {
            return;
        }

        if (linkService.isLinked(playerName)) {
            return;
        }

        if (this.bypassVanillaWhitelisted && Bukkit.getWhitelistedPlayers().stream().anyMatch(op -> op.getName().equalsIgnoreCase(playerName))) {
            // Make sure to compare player names, as Bukkit.getWhitelistedPlayers() returns OfflinePlayer
            // and player object might not be equal even if it's the same player.
            return;
        }

        String verifyCode = playerToVerifyCodeCache.getIfPresent(playerName);
        if (verifyCode == null || linkService.getLinkCache(verifyCode) == null) {
            verifyCode = linkService.buildVerifyCode(playerName);
            playerToVerifyCodeCache.put(playerName, verifyCode);
            getPlugin().getLogger().info("[Whitelist] Generated new verification code " + verifyCode + " for player " + playerName + " on join.");
        } else {
            getPlugin().getLogger().info("[Whitelist] Re-using existing verification code " + verifyCode + " for player " + playerName + " on join.");
        }

        sendLinkPromptMessage(player, verifyCode);

        // Store the final verifyCode to be used in the lambda
        final String finalVerifyCode = verifyCode;
        String kickMessage = ChatColor.translateAlternateColorCodes('&',
                this.kickForUnlinkedMessageTemplate
                        .replace("{player}", playerName)
                        .replace("{channel_name}", this.channelName)
                        .replace("{verifyCode}", finalVerifyCode) // Added verifyCode replacement
        );

        Bukkit.getScheduler().runTaskLater(getPlugin(), () -> {
            if (player.isOnline() && !linkService.isLinked(playerName)) {
                player.kickPlayer(kickMessage);
            }
        }, this.kickDelayTicks);
    }

    private void sendLinkPromptMessage(Player player, String verifyCode) {
        if (promptLinkMessageTemplate.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Error: Whitelist prompt message template is not configured.");
            return;
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("{player}", player.getName());
        placeholders.put("{channel_name}", this.channelName);
        placeholders.put("{kick_delay_seconds}", String.valueOf(this.kickDelayTicks / 20L));

        for (String rawLine : this.promptLinkMessageTemplate) {
            String lineWithPlaceholders = rawLine;
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                lineWithPlaceholders = lineWithPlaceholders.replace(entry.getKey(), entry.getValue());
            }

            // Split by {verifyCode} to insert clickable component
            String[] parts = lineWithPlaceholders.split("\\{verifyCode\\}", -1);
            TextComponent fullMessageLine = new TextComponent("");

            for (int i = 0; i < parts.length; i++) {
                if (!parts[i].isEmpty()) {
                    // Use TextComponent.fromLegacyText to handle color codes correctly
                    BaseComponent[] textComponents = TextComponent.fromLegacyText(
                            ChatColor.translateAlternateColorCodes('&', parts[i])
                    );
                    for (BaseComponent component : textComponents) {
                        fullMessageLine.addExtra(component);
                    }
                }

                if (i < parts.length - 1) { // If {verifyCode} was found and there's a part after it
                    TextComponent codeComponent = new TextComponent(verifyCode);
                    // Apply color and style from the template if specified before {verifyCode}
                    // This is implicitly handled by fromLegacyText on the part before,
                    // and the TextComponent itself can have styles.
                    // For example, if template is "...&b&l{verifyCode}...", the code will be bold and blue.
                    // We find the last color codes from the part before the codeComponent.
                    String partBefore = parts[i];
                    if (lineWithPlaceholders.contains("&" + ChatColor.COLOR_CHAR + "b&" + ChatColor.COLOR_CHAR + "l{verifyCode}")) { // Example specific check
                        // This is a bit manual; a more robust way would be to parse last color codes.
                        // For now, we rely on the template having colors directly before {verifyCode}
                        // e.g., "&fYour code: &b&l{verifyCode}"
                    }

                    codeComponent.setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, verifyCode));
                    codeComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            new Text(ChatColor.translateAlternateColorCodes('&', "&7点击复制验证码: &b" + verifyCode))));
                    fullMessageLine.addExtra(codeComponent);
                }
            }
            player.spigot().sendMessage(fullMessageLine);
        }
    }
}