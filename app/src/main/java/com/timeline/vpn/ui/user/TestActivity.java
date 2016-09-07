package com.timeline.vpn.ui.user;

import android.os.Bundle;

import com.timeline.vpn.R;
import com.timeline.vpn.ui.base.LogActivity;
import com.timeline.vpn.ui.view.MyProgressDialog;

/**
 * Created by gqli on 2016/8/13.
 */
public class TestActivity extends LogActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.anim_heart_view);
    }

    @Override
    protected void onResume() {
        super.onResume();
        MyProgressDialog myProgressDialog = new MyProgressDialog(this);
//        myProgressDialog.setCancelable(false);
        myProgressDialog.show();
    }
}
