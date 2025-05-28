package com.meteor.mckook.storage.mapper;

import com.meteor.mckook.model.link.KookUser;

import java.util.function.Consumer;

public interface LinkRepository{

    /**
     * 是否已绑定KOOK账户
     * @param player
     * @return
     */
    boolean isLinked(String player);

    /**
     * 获取绑定KOOK账户
     * @param player
     * @return
     */
    KookUser getLinkedKookUser(String player);

    /**
     * 绑定账号旧版，将被废弃或内部使用
     * @param player
     * @param kookUser
     */
    void link(String player,KookUser kookUser);

    boolean kookUserIsLinked(String kookId);

    /**
     * 异步地将 Minecraft 玩家名与 Kook ID 绑定。
     *
     * @param playerName Minecraft 玩家名。
     * @param kookId     要绑定的 Kook ID。
     * @param onSuccess  当成功绑定时调用此回调，参数为成功信息。
     * @param onFailure  当绑定失败时调用此回调，参数为错误或提示信息。
     */
    void bind(String playerName, String kookId, Consumer<String> onSuccess, Consumer<String> onFailure); // Modified signature
    /**
     * 根据 Kook ID 异步查询绑定关系中的玩家名。
     *
     * @param kookId 要查询的 Kook ID。
     * @param onSuccess 当成功找到玩家名时调用此回调，参数为玩家名。
     * @param onFailure 当未找到绑定或发生错误时调用此回调，参数为错误或提示信息。
     */
    void bindgetPlayerNameByKookId(String kookId, Consumer<String> onSuccess, Consumer<String> onFailure);
    /**
     * 根据 Minecraft 玩家名异步查询绑定关系中的 Kook ID。
     *
     * @param playerName 要查询的 Minecraft 玩家名。
     * @param onSuccess 当成功找到 Kook ID 时调用此回调，参数为 Kook ID。
     * @param onFailure 当未找到绑定或发生错误时调用此回调，参数为错误或提示信息。
     */
    void bindgetKookIdByPlayerName(String playerName, Consumer<String> onSuccess, Consumer<String> onFailure);
    /**
     * 根据 Minecraft 玩家名异步移除绑定关系。
     * 操作会尝试获取玩家UUID，然后从数据库删除记录并更新缓存。
     *
     * @param playerName 要移除绑定的 Minecraft 玩家名。
     * @param onSuccess  当成功移除绑定时调用此回调，参数为成功信息。
     * @param onFailure  当移除绑定失败或未找到绑定时调用此回调，参数为错误或提示信息。
     */
    void bindremoveByplayerName(String playerName, Consumer<String> onSuccess, Consumer<String> onFailure);
    /**
     * 根据 Kook ID 异步移除绑定关系。
     * 操作会尝试从数据库删除记录并更新缓存。
     *
     * @param kookId 要移除绑定的 Kook ID。
     * @param onSuccess 当成功移除绑定时调用此回调，参数为成功信息。
     * @param onFailure 当移除绑定失败或未找到绑定时调用此回调，参数为错误或提示信息。
     */
    void bindremoveByKookId(String kookId, Consumer<String> onSuccess, Consumer<String> onFailure);
}
