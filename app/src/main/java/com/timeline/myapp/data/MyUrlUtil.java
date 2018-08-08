package com.timeline.myapp.data;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.sspacee.common.util.DateUtils;
import com.sspacee.common.util.PackageUtils;
import com.sspacee.common.util.StringUtils;
import com.sspacee.common.util.SystemUtils;
import com.sspacee.common.util.ToastUtil;
import com.sspacee.yewu.um.MobAgent;
import com.timeline.myapp.bean.vo.VersionVo;
import com.timeline.myapp.constant.Constants;
import com.timeline.myapp.ui.base.WebViewActivity;
import com.timeline.nettypea.R;

import java.util.Date;

public class MyUrlUtil {
    public static void showAbout(Context context){
        VersionVo vo = StaticDataUtil.get(Constants.VERSION_INFO,VersionVo.class);
        String url =Constants.ABOUT;
        if (SystemUtils.isZH(context)) {
            url = Constants.ABOUT_ZH;
        }
        if(vo!=null && StringUtils.hasText(vo.aboutUrl)){
            url = vo.aboutUrl;
        }
        url = url + "?" + DateUtils.format(new Date(), DateUtils.DATE_FORMAT_MM);
        if (PackageUtils.hasBrowser(context)) {
            Uri uri = Uri.parse(url);
            Intent it = new Intent(Intent.ACTION_VIEW, uri);
            context.startActivity(it);
        } else {
            WebViewActivity.startWebViewActivity(context, url, context.getString(R.string.menu_btn_about), false, false, null);
        }
    }
    public static void showOfficeNet(Context context){
        VersionVo vo = StaticDataUtil.get(Constants.VERSION_INFO,VersionVo.class);
        String url = Constants.OFFICIALNET;
        if(vo!=null && StringUtils.hasText(vo.officeNet)){
            url = vo.officeNet;
        }
        if (PackageUtils.hasBrowser(context)) {
            Uri uri = Uri.parse(url);
            Intent it = new Intent(Intent.ACTION_VIEW, uri);
            context.startActivity(it);
        } else {
            ToastUtil.showShort(R.string.no_browser);
        }

    }
}
