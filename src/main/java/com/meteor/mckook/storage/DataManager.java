package com.meteor.mckook.storage;

import com.meteor.mckook.McKook;
import com.meteor.mckook.storage.database.SqliteDatabase;
import com.meteor.mckook.storage.mapper.BaseMapper;
import com.meteor.mckook.storage.mapper.LinkRepository;
import com.meteor.mckook.storage.mapper.impl.LinkRepositoryImpl;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class DataManager {

    public static DataManager instance;

    private McKook plugin;

    private AbstractDatabase abstractDatabase;

    private Map<Class<?>, BaseMapper> baseMapperMap;

    private DataManager(McKook plugin){
        this.plugin = plugin;
        this.abstractDatabase = new SqliteDatabase(plugin);

        try {
            this.abstractDatabase.connect();
            plugin.getLogger().info("已连接数据库");
        } catch (SQLException e) {
            e.printStackTrace();
            plugin.getLogger().info("数据库连接失败");
        }

        this.baseMapperMap = new HashMap<>();

        baseMapperMap.put(LinkRepository.class, new LinkRepositoryImpl(this.plugin, abstractDatabase));
    }

    public void close(){
        try {
            if (abstractDatabase != null && abstractDatabase.isConnected()) { // 增加 null 和连接状态检查
                abstractDatabase.disconnect();
                plugin.getLogger().info("数据库已成功断开连接。");
            }
        } catch (SQLException e) {
            // 记录错误，而不是直接抛出 RuntimeException，这可能会导致服务器崩溃
            plugin.getLogger().log(Level.SEVERE, "断开数据库连接时发生错误。", e);
            // throw new RuntimeException(e); // 避免在插件核心逻辑中随意抛出未检查异常
        }
    }

    public static void init(McKook plugin) {
        if (instance == null) { // Basic check to prevent re-initialization if called multiple times
            instance = new DataManager(plugin);
        }
    }
    public static DataManager getInstance() {
        return instance;
    }

    public <T> T getMapper(Class<T> mapper) {
        return mapper.cast(baseMapperMap.get(mapper));
    }

    public LinkRepository getLinkRepository() {
        return getMapper(LinkRepository.class);
    }
}
