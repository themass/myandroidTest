package com.openapi.ks.myapp.ui.base;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.openapi.commons.common.ui.view.MyWebView;
import com.openapi.commons.common.util.AudioMngHelper;
import com.openapi.commons.common.util.LogUtil;
import com.openapi.commons.common.util.PackageUtils;
import com.openapi.commons.common.util.SystemUtils;
import com.openapi.commons.common.util.ToastUtil;
import com.openapi.commons.yewu.ads.base.AdsContext;
import com.openapi.commons.yewu.net.request.CommonResponse;
import com.openapi.ks.moviefree1.R;
import com.openapi.ks.myapp.bean.form.CustomeAddForm;
import com.openapi.ks.myapp.bean.vo.NullReturnVo;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.data.BaseService;
import com.openapi.ks.myapp.ui.base.app.BaseFragmentActivity;
import com.openapi.ks.myapp.ui.base.features.BaseWebViewFragment;

import java.util.HashMap;

/**
 * Created by openapi on 2016/3/17.
 */
public class WebViewActivity extends BaseFragmentActivity implements MyWebView.OnTouchRightSlide {
    private static final String TAG = "customeadd_tag";

    BaseWebViewFragment webViewFragment;
    private boolean adsNeed = false;
    private boolean adsPopNeed = false;
    private String url;
    BaseService baseService;
    private AudioMngHelper audioMngHelper;
    public static void startWebViewActivity(Context context, String url, String title, boolean adsShow, boolean adsPopShow, HashMap<String, Object> param) {
        Intent intent = new Intent(context, WebViewActivity.class);
        intent.putExtra(Constants.URL, url);
        intent.putExtra(Constants.ADS_SHOW_CONFIG, adsShow);
        intent.putExtra(Constants.ADS_POP_SHOW_CONFIG, adsPopShow);
        intent.putExtra(Constants.CONFIG_PARAM, param);
        intent.putExtra(Constants.TITLE, title);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.common_fragment);
        baseService = new BaseService();
        baseService.setup(this);
        webViewFragment = new BaseWebViewFragment();
        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragment, webViewFragment)
                .commit();
        String title = getIntent().getStringExtra(Constants.TITLE);
        url = getIntent().getStringExtra(Constants.URL);
        setToolbarTitle(title);
        adsDelayGone();
        adsNeed = getIntent().getBooleanExtra(Constants.ADS_SHOW_CONFIG, false);
        adsPopNeed = getIntent().getBooleanExtra(Constants.ADS_POP_SHOW_CONFIG, false);
        if(adsPopNeed){
            AdsContext.showRand(this);
        }
        audioMngHelper = new AudioMngHelper(this);
    }

    public boolean needShow() {
        return adsNeed;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        LogUtil.i("onKeyDown");
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (webViewFragment == null || !webViewFragment.goBack()) {
                finish();
                return true;
            }
        }else  if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            audioMngHelper.setVoiceStep100(1).subVoice100();

        }else  if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            audioMngHelper.setVoiceStep100(1).addVoice100();
        }
        return true;
    }

    @Override
    public void onTouchRight(int distans) {

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        LogUtil.i("onCreateOptionsMenu");
        getMenuInflater().inflate(R.menu.browser_menu, menu);
        return true;
    }
    CommonResponse.ResponseOkListener listener = new CommonResponse.ResponseOkListener<NullReturnVo>() {
        @Override
        public void onResponse(NullReturnVo vo) {
            ToastUtil.showShort(R.string.custome_ok);
        }
    };
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_browser) {
            if (PackageUtils.hasBrowser(this)) {
                Uri uri = Uri.parse(url);
                Intent it = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(it);
                finish();
            } else {
                ToastUtil.showShort(R.string.no_browser);
            }
            return true;
        } else if (id == R.id.menu_url) {
            SystemUtils.copy(this, url);
            return true;
        } else if (id == R.id.menu_favorite) {
            if(url.startsWith(Constants.HTTP_URL) ){
                CustomeAddForm form = new CustomeAddForm();
                form.title = getString(R.string.video);
                form.uri = url;
                Uri uri = Uri.parse(url);
                form.schema = uri.getScheme();
                form.uri = uri.getPath();
                form.url = url;
                baseService.postData(Constants.getUrl(Constants.API_ADD_CUSTOME), form, listener, new CommonResponse.ResponseErrorListener() {
                    @Override
                    protected void onError() {
                        super.onError();
                    }
                }, TAG, NullReturnVo.class);
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            getToolbar().setVisibility(View.GONE);
        } else {
            getToolbar().setVisibility(View.VISIBLE);
        }
    }
}
