package com.meteor.mckook.storage.mapper.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meteor.mckook.McKook;
import com.meteor.mckook.model.link.KookUser;
import com.meteor.mckook.reflect.orm.ReflectFactory;
import com.meteor.mckook.storage.AbstractDatabase;
import com.meteor.mckook.storage.mapper.LinkRepository;
import com.meteor.mckook.storage.mapper.BaseMapper;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

public class LinkRepositoryImpl implements LinkRepository, BaseMapper {
    private final McKook plugin;

    private final AbstractDatabase database;

    private Cache<String,KookUser> kookUserCache;


    private final String KOOK_USER_TABLE_NAME = "KOOK_LINK_USER";


    public LinkRepositoryImpl(McKook plugin, AbstractDatabase database){
        this.plugin = plugin;

        this.database = database;

        this.kookUserCache = Caffeine.newBuilder()
                .expireAfterWrite(25, TimeUnit.MINUTES)
                .maximumSize(1000)
                .build();


        Map<String,String> params = new HashMap<>();

        params.put("table",KOOK_USER_TABLE_NAME);

        // 创建kook用户表
        this.database.executeUpdate("CREATE TABLE IF NOT EXISTS {table} (\n" +
                "    player_uuid CHAR(36) PRIMARY KEY,\n" +
                "    kook_id VARCHAR(255),\n" +
                "    player VARCHAR(25),\n" +
                "    userName VARCHAR(255),\n" +
                "    avatar TEXT,\n" +
                "    mobileVerified BOOLEAN,\n" +
                "    joinedAt BIGINT,\n" +
                "    nickName VARCHAR(255)\n" +
                ");\n",params,null);

    }
    /**
     * 用于临时存储从数据库查询到的绑定详情的辅助内部类。
     */
    private static class LinkDetails {
        final String playerUuid;
        final String playerName;

        LinkDetails(String playerUuid, String playerName) {
            this.playerUuid = playerUuid;
            this.playerName = playerName;
        }
    }
    /**
     * 尝试根据玩家名称获取其 UUID。
     * 此方法内部调用 Bukkit.getOfflinePlayer，这可能是一个阻塞操作。
     * 建议在异步线程中调用此方法，以避免阻塞主线程。
     *
     * @param playerName 玩家的名称
     * @return 如果成功获取到 UUID，则返回 UUID 对象；否则返回 null。
     */
    private UUID getPlayerUUIDByName(String playerName) {
        try {
            // Bukkit.getOfflinePlayer 可能会执行阻塞的网络调用。
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
            UUID uuid = offlinePlayer.getUniqueId();

            if (uuid == null) {
                // 这种情况对于有效的 playerName 来说比较罕见，但最好处理。
                this.plugin.getLogger().warning("[LinkService] 无法获取玩家 " + playerName + " 的UUID。该玩家可能从未在此服务器或以该名称登录。");
                return null;
            }
            return uuid;
        } catch (Exception e) {
            // 捕获泛型 Exception 可能隐藏具体问题，但在此处作为基本错误处理。
            this.plugin.getLogger().log(Level.WARNING, "[LinkService] 获取玩家 " + playerName + " 的UUID时发生异常: " + e.getMessage(), e);
            return null;
        }
    }
    /**
     * 根据 Kook ID 从数据库中获取绑定的玩家 UUID 和玩家名。
     *
     * @param kookId Kook ID
     * @return 如果找到绑定则返回 LinkDetails 对象，否则返回 null。
     */
    private LinkDetails getLinkDetailsByKookId(String kookId) {
        String sql = "SELECT player_uuid, player FROM " + KOOK_USER_TABLE_NAME + " WHERE kook_id = ? LIMIT 1";
        return this.database.executeQuery(sql, null, Arrays.asList(kookId), resultSet -> {
            try {
                if (resultSet.next()) {
                    return new LinkDetails(resultSet.getString("player_uuid"), resultSet.getString("player"));
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return null; // 未找到匹配的记录
        });
    }
    /**
     * 根据 player_uuid 从数据库中获取玩家名。
     * 这是一个内部辅助方法，主要用于在解绑时获取玩家名以进行缓存失效。
     *
     * @param playerUuidStr 玩家 UUID 的字符串表示。
     * @return 如果找到则返回玩家名，否则返回 null。
     */
    private String getPlayerNameByUUIDInternal(String playerUuidStr) {
        String selectSql = "SELECT player FROM " + KOOK_USER_TABLE_NAME + " WHERE player_uuid = ?";
        return this.database.executeQuery(selectSql, null, Arrays.asList(playerUuidStr), resultSet -> {
            try {
                if (resultSet.next()) {
                    return resultSet.getString("player");
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return null; // 未找到匹配的记录
        });
    }

    /**
     * 异步地将 Minecraft 玩家名与 Kook ID 绑定。
     * 此方法会异步执行以下操作：
     * 1. 根据玩家名获取玩家 UUID (可能涉及网络请求)。
     * 2. 检查该 UUID 是否已在数据库中存在绑定。
     * 3. 检查该 Kook ID 是否已在数据库中存在绑定。
     * 4. 构造 KookUser 对象。
     * 5. 将绑定信息写入数据库。
     * 所有操作均在异步线程中完成，结果和错误将通过回调传递，并记录到服务器日志。
     *
     * @param playerName Minecraft 玩家名。
     * @param kookId     要绑定的 Kook ID。
     * @param onSuccess  当成功绑定时调用此回调，参数为成功信息。
     * @param onFailure  当绑定失败时调用此回调，参数为错误或提示信息。
     */
    @Override
    public void bind(String playerName, String kookId, Consumer<String> onSuccess, Consumer<String> onFailure) {
        final String nickName = "fake_player"; // Consider if this should be dynamic or configurable

        this.plugin.getServer().getScheduler().runTaskAsynchronously(this.plugin, () -> {
            long startTime = System.currentTimeMillis();
            this.plugin.getLogger().info("[LinkService] (Async) 开始尝试为玩家 " + playerName + " 绑定 Kook ID: " + kookId);

            // 1) 根据玩家名拿 UUID
            UUID uuid = getPlayerUUIDByName(playerName);
            if (uuid == null) {
                String msg = "无法为玩家 " + playerName + " 获取UUID，绑定操作中止。";
                this.plugin.getLogger().warning("[LinkService] (Async) " + msg);
                onFailure.accept(msg);
                logOperationDuration(startTime, "绑定 (Player: " + playerName + ", Kook ID: " + kookId + ", UUID获取失败)");
                return;
            }
            String playerUuidStr = uuid.toString();

            // 2) 检查此 player_uuid 是否已存在绑定
            String checkUuidSql = "SELECT kook_id FROM " + KOOK_USER_TABLE_NAME + " WHERE player_uuid = ? LIMIT 1";
            try {
                String existingKookIdForUuid = this.database.executeQuery(checkUuidSql, null, Collections.singletonList(playerUuidStr), resultSet -> {
                    try {
                        if (resultSet.next()) {
                            return resultSet.getString("kook_id");
                        }
                        return null;
                    } catch (SQLException e) {
                        throw new RuntimeException("SQLException while checking existing UUID binding", e);
                    }
                });

                if (existingKookIdForUuid != null) {
                    String msg = "绑定失败：玩家 " + playerName + " (UUID: " + playerUuidStr + ") 已被 Kook ID: " + existingKookIdForUuid + " 绑定。请先解除现有绑定！";
                    this.plugin.getLogger().warning("[LinkService] (Async) " + msg);
                    onFailure.accept(msg);
                    logOperationDuration(startTime, "绑定 (Player: " + playerName + ", Kook ID: " + kookId + ", UUID已绑定)");
                    return;
                }

                // 3) 检查此 kook_id 是否已存在绑定
                String checkKookIdSql = "SELECT player FROM " + KOOK_USER_TABLE_NAME + " WHERE kook_id = ? LIMIT 1";
                String existingPlayerForKookId = this.database.executeQuery(checkKookIdSql, null, Collections.singletonList(kookId), resultSet -> {
                    try {
                        if (resultSet.next()) {
                            return resultSet.getString("player");
                        }
                        return null;
                    } catch (SQLException e) {
                        throw new RuntimeException("SQLException while checking existing Kook ID binding", e);
                    }
                });

                if (existingPlayerForKookId != null) {
                    String msg = "绑定失败：Kook ID " + kookId + " 已被玩家 " + existingPlayerForKookId + " 绑定。请先解除现有绑定或使用其他Kook ID！";
                    this.plugin.getLogger().warning("[LinkService] (Async) " + msg);
                    onFailure.accept(msg);
                    logOperationDuration(startTime, "绑定 (Player: " + playerName + ", Kook ID: " + kookId + ", Kook ID已绑定)");
                    return;
                }

            } catch (RuntimeException e) {
                String errorMsg = "绑定检查时发生数据库错误: " + e.getMessage();
                this.plugin.getLogger().log(Level.SEVERE, "[LinkService] (Async) " + errorMsg, e);
                onFailure.accept("绑定检查时发生内部错误，请联系管理员。");
                logOperationDuration(startTime, "绑定 (Player: " + playerName + ", Kook ID: " + kookId + ", 检查时出错)");
                return;
            }


            // 4) 构造 KookUser 实例
            KookUser user = new KookUser(
                    playerUuidStr,            // player_uuid
                    kookId,                   // kook_id
                    playerName,               // player
                    null,                     // userName (如果需要，后续可从 Kook API 获取)
                    null,                     // avatar   (如果需要，后续可从 Kook API 获取)
                    false,                    // mobileVerified
                    System.currentTimeMillis(), // joinedAt (本次绑定的时间戳)
                    nickName                  // nickName
            );

            // 5) 执行数据库链接操作 (写库)
            try {
                this.link(playerName, user); // This calls the `void link(String player, KookUser kookUser)` method

                String successMsg = "玩家 " + playerName + " (UUID: " + playerUuidStr + ") 已成功绑定到 Kook ID: " + kookId + "。";
                this.plugin.getLogger().info("[LinkService] (Async) " + successMsg);
                onSuccess.accept(successMsg);

            } catch (Exception e) {
                // 理论上，由于前面的检查，主键冲突不应再发生，但保留以防万一
                String errorMsg = "为玩家 " + playerName + " (UUID: " + playerUuidStr + ") 绑定 Kook ID " + kookId + " 时数据库操作失败。";
                this.plugin.getLogger().log(Level.SEVERE, "[LinkService] (Async) " + errorMsg + ": " + e.getMessage(), e);
                onFailure.accept("绑定失败：" + e.getMessage() + " 请联系管理员。");
            }

            logOperationDuration(startTime, "绑定 (Player: " + playerName + ", Kook ID: " + kookId + ")");
        });
    }
    /**
     * 根据 Minecraft 玩家名异步查询绑定关系中的 Kook ID。
     * 此方法会异步执行以下操作：
     * 1. 根据玩家名获取玩家 UUID (可能涉及网络请求)。
     * 2. 如果成功获取 UUID，则查询数据库以获取关联的 Kook ID。
     * 3. 将查询结果通过回调函数传递。
     * 所有操作均在异步线程中完成，错误也将记录到服务器日志并通过回调传递。
     *
     * @param playerName 要查询的 Minecraft 玩家名。
     * @param onSuccess 当成功找到 Kook ID 时调用此回调，参数为 Kook ID。
     * @param onFailure 当未找到绑定或发生错误时调用此回调，参数为错误或提示信息。
     */
    @Override
    public void bindgetKookIdByPlayerName(String playerName, Consumer<String> onSuccess, Consumer<String> onFailure) {
        this.plugin.getServer().getScheduler().runTaskAsynchronously(this.plugin, () -> {
            long startTime = System.currentTimeMillis();
            // 日志可以保留，用于服务器后台追踪
            // this.plugin.getLogger().info("[LinkService] (Async) 开始根据玩家名查询 Kook ID: " + playerName);

            // 1. 根据玩家名获取 UUID
            UUID uuid = getPlayerUUIDByName(playerName);
            if (uuid == null) {
                // getPlayerUUIDByName 方法内部已经记录了具体的失败原因
                String msg = "无法为玩家 " + playerName + " 获取UUID，查询 Kook ID 操作中止。";
                this.plugin.getLogger().warning("[LinkService] (Async) " + msg); // 保留服务器日志
                onFailure.accept(msg); // 通过回调通知调用者
                logOperationDuration(startTime, "查询 Kook ID (Player: " + playerName + ", UUID获取失败)");
                return;
            }
            String playerUuidStr = uuid.toString();

            // 2. & 3. 根据 UUID 查询数据库获取 kook_id 并通过回调返回
            String sql = "SELECT kook_id FROM " + KOOK_USER_TABLE_NAME + " WHERE player_uuid = ? LIMIT 1";
            try {
                String kookIdResult = this.database.executeQuery(sql, null, Arrays.asList(playerUuidStr), resultSet -> {
                    try {
                        if (resultSet.next()) {
                            return resultSet.getString("kook_id");
                        }
                        return null; // Kook ID not found for this UUID
                    } catch (SQLException e) {
                        // Consistent with other lambdas in this file, re-throw as RuntimeException
                        throw new RuntimeException("SQLException while processing ResultSet for Kook ID query", e);
                    }
                });

                if (kookIdResult != null && !kookIdResult.isEmpty()) {
                    this.plugin.getLogger().info("[LinkService] (Async) 玩家 " + playerName + " (UUID: " + playerUuidStr + ") 绑定的 Kook ID 为: " + kookIdResult); // 保留服务器日志
                    onSuccess.accept(kookIdResult); // 通过回调返回 Kook ID
                } else {
                    String msg = "未找到玩家 " + playerName + " (UUID: " + playerUuidStr + ") 的 Kook ID 绑定记录。";
                    this.plugin.getLogger().info("[LinkService] (Async) " + msg); // 保留服务器日志
                    onFailure.accept(msg); // 通过回调通知未找到
                }
            } catch (RuntimeException e) { // Catches RuntimeException from executeQuery's lambda or executeQuery itself
                String errorMsgForLog = "[LinkService] (Async) 根据玩家 " + playerName + " (UUID: " + playerUuidStr + ") 查询 Kook ID 时发生运行时异常: " + e.getMessage();
                this.plugin.getLogger().log(Level.SEVERE, errorMsgForLog, e); // 保留服务器日志
                onFailure.accept("查询 Kook ID 时发生内部错误，请联系管理员。"); // 通过回调通知错误
            }

            logOperationDuration(startTime, "查询 Kook ID (Player: " + playerName + ")");
        });
    }
    /**
     * 根据 Kook ID 异步查询绑定关系中的玩家名。
     * 此方法会异步执行数据库查询操作。
     *
     * @param kookId 要查询的 Kook ID。
     * @param onSuccess 当成功找到玩家名时调用此回调，参数为玩家名。
     * @param onFailure 当未找到绑定或发生错误时调用此回调，参数为错误或提示信息。
     */
    @Override
    public void bindgetPlayerNameByKookId(String kookId, Consumer<String> onSuccess, Consumer<String> onFailure) {
        this.plugin.getServer().getScheduler().runTaskAsynchronously(this.plugin, () -> {
            long startTime = System.currentTimeMillis();
            this.plugin.getLogger().info("[LinkService] (Async) 开始根据 Kook ID 查询玩家名: " + kookId);

            try {
                LinkDetails linkDetails = getLinkDetailsByKookId(kookId); // 这是一个同步数据库调用

                if (linkDetails != null && linkDetails.playerName != null) {
                    this.plugin.getLogger().info("[LinkService] (Async) Kook ID: " + kookId + " 绑定的玩家为: " + linkDetails.playerName + " (UUID: " + linkDetails.playerUuid + ")");
                    onSuccess.accept(linkDetails.playerName);
                } else {
                    String msg = "未找到 Kook ID: " + kookId + " 的有效绑定记录。";
                    this.plugin.getLogger().info("[LinkService] (Async) " + msg);
                    onFailure.accept(msg);
                }
            } catch (RuntimeException e) { // Catches RuntimeException from getLinkDetailsByKookId (e.g., SQLException)
                String errorMsgForLog = "[LinkService] (Async) 根据 Kook ID " + kookId + " 查询玩家名时发生运行时异常: " + e.getMessage();
                this.plugin.getLogger().log(Level.SEVERE, errorMsgForLog, e);
                onFailure.accept("查询玩家名时发生内部错误，请联系管理员。");
            }

            logOperationDuration(startTime, "查询玩家名 (Kook ID: " + kookId + ")");
        });
    }
    /**
     * 根据 Minecraft 玩家名异步移除绑定关系。
     * 此方法会异步执行以下操作：
     * 1. 根据玩家名获取玩家 UUID (可能涉及网络请求)。
     * 2. 检查此 UUID 是否在数据库中有绑定记录。
     * 3. 如果存在绑定，则从数据库中删除该记录。
     * 4. 使缓存中对应的条目失效。
     * 所有操作均在异步线程中完成，结果和错误将通过回调传递，并记录到服务器日志。
     *
     * @param playerName 要移除绑定的 Minecraft 玩家名。
     * @param onSuccess  当成功移除绑定时调用此回调，参数为成功信息。
     * @param onFailure  当移除绑定失败或未找到绑定时调用此回调，参数为错误或提示信息。
     */
    @Override
    public void bindremoveByplayerName(String playerName, Consumer<String> onSuccess, Consumer<String> onFailure) {
        this.plugin.getServer().getScheduler().runTaskAsynchronously(this.plugin, () -> {
            long startTime = System.currentTimeMillis();
            this.plugin.getLogger().info("[LinkService] (Async) 开始尝试为玩家解绑: " + playerName);

            // 1. 根据玩家名获取 UUID
            UUID uuid = getPlayerUUIDByName(playerName);
            if (uuid == null) {
                String msg = "无法为玩家 " + playerName + " 获取UUID，解绑操作中止。";
                this.plugin.getLogger().warning("[LinkService] (Async) " + msg);
                onFailure.accept(msg);
                logOperationDuration(startTime, "解绑 (Player: " + playerName + ", UUID获取失败)");
                return;
            }
            String playerUuidStr = uuid.toString();

            // 2. 检查此 UUID 是否在数据库中有绑定记录，并获取数据库中存储的玩家名
            String playerNameFromDb = getPlayerNameByUUIDInternal(playerUuidStr);

            if (playerNameFromDb == null) {
                String msg = "玩家 " + playerName + " (UUID: " + playerUuidStr + ") 未在系统中绑定或绑定信息不一致，无需移除。";
                plugin.getLogger().info("[LinkService] (Async) " + msg);
                onFailure.accept(msg); // Or a more user-friendly "Player not found or not bound."
                logOperationDuration(startTime, "解绑 (Player: " + playerName + ", 未找到绑定)");
                return;
            }

            // 3. 执行数据库删除操作
            String deleteSql = "DELETE FROM " + KOOK_USER_TABLE_NAME + " WHERE player_uuid = ?";
            try {
                int affectedRows = this.database.executeUpdate(deleteSql, null, Arrays.asList(playerUuidStr));

                if (affectedRows > 0) {
                    String successMsg = "成功移除了玩家 " + playerNameFromDb + " (UUID: " + playerUuidStr + ") 的绑定。";
                    plugin.getLogger().info("[LinkService] (Async) " + successMsg);
                    onSuccess.accept(successMsg);

                    // 4. 如果删除成功，使缓存失效
                    kookUserCache.invalidate(playerNameFromDb);
                    plugin.getLogger().info("[LinkService] (Async) 已使玩家: " + playerNameFromDb + " (UUID: " + playerUuidStr + ") 的缓存失效。");
                } else {
                    String warnMsg = "尝试为玩家 " + playerNameFromDb + " (UUID: " + playerUuidStr + ") 解绑时，数据库中未删除任何行 (可能已被提前移除或UUID不匹配)。";
                    plugin.getLogger().warning("[LinkService] (Async) " + warnMsg);
                    onFailure.accept("解绑玩家 " + playerNameFromDb + " 失败，可能记录已被移除。");
                }
            } catch (RuntimeException e) {
                String errorMsg = "为玩家 " + playerNameFromDb + " (UUID: " + playerUuidStr + ") 解绑时发生数据库错误。";
                plugin.getLogger().log(Level.SEVERE, "[LinkService] (Async) " + errorMsg + ": " + e.getMessage(), e);
                onFailure.accept("解绑时发生内部错误，请联系管理员。");
            }
            logOperationDuration(startTime, "解绑 (Player: " + playerName + ")");
        });
    }
    /**
     * 根据 Kook ID 异步移除绑定关系。
     * 此方法会异步执行以下操作：
     * 1. 根据 Kook ID 查询关联的玩家名和 UUID。
     * 2. 如果存在绑定，则从数据库中删除该记录。
     * 3. 使缓存中对应的玩家条目失效。
     * 所有操作均在异步线程中完成，结果和错误将通过回调传递，并记录到服务器日志。
     *
     * @param kookId 要移除绑定的 Kook ID。
     * @param onSuccess 当成功移除绑定时调用此回调，参数为成功信息。
     * @param onFailure 当移除绑定失败或未找到绑定时调用此回调，参数为错误或提示信息。
     */
    @Override
    public void bindremoveByKookId(String kookId, Consumer<String> onSuccess, Consumer<String> onFailure) {
        this.plugin.getServer().getScheduler().runTaskAsynchronously(this.plugin, () -> {
            long startTime = System.currentTimeMillis();
            this.plugin.getLogger().info("[LinkService] (Async) 开始尝试为 Kook ID 解绑: " + kookId);

            // 1. 根据 Kook ID 获取绑定的玩家名和 UUID
            LinkDetails linkDetails = getLinkDetailsByKookId(kookId); // This is a synchronous DB call

            if (linkDetails == null || linkDetails.playerName == null) {
                String msg = "未找到 Kook ID: " + kookId + " 的有效绑定记录，解绑操作中止。";
                this.plugin.getLogger().info("[LinkService] (Async) " + msg);
                onFailure.accept(msg);
                logOperationDuration(startTime, "解绑 (Kook ID: " + kookId + ", 未找到绑定)");
                return;
            }

            String playerName = linkDetails.playerName;
            // String playerUuid = linkDetails.playerUuid; // playerUuid can be used for logging or further operations

            // 2. 执行数据库删除操作 (直接使用 kookId 进行删除)
            String deleteSql = "DELETE FROM " + KOOK_USER_TABLE_NAME + " WHERE kook_id = ?";
            try {
                int affectedRows = this.database.executeUpdate(deleteSql, null, Arrays.asList(kookId));

                if (affectedRows > 0) {
                    String successMsg = "成功移除了 Kook ID: " + kookId + " (关联玩家: " + playerName + ") 的绑定。";
                    this.plugin.getLogger().info("[LinkService] (Async) " + successMsg);
                    onSuccess.accept(successMsg);

                    // 3. 如果删除成功，使缓存失效
                    kookUserCache.invalidate(playerName);
                    this.plugin.getLogger().info("[LinkService] (Async) 已使玩家: " + playerName + " (原 Kook ID: " + kookId + ") 的缓存失效。");
                } else {
                    String warnMsg = "尝试为 Kook ID: " + kookId + " 解绑时，数据库中未删除任何行 (可能已被提前移除)。";
                    this.plugin.getLogger().warning("[LinkService] (Async) " + warnMsg);
                    onFailure.accept("解绑 Kook ID: " + kookId + " 失败，可能记录已被移除。");
                }
            } catch (RuntimeException e) {
                String errorMsg = "为 Kook ID: " + kookId + " (关联玩家: " + playerName + ") 解绑时发生数据库错误。";
                this.plugin.getLogger().log(Level.SEVERE, "[LinkService] (Async) " + errorMsg + ": " + e.getMessage(), e);
                onFailure.accept("解绑时发生内部错误，请联系管理员。");
            }
            logOperationDuration(startTime, "解绑 (Kook ID: " + kookId + ")");
        });
    }

    private void logOperationDuration(long startTime, String operationName) {
        long duration = System.currentTimeMillis() - startTime;
        this.plugin.getLogger().info(String.format("[LinkService] (Async) 操作 '%s' 完成。耗时 %d ms", operationName, duration));
    }

    /**
     * 检查玩家是否已链接，并处理玩家改名的情况。
     * 注意：此方法包含 Bukkit.getOfflinePlayer() 调用，可能是阻塞的。
     *
     * @param currentPlayerName 当前的玩家名
     * @return 如果玩家已链接则返回 true，否则 false
     */
    @Override
    public boolean isLinked(String currentPlayerName) {
        // 1. 通过当前玩家名获取 UUID (潜在阻塞操作)
        UUID playerUuid = getPlayerUUIDByName(currentPlayerName);
        if (playerUuid == null) {
            // getPlayerUUIDByName 内部已打印日志
            return false; //无法获取UUID，视为未绑定
        }
        String playerUuidStr = playerUuid.toString();

        // 2. 检查缓存 (使用当前玩家名作为键)
        KookUser cachedUser = kookUserCache.getIfPresent(currentPlayerName);
        if (cachedUser != null) {
                return true; // 缓存有效且名字匹配
        }

        // 3. 使用 UUID 查询数据库
        // 查询所有字段，以便在链接存在时更新缓存
        String sql = "SELECT player_uuid, kook_id, player, userName, avatar, mobileVerified, joinedAt, nickName FROM " + KOOK_USER_TABLE_NAME + " WHERE player_uuid = ?";

        Boolean isLinkedResult = this.database.executeQuery(sql, null, Collections.singletonList(playerUuidStr), resultSet -> {
            try {
                if (resultSet.next()) {
                    String dbPlayerName = resultSet.getString("player"); // 数据库中存储的（可能旧的）玩家名
                    String kookId = resultSet.getString("kook_id");

                    if (kookId == null || kookId.isEmpty()) { // 理论上不应发生，因为我们是按UUID查的，但作为防御
                        return false;
                    }

                    // 4. 检查并更新数据库中的玩家名
                    if (!dbPlayerName.equals(currentPlayerName)) {
                        plugin.getLogger().info("[LinkService] 玩家 " + playerUuidStr + " (已绑定) 旧游戏名字 '" + dbPlayerName + "' 变更为 '" + currentPlayerName + "'. 进行数据库更新操作.");
                        String updateSql = "UPDATE " + KOOK_USER_TABLE_NAME + " SET player = ? WHERE player_uuid = ?";
                        try {
                            int affectedRows = this.database.executeUpdate(updateSql, null, Arrays.asList(currentPlayerName, playerUuidStr));
                            if (affectedRows > 0) {
                                plugin.getLogger().info("[LinkService] Successfully updated player name in DB for " + playerUuidStr + " to '" + currentPlayerName + "'.");
                                // 如果旧名字的缓存还存在，使其失效
                                if (kookUserCache.getIfPresent(dbPlayerName) != null) {
                                    kookUserCache.invalidate(dbPlayerName);
                                    plugin.getLogger().info("[LinkService] Invalidated cache for old name: '" + dbPlayerName + "'.");
                                }
                            } else {
                                plugin.getLogger().warning("[LinkService] Failed to update player name in DB for " + playerUuidStr + " (0 rows affected).");
                            }
                        } catch (Exception e) {
                            plugin.getLogger().log(Level.SEVERE, "[LinkService] Error updating player name in DB for " + playerUuidStr, e);
                            // 即使更新失败，绑定关系仍然存在，继续处理
                        }
                    }

                    // 5. 更新/填充缓存 (使用当前玩家名作为键，并确保KookUser对象内部玩家名也是最新的)
                    KookUser userToCache = new KookUser(
                            playerUuidStr, // resultSet.getString("player_uuid")
                            kookId,
                            currentPlayerName, // 关键：使用当前的玩家名
                            resultSet.getString("userName"),
                            resultSet.getString("avatar"),
                            resultSet.getBoolean("mobileVerified"),
                            resultSet.getLong("joinedAt"),
                            resultSet.getString("nickName")
                    );
                    kookUserCache.put(currentPlayerName, userToCache);
                    plugin.getLogger().fine("[LinkService] (isLinked) Refreshed cache for " + currentPlayerName + " (UUID: " + playerUuidStr + ")");
                    return true; // 玩家已链接
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "[LinkService] SQLException during isLinked DB check for UUID " + playerUuidStr, e);
                throw new RuntimeException("Database error in isLinked", e); // 让 executeQuery 的错误处理机制捕获
            }
            return false; // UUID 未在数据库中找到绑定记录
        });

        return Boolean.TRUE.equals(isLinkedResult); // 处理 executeQuery 可能返回 null 的情况
    }

    @Override
    public KookUser getLinkedKookUser(String player) {
        // 先走缓存
        KookUser cacheIfPresent = kookUserCache.getIfPresent(player);
        if(cacheIfPresent!=null){
            return cacheIfPresent;
        }
//根据玩家名字player来查询数据库的对应kook_id
        String sql = "select * from "+KOOK_USER_TABLE_NAME+" where player = ?";

        return this.database.executeQuery(sql,null,Arrays.asList(player),resultSet -> {
            try {
                if(resultSet.next()){
                    KookUser andPopulate = ReflectFactory.createAndPopulate(KookUser.class, resultSet);
                    kookUserCache.put(player,andPopulate);
                    return getLinkedKookUser(player);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InstantiationException e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    @Override
    public void link(String player, KookUser kookUser) {
        String sql = "INSERT INTO " + KOOK_USER_TABLE_NAME +
                " (player_uuid, kook_id, player, userName, avatar, mobileVerified, joinedAt, nickName) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        this.database.executeUpdate(sql,null,kookUser.getFieldList());
    }

    @Override
    public boolean kookUserIsLinked(String kookId) {
        String sql = "select player from "+KOOK_USER_TABLE_NAME+" where kook_id = ?";
        return this.database.executeQuery(sql,null,Arrays.asList(kookId),resultSet -> {
            try {
                if(resultSet.next()) return true;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return false;
        });
    }
}
