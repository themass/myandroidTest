package com.qq.vpn.support;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.qq.ext.util.DateUtils;
import com.qq.ext.util.PackageUtils;
import com.qq.Constants;
import com.qq.ext.util.SystemUtils;
import com.qq.vpn.main.ui.WebViewActivity;
import com.qq.network.R;

import java.util.Date;

public class MyUrlUtil {
    public static void showAbout(Context context){
        String url =Constants.ABOUT;
        url = url + "?" + DateUtils.format(new Date(), DateUtils.DATE_FORMAT_MM);
        if (PackageUtils.hasBrowser(context)) {
            Uri uri = Uri.parse(url);
            Intent it = new Intent(Intent.ACTION_VIEW, uri);
            context.startActivity(it);
        } else {
            WebViewActivity.startWebViewActivity(context, url, context.getString(R.string.menu_btn_about), false, false, null);
        }
    }
}
