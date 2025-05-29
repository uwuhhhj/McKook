package com.meteor.mckook.storage.mapper;

import com.meteor.mckook.model.link.KookUser;

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
     * 绑定账号
     * @param player
     * @param kookUser
     */
    void link(String player,KookUser kookUser);

    boolean kookUserIsLinked(String kookId);

    void bind(String playerName, String kookId);
    void bindgetPlayerNameByKookId(String kookId);
    void bindgetKookIdByPlayerName(String playerName);
    /**
     * 根据 Minecraft 玩家名异步移除绑定关系。
     * 操作会尝试获取玩家UUID，然后从数据库删除记录并更新缓存。
     *
     * @param playerName 要移除绑定的 Minecraft 玩家名。
     */
    void bindremoveByplayerName(String playerName);
    /**
     * 根据 Kook ID 异步移除绑定关系。
     * 操作会尝试从数据库删除记录并更新缓存。
     *
     * @param kookId 要移除绑定的 Kook ID。
     */
    void bindremoveByKookId(String kookId); // 新添加的方法 (使用 ByKookId 后缀以明确区分)
}
