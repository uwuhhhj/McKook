package com.meteor.mckook.command.cmds;
import com.meteor.mckook.kook.service.LinkService;

import com.meteor.mckook.McKook;
import com.meteor.mckook.command.SubCmd;
import com.meteor.mckook.util.BaseConfig;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class BindCmd extends SubCmd {
    private LinkService linkService;
    private final String NO_PERMISSION_MESSAGE;
    // 建议的权限节点前缀
    private static final String PERM_BASE = "mckook.command.bind.";

    public BindCmd(McKook plugin) {
        super(plugin);
        // 从配置文件获取无权限消息
        this.NO_PERMISSION_MESSAGE = BaseConfig.instance.getMessageBox().getMessage(null, "message.bind.no-permission");
    }

    @Override
    public String label() {
        return "bind";
    }

    public String getPermission() {
        return "mckook.command.use.bind";
    }

    @Override
    public boolean playersOnly() {
        return false;
    }

    @Override
    public String usage() {
        return ChatColor.translateAlternateColorCodes('&', "&6/mckook bind <add|getplayer|getkook|removeplayer|removekook> [参数...]");
    }

    @Override
    public String[] aliases() {
        return new String[0];
    }

    @Override
    public boolean hasPerm(CommandSender sender) {
        return sender.hasPermission("mckook.command.bind");
    }

    private boolean hasSubPerm(CommandSender sender, String action) {
        return sender.hasPermission(PERM_BASE + action.toLowerCase());
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        this.perform(sender, args);
    }

    @Override
    public void perform(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendFullUsage(sender);
            return;
        }

        String action = args[1].toLowerCase();

        if (!hasSubPerm(sender, action)) {
            sender.sendMessage(NO_PERMISSION_MESSAGE);
            return;
        }

        switch (action) {
            case "add":
                if (args.length < 4) {
                    sender.sendMessage(BaseConfig.instance.getMessageBox().getMessage(null, "message.bind.add.usage"));
                    return;
                }
                String playerNameToAdd = args[2];
                String kookIdToAdd = args[3];
                handleAddBinding(sender, playerNameToAdd, kookIdToAdd);
                break;
            case "getplayer":
                if (args.length < 3) {
                    sender.sendMessage(BaseConfig.instance.getMessageBox().getMessage(null, "message.bind.getplayer.usage"));
                    return;
                }
                String playerNameToGet = args[2];
                handleGetKookIdByPlayer(sender, playerNameToGet);
                break;
            case "getkook":
                if (args.length < 3) {
                    sender.sendMessage(BaseConfig.instance.getMessageBox().getMessage(null, "message.bind.getkook.usage"));
                    return;
                }
                String kookIdToGet = args[2];
                handleGetPlayerByKookId(sender, kookIdToGet);
                break;
            case "removeplayer":
                if (args.length < 3) {
                    sender.sendMessage(BaseConfig.instance.getMessageBox().getMessage(null, "message.bind.removeplayer.usage"));
                    return;
                }
                String playerNameToRemove = args[2];
                handleRemoveBindingByPlayer(sender, playerNameToRemove);
                break;
            case "removekook":
                if (args.length < 3) {
                    sender.sendMessage(BaseConfig.instance.getMessageBox().getMessage(null, "message.bind.removekook.usage"));
                    return;
                }
                String kookIdToRemove = args[2];
                handleRemoveBindingByKookId(sender, kookIdToRemove);
                break;
            default:
                Map<String, String> params = new HashMap<>();
                params.put("@action@", args[1]);
                sender.sendMessage(BaseConfig.instance.getMessageBox().getMessage(params, "message.bind.unknown-action"));
                sendFullUsage(sender);
                break;
        }
    }

    private void sendFullUsage(CommandSender sender) {
        List<String> usageMessages = BaseConfig.instance.getMessageBox().getMessageList(Collections.emptyMap(), "message.bind.usage");
        for (String message : usageMessages) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        }
    }

    private LinkService getLinkService() {
        if (plugin.getKookBot() != null) {
            try {
                this.linkService = plugin.getKookBot().getService(LinkService.class);
                return this.linkService;
            } catch (Exception e) {
                plugin.getLogger().warning("获取 LinkService 失败: " + e.getMessage());
                this.linkService = null;
                return null;
            }
        }
        this.linkService = null;
        return null;
    }

    private void handleAddBinding(CommandSender sender, String playerName, String kookId) {
        LinkService currentLinkService = getLinkService();
        if (currentLinkService == null) {
            sender.sendMessage(BaseConfig.instance.getMessageBox().getMessage(null, "message.bind.service-not-ready"));
            return;
        }

        Map<String, String> params = new HashMap<>();
        params.put("@player@", playerName);
        params.put("@kook-id@", kookId);
        sender.sendMessage(BaseConfig.instance.getMessageBox().getMessage(params, "message.bind.add.start"));

        currentLinkService.linkRepository.bind(
                playerName,
                kookId,
                successMessage -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(ChatColor.GREEN + successMessage);
                    });
                },
                errorMessage -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(ChatColor.YELLOW + errorMessage);
                    });
                }
        );
    }

    private void handleGetKookIdByPlayer(CommandSender sender, String playerName) {
        LinkService currentLinkService = getLinkService();
        if (currentLinkService == null) {
            sender.sendMessage(BaseConfig.instance.getMessageBox().getMessage(null, "message.bind.service-not-ready"));
            return;
        }

        Map<String, String> params = new HashMap<>();
        params.put("@player@", playerName);
        sender.sendMessage(BaseConfig.instance.getMessageBox().getMessage(params, "message.bind.getplayer.start"));

        currentLinkService.linkRepository.bindgetKookIdByPlayerName(
                playerName,
                kookId -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        params.put("@kook-id@", kookId);
                        sender.sendMessage(BaseConfig.instance.getMessageBox().getMessage(params, "message.bind.getplayer.success"));
                    });
                },
                errorMessage -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(ChatColor.YELLOW + errorMessage);
                    });
                }
        );
    }

    private void handleGetPlayerByKookId(CommandSender sender, String kookId) {
        LinkService currentLinkService = getLinkService();
        if (currentLinkService == null) {
            sender.sendMessage(BaseConfig.instance.getMessageBox().getMessage(null, "message.bind.service-not-ready"));
            return;
        }

        Map<String, String> params = new HashMap<>();
        params.put("@kook-id@", kookId);
        sender.sendMessage(BaseConfig.instance.getMessageBox().getMessage(params, "message.bind.getkook.start"));

        currentLinkService.linkRepository.bindgetPlayerNameByKookId(
                kookId,
                playerName -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        params.put("@player@", playerName);
                        sender.sendMessage(BaseConfig.instance.getMessageBox().getMessage(params, "message.bind.getkook.success"));
                    });
                },
                errorMessage -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(ChatColor.YELLOW + errorMessage);
                    });
                }
        );
    }

    private void handleRemoveBindingByPlayer(CommandSender sender, String playerName) {
        LinkService currentLinkService = getLinkService();
        if (currentLinkService == null) {
            sender.sendMessage(BaseConfig.instance.getMessageBox().getMessage(null, "message.bind.service-not-ready"));
            return;
        }

        Map<String, String> params = new HashMap<>();
        params.put("@player@", playerName);
        sender.sendMessage(BaseConfig.instance.getMessageBox().getMessage(params, "message.bind.removeplayer.start"));

        currentLinkService.linkRepository.bindremoveByplayerName(
                playerName,
                successMessage -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(ChatColor.GREEN + successMessage);
                    });
                },
                errorMessage -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(ChatColor.YELLOW + errorMessage);
                    });
                }
        );
    }

    private void handleRemoveBindingByKookId(CommandSender sender, String kookId) {
        LinkService currentLinkService = getLinkService();
        if (currentLinkService == null) {
            sender.sendMessage(BaseConfig.instance.getMessageBox().getMessage(null, "message.bind.service-not-ready"));
            return;
        }

        Map<String, String> params = new HashMap<>();
        params.put("@kook-id@", kookId);
        sender.sendMessage(BaseConfig.instance.getMessageBox().getMessage(params, "message.bind.removekook.start"));

        currentLinkService.linkRepository.bindremoveByKookId(
                kookId,
                successMessage -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(ChatColor.GREEN + successMessage);
                    });
                },
                errorMessage -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(ChatColor.YELLOW + errorMessage);
                    });
                }
        );
    }

    @Override
    public List<String> getTab(CommandSender sender, String[] args) {
        String currentArg = args[args.length - 1].toLowerCase();

        if (args.length == 2) {
            List<String> actions = Arrays.asList("add", "getplayer", "getkook", "removeplayer", "removekook");
            return actions.stream()
                    .filter(action -> hasSubPerm(sender, action))
                    .filter(action -> action.toLowerCase().startsWith(currentArg))
                    .collect(Collectors.toList());
        }

        if (args.length >= 3) {
            String action = args[1].toLowerCase();
            if (!hasSubPerm(sender, action)) {
                return Collections.emptyList();
            }

            if (args.length == 3) {
                List<String> suggestions = new ArrayList<>();
                switch (action) {
                    case "add":
                    case "getplayer":
                    case "removeplayer":
                        Bukkit.getOnlinePlayers().stream()
                                .map(Player::getName)
                                .filter(name -> name.toLowerCase().startsWith(currentArg))
                                .forEach(suggestions::add);
                        break;
                }
                return suggestions;
            }
        }
        return Collections.emptyList();
    }
}