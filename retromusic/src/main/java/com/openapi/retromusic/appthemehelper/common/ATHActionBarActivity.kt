package com.openapi.retromusic.appthemehelper.common

import androidx.appcompat.widget.Toolbar

import com.openapi.retromusic.appthemehelper.util.ToolbarContentTintHelper

class ATHActionBarActivity : ATHToolbarActivity() {

    override fun getATHToolbar(): Toolbar? {
        return ToolbarContentTintHelper.getSupportActionBarView(supportActionBar)
    }
}
