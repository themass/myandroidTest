package com.timeline.vpn.ui.inte;

import android.view.View;

public class FabOpListener {
    public interface OnFabListener{
        public void setFabUpVisibility(int v);
        public void setFabUpClickListener(View.OnClickListener l);
    }
    public interface SetFabListener{
        public void setFabUpListener(OnFabListener l);
    }
}