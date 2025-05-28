package com.meteor.mckook.kook.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meteor.mckook.McKook;
import com.meteor.mckook.kook.KookBot;
import com.meteor.mckook.kook.KookService;
import com.meteor.mckook.model.link.KookUser;
import com.meteor.mckook.model.link.LinkCache;
import com.meteor.mckook.storage.DataManager;
import com.meteor.mckook.storage.mapper.LinkRepository;
import com.meteor.mckook.util.VerificationCodeGenerator;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import snw.jkook.entity.User;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * KOOK账户绑定
 */
public class LinkService implements KookService {
    private McKook plugin;
    private KookBot kookBot;

    public LinkRepository linkRepository;

    /**
     * 验证码缓存
     */
    private Cache<String, LinkCache> linkCacheCache;

    public LinkService(KookBot kookBot){

        this.kookBot = kookBot;

        this.linkCacheCache = Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES) // 验证码有效期为5分钟
                .maximumSize(500)
                .build();

        this.linkRepository = DataManager.instance.getMapper(LinkRepository.class);
    }


    public boolean isLinked(String player){
        return linkRepository.isLinked(player);
    }

    public String buildVerifyCode(String player){
        String verifyCode = VerificationCodeGenerator.generateVerificationCode();
        linkCacheCache.put(verifyCode,new LinkCache(player));
        return verifyCode;
    }

    public KookUser getLinkUser(String player){
        return linkRepository.getLinkedKookUser(player);
    }

    public KookUser link(String player,User user){
        KookUser kookUser = new KookUser();
        kookUser.setPlayer_uuid(setPlayerUuid(player));
        kookUser.setKook_id(user.getId());
        kookUser.setPlayer(player);
        kookUser.setUserName(user.getName());
        kookUser.setAvatar(user.getAvatarUrl(false));
        kookUser.setMobileVerified(true);
        kookUser.setJoinedAt(System.currentTimeMillis());
        kookUser.setNickName(player);
        linkRepository.link(player,kookUser);
        return kookUser;
    }

    private String setPlayerUuid(String player) {
        if (Bukkit.getPlayerExact(player) == null) {
            //说明玩家不在线
            try {
                // Bukkit.getOfflinePlayer 可能会执行阻塞的网络调用。
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(player);
                UUID uuid = offlinePlayer.getUniqueId();

                if (uuid == null) {
                    // 这种情况对于有效的 playerName 来说比较罕见，但最好处理。
                    this.plugin.getLogger().warning("[LinkService] 无法获取玩家 " + player + " 的UUID。该玩家可能从未在此服务器或以该名称登录。");
                    return null;
                }
                return uuid.toString();
            } catch (Exception e) {
                // 捕获泛型 Exception 可能隐藏具体问题，但在此处作为基本错误处理。
                this.plugin.getLogger().log(Level.WARNING, "[LinkService] 获取玩家 " + player + " 的UUID时发生异常: " + e.getMessage(), e);
                return null;
            }
        }
        return Objects.requireNonNull(Bukkit.getPlayerExact(player)).getUniqueId().toString();
    }

    public LinkCache getLinkCache(String verifyCode){
        return linkCacheCache.getIfPresent(verifyCode);
    }

    public boolean kookUserIsLinked(String kookId){
        return linkRepository.kookUserIsLinked(kookId);

    }


}
