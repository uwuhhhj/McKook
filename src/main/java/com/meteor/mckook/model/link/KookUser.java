package com.meteor.mckook.model.link;

import com.meteor.mckook.reflect.orm.FieldListable;
import com.meteor.mckook.reflect.orm.FieldOrder;
import com.meteor.mckook.reflect.orm.ResultSetPopulatable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ResultSetPopulatable
public class KookUser implements FieldListable {
    //绑定玩家游戏的uuid
    @FieldOrder(1)
    private String player_uuid;
    // 绑定玩家KOOK身份 KOOK_id
    @FieldOrder(2)
    private String kook_id;
    // 绑定玩家名
    @FieldOrder(3)
    private String player;
    // KOOK名
    @FieldOrder(4)
    private String userName;
    // KOOKt头像网站
    @FieldOrder(5)
    private String avatar;
    // 手机号码是否已验证
    @FieldOrder(6)
    private boolean mobileVerified;
    // 加入服务器(kook)时间
    @FieldOrder(7)
    private long joinedAt;
    @FieldOrder(8)
    private String NickName;
    // KookUser.java
}
