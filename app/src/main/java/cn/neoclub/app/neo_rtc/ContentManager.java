package cn.neoclub.app.neo_rtc;

import android.content.Context;
import android.content.SharedPreferences;

public class ContentManager {
    private static final String PREFERENCES_NAME = ".content";
    private static final String CONTENT_ROOMID = "roomId";
    private static final String CONTENT_CLIENTID = "clientId";

    public static final String WSS_URL = "wss://rtc.neoclub.cn";
    public static final String JOIN_URL = "https://rtc.neoclub.cn" + "/login/join";
    public static final String CREATE_URL = "https://rtc.neoclub.cn" + "/login/create";
    public static final String XIRSYS_URL = "https://service.xirsys.com/ice?ident=payonxp&secret=e6d7466a-2a01-11e6-84a1-a42b5b4d6e65&domain=rtc.neoclub.cn&application=neoclub&room=neoclub-room&secure=1";


    private static SharedPreferences preferences;

    public static void setContentRoomid(Context context, String roomId) {
        getSharedPreferences(context).edit()
                .putString(CONTENT_ROOMID, roomId)
                .apply();
    }

    public static void setContentClientid(Context context, String contentClientid) {
        getSharedPreferences(context).edit()
                .putString(CONTENT_CLIENTID, contentClientid)
                .apply();
    }

    public static void clear(Context context) {
        getSharedPreferences(context)
                .edit()
                .clear()
                .apply();
    }

    public static String getContentRoomid(Context context) {
        return getSharedPreferences(context).getString(CONTENT_ROOMID, "");
    }

    public static String getContentClientid(Context context) {
        return getSharedPreferences(context).getString(CONTENT_CLIENTID, "");
    }


    private static SharedPreferences getSharedPreferences(Context context) {
        if (preferences == null) {
            preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        }
        return preferences;
    }
}
