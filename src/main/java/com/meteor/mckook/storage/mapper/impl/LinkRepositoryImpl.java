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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
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
     * 通过 Minecraft 玩家名 和 kookId 建立绑定。
     * 【警告】此方法现在同步执行，包含潜在的阻塞操作 (网络请求和数据库写入)，
     * 如果在主线程调用，可能导致服务器卡顿。
     *
     * @param playerName Minecraft 玩家名
     * @param kookId     要绑定的 kook_id
     */
    public void bind(String playerName, String kookId) {
        // 遵循 Java 命名约定，变量名使用 camelCase (此注释针对 nickName，但其为硬编码)
        // nickName 的值可以考虑是否需要更动态的来源
        final String nickName = "fake_player";

        // 使用 Bukkit Scheduler 异步执行任务
        this.plugin.getServer().getScheduler().runTaskAsynchronously(this.plugin, () -> {
            long start = System.currentTimeMillis();

            // 1) 根据玩家名拿 UUID
            UUID uuid = getPlayerUUIDByName(playerName); // 调用新提取的方法
                if (uuid == null) {
                    // 这种情况对于有效的 playerName 来说比较罕见，但最好处理。
                    this.plugin.getLogger().warning("[LinkService] (Async) 无法获取玩家 " + playerName + " 的UUID。该玩家可能从未在此服务器或以该名称登录。");
                    return; // 终止异步任务
                }
            // 2) 构造 KookUser 实例
            KookUser user = new KookUser(
                    uuid.toString(),          // player_uuid
                    kookId,                   // kook_id
                    playerName,               // player
                    null,                     // userName (如果需要，后续可从 Kook API 获取)
                    null,                     // avatar   (如果需要，后续可从 Kook API 获取)
                    false,                    // mobileVerified
                    System.currentTimeMillis(), // joinedAt (本次绑定的时间戳)
                    nickName                  // nickName
            );

            // 3) 执行数据库链接操作 (写库)
            // 数据库操作现在也在异步线程中执行。
            try {
                this.link(playerName, user); // 调用包含 INSERT SQL 查询的方法
            } catch (Exception e) {
                // 例如，如果 player_uuid 已存在 (主键冲突)，这里会捕获到异常
                this.plugin.getLogger().log(Level.SEVERE, "[LinkService] (Async) 绑定玩家 " + playerName + " 到 kook_id " + kookId + " 时数据库操作失败: " + e.getMessage(), e);
                // 考虑向用户/发送者反馈绑定失败的信息（如果适用，需要调度回主线程）
                return; // 终止异步任务
            }

            // 4) 计算耗时并记录日志
            long duration = System.currentTimeMillis() - start;
            this.plugin.getLogger().info(
                    String.format("[LinkService] 玩家 %s 成功绑定 kook_id %s。耗时 %d ms (异步执行)",
                            playerName, kookId, duration)
            );
            // 如果此方法由命令调用，考虑向命令发送者发送成功消息
            // 这需要调度回主线程，例如:
            // Bukkit.getScheduler().runTask(this.plugin, () -> {
            //     Player onlinePlayer = Bukkit.getPlayerExact(playerName);
            //     if (onlinePlayer != null) {
            //         onlinePlayer.sendMessage("您已成功绑定 Kook 账户！");
            //     }
            // });
        });
    }

    /**
     * 根据 Minecraft 玩家名异步查询绑定关系中的 Kook ID。
     * 此方法会异步执行以下操作：
     * 1. 根据玩家名获取玩家 UUID (可能涉及网络请求)。
     * 2. 如果成功获取 UUID，则查询数据库以获取关联的 Kook ID。
     * 3. 将查询结果（找到的 Kook ID 或未找到的信息）记录到服务器日志。
     * 所有操作均在异步线程中完成，错误也将记录到服务器日志。
     *
     * @param playerName 要查询的 Minecraft 玩家名。
     */
    public void bindgetKookIdByPlayerName(String playerName) {
        this.plugin.getServer().getScheduler().runTaskAsynchronously(this.plugin, () -> {
            long startTime = System.currentTimeMillis();
            this.plugin.getLogger().info("[LinkService] (Async) 开始根据玩家名查询 Kook ID: " + playerName);

            // 1. 根据玩家名获取 UUID
            UUID uuid = getPlayerUUIDByName(playerName);
            if (uuid == null) {
                // getPlayerUUIDByName 方法内部已经记录了具体的失败原因
                this.plugin.getLogger().warning("[LinkService] (Async) 无法为玩家 " + playerName + " 获取UUID，查询 Kook ID 操作中止。");
                logOperationDuration(startTime, "查询 Kook ID (Player: " + playerName + ", UUID获取失败)");
                return;
            }
            String playerUuidStr = uuid.toString();

            // 2. & 3. 根据 UUID 查询数据库获取 kook_id 并记录
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
                    this.plugin.getLogger().info("[LinkService] (Async) 玩家 " + playerName + " (UUID: " + playerUuidStr + ") 绑定的 Kook ID 为: " + kookIdResult);
                } else {
                    this.plugin.getLogger().info("[LinkService] (Async) 未找到玩家 " + playerName + " (UUID: " + playerUuidStr + ") 的 Kook ID 绑定记录。");
                }
            } catch (RuntimeException e) { // Catches RuntimeException from executeQuery's lambda or executeQuery itself
                this.plugin.getLogger().log(Level.SEVERE, "[LinkService] (Async) 根据玩家 " + playerName + " (UUID: " + playerUuidStr + ") 查询 Kook ID 时发生运行时异常: " + e.getMessage(), e);
            }

            logOperationDuration(startTime, "查询 Kook ID (Player: " + playerName + ")");
        });
    }

    public void bindgetPlayerNameByKookId(String kookId){
        LinkDetails linkDetails = getLinkDetailsByKookId(kookId);
        if (linkDetails != null) {
            this.plugin.getLogger().info("[LinkService] (Async) 找到 Kook ID: " + kookId + " 的有效绑定记录:" + linkDetails.playerName + "-" + linkDetails.playerUuid);

        }
        this.plugin.getLogger().info("[LinkService] (Async) 未找到 Kook ID: " + kookId + " 的有效绑定记录。");

    }
    /**
     * 根据 Minecraft 玩家名异步移除绑定关系。
     * 此方法会异步执行以下操作：
     * 1. 根据玩家名获取玩家 UUID (可能涉及网络请求)。
     * 2. 检查此 UUID 是否在数据库中有绑定记录。
     * 3. 如果存在绑定，则从数据库中删除该记录。
     * 4. 使缓存中对应的条目失效。
     * 所有操作均在异步线程中完成，结果和错误将记录到服务器日志。
     *
     * @param playerName 要移除绑定的 Minecraft 玩家名。
     */
    @Override
    public void bindremoveByplayerName(String playerName) { // 方法名建议遵循Java驼峰命名法，如 removeBindByPlayerName

        this.plugin.getServer().getScheduler().runTaskAsynchronously(this.plugin, () -> {
            long startTime = System.currentTimeMillis();

            // 1. 根据玩家名获取 UUID (这可能是一个阻塞操作)
            UUID uuid = getPlayerUUIDByName(playerName);
            if (uuid == null) {
                // getPlayerUUIDByName 方法内部已经记录了具体的失败原因
                this.plugin.getLogger().warning("[LinkService] (Async) 无法为玩家 " + playerName + " 获取UUID，解绑操作中止。");
                logOperationDuration(startTime, "解绑 (Player: " + playerName + ", UUID获取失败)"); // 确保记录耗时
                return; // 终止异步任务
            }
            String playerUuidStr = uuid.toString();

            // 2. 检查此 UUID 是否在数据库中有绑定记录，并获取数据库中存储的玩家名 (用于缓存键的准确性)
            String playerNameFromDb = getPlayerNameByUUIDInternal(playerUuidStr);

            if (playerNameFromDb == null) {
                plugin.getLogger().info("[LinkService] (Async) 玩家 " + playerName + " (UUID: " + playerUuidStr + ") 未在系统中绑定或绑定信息不一致，无需移除。");
                logOperationDuration(startTime, "解绑 (Player: " + playerName + ", 未找到绑定)"); // 确保记录耗时
                return; // 如果未找到绑定，则无需继续操作
            }

            // 3. 执行数据库删除操作
            String deleteSql = "DELETE FROM " + KOOK_USER_TABLE_NAME + " WHERE player_uuid = ?";
            int affectedRows = this.database.executeUpdate(deleteSql, null, Arrays.asList(playerUuidStr));

            if (affectedRows > 0) {
                plugin.getLogger().info("[LinkService] (Async) 成功移除了玩家 " + playerNameFromDb + " (UUID: " + playerUuidStr + ") 的绑定。影响行数: " + affectedRows);

                // 4. 如果删除成功，使缓存失效 (使用从数据库获取的 playerName，确保键的准确性)
                kookUserCache.invalidate(playerNameFromDb);
                plugin.getLogger().info("[LinkService] (Async) 已使玩家: " + playerNameFromDb + " (UUID: " + playerUuidStr + ") 的缓存失效。");
            } else {
                plugin.getLogger().warning("[LinkService] (Async) 尝试为玩家 " + playerNameFromDb + " (UUID: " + playerUuidStr + ") 解绑时，数据库中未删除任何行 (可能已被提前移除或UUID不匹配)。");
            }
            // 无论成功与否（除了早期返回），都记录整体操作耗时
            logOperationDuration(startTime, "解绑 (Player: " + playerName + ")");
        });
    }
    /**
     * 根据 Kook ID 异步移除绑定关系。
     * 此方法会异步执行以下操作：
     * 1. 根据 Kook ID 查询关联的玩家名和 UUID。
     * 2. 如果存在绑定，则从数据库中删除该记录。
     * 3. 使缓存中对应的玩家条目失效。
     * 所有操作均在异步线程中完成，结果和错误将记录到服务器日志。
     *
     * @param kookId 要移除绑定的 Kook ID。
     */
    @Override
    public void bindremoveByKookId(String kookId) {
        this.plugin.getServer().getScheduler().runTaskAsynchronously(this.plugin, () -> {
            long startTime = System.currentTimeMillis();
            this.plugin.getLogger().info("[LinkService] (Async) 开始尝试为 Kook ID 解绑: " + kookId);

            // 1. 根据 Kook ID 获取绑定的玩家名和 UUID
            LinkDetails linkDetails = getLinkDetailsByKookId(kookId);

            if (linkDetails == null || linkDetails.playerName == null) {
                // getLinkDetailsByKookId 方法内部已记录查询时的 SQL 异常
                // 如果 linkDetails 为 null 或者 playerName 为 null，说明未找到有效绑定
                this.plugin.getLogger().info("[LinkService] (Async) 未找到 Kook ID: " + kookId + " 的有效绑定记录，解绑操作中止。");
                logOperationDuration(startTime, "解绑 (Kook ID: " + kookId + ", 未找到绑定)");
                return; // 终止异步任务
            }

            String playerName = linkDetails.playerName;
            // String playerUuid = linkDetails.playerUuid; // playerUuid 可用于日志或进一步操作

            // 2. 执行数据库删除操作 (直接使用 kookId 进行删除)
            String deleteSql = "DELETE FROM " + KOOK_USER_TABLE_NAME + " WHERE kook_id = ?";
            int affectedRows = this.database.executeUpdate(deleteSql, null, Arrays.asList(kookId));

            if (affectedRows > 0) {
                this.plugin.getLogger().info("[LinkService] (Async) 成功移除了 Kook ID: " + kookId + " (关联玩家: " + playerName + ") 的绑定。影响行数: " + affectedRows);

                // 3. 如果删除成功，使缓存失效
                // 使用从数据库获取的 playerName 来确保缓存键的准确性
                kookUserCache.invalidate(playerName);
                this.plugin.getLogger().info("[LinkService] (Async) 已使玩家: " + playerName + " (原 Kook ID: " + kookId + ") 的缓存失效。");
            } else {
                // 如果 affectedRows 为 0，可能是在查询和删除之间记录已被其他操作移除
                this.plugin.getLogger().warning("[LinkService] (Async) 尝试为 Kook ID: " + kookId + " 解绑时，数据库中未删除任何行 (可能已被提前移除)。");
            }
            logOperationDuration(startTime, "解绑 (Kook ID: " + kookId + ")");
        });
    }

    // 辅助方法记录操作耗时 (可选, 但有助于保持日志格式一致)
    private void logOperationDuration(long startTime, String operationName) {
        long duration = System.currentTimeMillis() - startTime;
        this.plugin.getLogger().info(String.format("[LinkService] (Async) 操作 '%s' 完成。耗时 %d ms", operationName, duration));
    }


    @Override
    public boolean isLinked(String player) {

        // 当有缓存时，必定已经绑定过了
        if(kookUserCache.getIfPresent(player)!=null) return true;

        String sql = "select kook_id from "+KOOK_USER_TABLE_NAME+" where player = ?";

        return this.database.executeQuery(sql,null,Arrays.asList(player),resultSet -> {
            try {
                if(resultSet.next()){
                    kookUserCache.put(player,getLinkedKookUser(player));
                    return true;
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return false;
        });
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
