package chat.ui.adapter.media.holders;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.openapi.core.util.FileUtils;
import com.openapi.core.util.LogUtil;
import com.openapi.core.util.ToastUtil;
import com.openapi.ks.chat.R;
import com.openapi.ks.myapp.base.MyApplication;

import java.util.concurrent.atomic.AtomicInteger;

import chat.ui.adapter.media.FileTypeIconMapping;
import chat.ui.data.model.Message;
import tz.co.hosannahighertech.messagekit.utils.RoundedImageView;

/*
 * Created by troy379 on 05.04.17.
 */
public class MyOutComingFileViewHolder
        extends MyOutComingMassageHandler {

    protected ImageView image;
    protected TextView fileName;
    protected TextView minType;
    public static AtomicInteger counter= new AtomicInteger(0);

    public MyOutComingFileViewHolder(View itemView, Object payload) {
        super(itemView, payload);
        image = (ImageView) itemView.findViewById(tz.co.hosannahighertech.messagekit.R.id.image);
        fileName = itemView.findViewById(R.id.file_name);
        minType = itemView.findViewById(R.id.file_minetype);
//        imageOverlay = itemView.findViewById(tz.co.hosannahighertech.messagekit.R.id.imageOverlay);
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
//        imageLoader.loadImage(image, message.getImageUrl(), null);
        myOutcomingAudioFooterView.setVoiceVisible(false);
        String mineType = FileUtils.getExtensionFromFileName(message.getLocalFile().getFileName());
        fileName.setText(message.getLocalFile().getFileName());
        minType.setText(mineType.toUpperCase()+" , "+message.getLocalFile().getFileSize());
        image.setImageResource(FileTypeIconMapping.getIconByMimeType(mineType.toLowerCase()));
        bubble.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Uri uri = Uri.parse(message.getLocalFile().getPath());
                FileUtils.openUriWithSystem((Activity) v.getContext(), uri);
            }
        });
//        try {
//            if (rvAds != null && counter.get()<2) {
//                counter.addAndGet(1);
//                rvAds.setVisibility(View.VISIBLE);
//                AdsManager.getInstans().showBannerAds((FragmentActivity) messageUserAvatar.getContext(), rvAds, AdsContext.Categrey.CATEGREY_VPN2);
//            } else {
//                rvAds.removeAllViews();
//                rvAds.setVisibility(View.GONE);
//            }
//        }catch (Exception e){
//            LogUtil.e("",e);
//        }
    }

}
