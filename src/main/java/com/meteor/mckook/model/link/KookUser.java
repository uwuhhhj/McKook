package com.meteor.mckook.model.link;

import com.meteor.mckook.reflect.orm.FieldListable;
import com.meteor.mckook.reflect.orm.FieldOrder;
import com.meteor.mckook.reflect.orm.ResultSetPopulatable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ResultSetPopulatable
public class KookUser implements FieldListable {
    // 绑定玩家KOOK身份 KOOK_id
    @FieldOrder(1)
    private String kook_id;
    //绑定玩家游戏的uuid
    @FieldOrder(2)
    private String player_uuid;
    // 绑定玩家名
    @FieldOrder(3)
    private String player;
    @FieldOrder(4)
    private String userName;
    @FieldOrder(5)
    private String nickName;
    @FieldOrder(6)
    private String identifyNum;
    @FieldOrder(7)
    private String avatar;
    // 是否为vip
    @FieldOrder(8)
    private boolean vip;
    // 是否为机器人
    @FieldOrder(9)
    private boolean bot;
    // 手机号码是否已验证
    @FieldOrder(10)
    private boolean mobileVerified;
    // 加入服务器(kook)时间
    @FieldOrder(11)
    private long joinedAt;

    // KookUser.java


}
