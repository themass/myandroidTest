package chat.ui.adapter.media.holders;

import android.graphics.drawable.AnimationDrawable;
import android.media.MediaPlayer;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.core.view.ViewCompat;

import com.openai.commonutil.FormatUtils;
import com.openai.commonutil.LogUtil;
import com.openai.commonview.view.widget.MediaManager;
import com.openapi.ks.chat.R;
import com.openapi.ks.myapp.base.MyApplication;

import chat.ui.data.model.Message;
import tz.co.hosannahighertech.messagekit.messages.MessagesListStyle;
import tz.co.hosannahighertech.messagekit.utils.RoundedImageView;

/*
 * Created by troy379 on 05.04.17.
 */
public class MyOutComingImageMessageViewHolder
        extends MyOutComingMassageHandler {

    private Message message;
    protected ImageView image;
    protected View imageOverlay;

    public MyOutComingImageMessageViewHolder(View itemView, Object payload) {
        super(itemView, payload);
        image = (ImageView) itemView.findViewById(tz.co.hosannahighertech.messagekit.R.id.image);
        imageOverlay = itemView.findViewById(tz.co.hosannahighertech.messagekit.R.id.imageOverlay);

        if (image instanceof RoundedImageView) {
            ((RoundedImageView) image).setCorners(
                    tz.co.hosannahighertech.messagekit.R.dimen.message_bubble_corners_radius,
                    tz.co.hosannahighertech.messagekit.R.dimen.message_bubble_corners_radius,
                    0,
                    tz.co.hosannahighertech.messagekit.R.dimen.message_bubble_corners_radius
            );
        }
    }

    @Override
    public void onBind(Message message) {
        super.onBind(message);
        this.message = message;
        LogUtil.i("img url:"+message.getImageUrl());
        if (image != null && imageLoader != null) {
            imageLoader.loadImage(image, message.getImageUrl(), null);
        }

        if (imageOverlay != null) {
            imageOverlay.setSelected(isSelected());
        }
    }

}
