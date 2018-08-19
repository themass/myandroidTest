package com.qq.vpn.ui.base.actvity;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.View;

import com.qq.ext.util.LogUtil;
import com.qq.vpn.ui.base.actvity.BaseFragmentActivity;
import com.qq.vpn.ui.inte.FabOpListener;
import com.qq.vpn.ui.inte.OnBackKeyDownListener;
import com.qq.network.R;

import java.io.Serializable;

/**
 * Created by dengt on 2016/9/5.
 */
public class CommonFragmentActivity extends BaseFragmentActivity implements FabOpListener.OnFabListener {
    public static final String FRAGMENT = "FRAGMENT";
    public static final String TITLE = "TITLE";
    public static final String PARAM = "PARAM";
    private Boolean slidingClose = false;
    private Boolean toolbarShow = true;
    private Fragment fragment = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.common_fragment);
            disableScrollBanner();
        showToolbar(toolbarShow);
        Class f = (Class) getIntent().getSerializableExtra(FRAGMENT);
        setFabUpVisibility(View.VISIBLE);
        String title = null;
        Serializable name = getIntent().getSerializableExtra(TITLE);
        if (name instanceof String) {
            title = (String) name;
        } else if (name instanceof Integer) {
            title = getString((Integer) name);
        }
        try {
            fragment = (Fragment) f.newInstance();
            if (getIntent().getSerializableExtra(PARAM) != null) {
                Bundle bundle = new Bundle();
                bundle.putSerializable(PARAM, getIntent().getSerializableExtra(PARAM));
                fragment.setArguments(bundle);
            }
            if (fragment instanceof FabOpListener.SetFabListener) {
                ((FabOpListener.SetFabListener) fragment).setFabUpListener(this);
            }
        } catch (Exception e) {
            LogUtil.e(e);
        }
        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragment, fragment)
                .commitAllowingStateLoss();
        if (title != null) {
            setToolbarTitle(title, true);
        }
    }
    @Override
    protected boolean needLocationView() {
        return false;
    }
    @Override
    protected boolean enableSliding() {
        return slidingClose;
    }

    @Override
    public void onBackPressed() {
        if(fragment instanceof OnBackKeyDownListener){
            boolean ret = ((OnBackKeyDownListener)fragment).onkeyBackDown();
            if(!ret){
                super.onBackPressed();
            }
        }else{
            super.onBackPressed();
        }

    }
}
