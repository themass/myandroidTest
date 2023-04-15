package com.openapi.ks.myapp.ui.inte;

import android.view.View;

public class FabOpListener {
    public interface OnFabListener {
        void setFabUpVisibility(int v);

        void setFabUpClickListener(View.OnClickListener l);
    }

    public interface SetFabListener {
        void setFabUpListener(OnFabListener l);
    }
}