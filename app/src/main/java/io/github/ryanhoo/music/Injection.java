package io.github.ryanhoo.music;

import android.content.Context;

import com.openapi.ks.myapp.base.MyApplication;

/**
 * Created with Android Studio.
 * User: ryan.hoo.j@gmail.com
 * Date: 9/10/16
 * Time: 4:11 PM
 * Desc: Injection
 */
public class Injection {

    public static Context provideContext() {
        return MyApplication.getInstance();
    }
}
