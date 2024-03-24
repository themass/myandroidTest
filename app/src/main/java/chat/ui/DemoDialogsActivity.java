package chat.ui;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.openapi.ks.myapp.ui.base.app.BaseFragmentActivity;
import com.rks.musicx.base.BaseActivity;

import chat.ui.data.model.Dialog;
import chat.ui.utils.AppUtils;
import tz.co.hosannahighertech.messagekit.commons.ImageLoader;
import tz.co.hosannahighertech.messagekit.dialogs.DialogsListAdapter;
/*
 * Created by troy379 on 05.04.17.
 */
public abstract class DemoDialogsActivity extends BaseFragmentActivity
        implements DialogsListAdapter.OnDialogClickListener<Dialog>,
        DialogsListAdapter.OnDialogLongClickListener<Dialog> {

    protected ImageLoader imageLoader;
    protected DialogsListAdapter<Dialog> dialogsAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        imageLoader = (imageView, url, payload) -> Glide.with(this).load(url).into(imageView);
    }
    @Override
    public void setupView() {
        super.setupView();
        showToolbar(false);
    }
    @Override
    public void onDialogLongClick(Dialog dialog) {
        AppUtils.showToast(
                this,
                dialog.getDialogName(),
                false);
    }
}
