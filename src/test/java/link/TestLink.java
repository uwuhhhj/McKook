package link;

import com.meteor.mckook.model.link.KookUser;

import java.util.List;

public class TestLink {
    public static void main(String[] args) {
        KookUser kookUser = new KookUser();
        kookUser.setKook_id("1");
        kookUser.setAvatar("avater");
        kookUser.setUserName("username");
        kookUser.setPlayer("playername");
        kookUser.setMobileVerified(false);
        kookUser.setJoinedAt(System.currentTimeMillis());

        List<Object> fieldList = kookUser.getFieldList();
        for (Object o : fieldList) {
            System.out.print(o+",");
        }
    }
}
