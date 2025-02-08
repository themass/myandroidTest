package chat.ui.adapter.media.holders;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import androidx.fragment.app.FragmentActivity;

import com.openai.ads.yewu.ads.base.AdsContext;
import com.openai.ads.yewu.ads.base.AdsManager;
import com.openai.commonutil.LogUtil;
import com.openapi.ks.myapp.ui.sound.MyFullScreenImageGalleryActivity;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import chat.ui.data.model.Message;
import tz.co.hosannahighertech.messagekit.utils.RoundedImageView;

/*
 * Created by troy379 on 05.04.17.
 */
public class MyOutComingImageMessageViewHolder
        extends MyOutComingMassageHandler {

    protected ImageView image;
    public static AtomicInteger counter= new AtomicInteger(0);

    public MyOutComingImageMessageViewHolder(View itemView, Object payload) {
        super(itemView, payload);
        image = (ImageView) itemView.findViewById(tz.co.hosannahighertech.messagekit.R.id.image);
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
        LogUtil.i("img url:"+message.getImageUrl());
        if (image != null && imageLoader != null) {
            imageLoader.loadImage(image, message.getImageUrl(), null);
        }
        image.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String url = "file://"+message.getImageUrl();
                LogUtil.i("打开图片"+url);
                ArrayList list = new ArrayList();
                list.add(url);
                Intent intent = new Intent(view.getContext(), MyFullScreenImageGalleryActivity.class);
                Bundle bundle = new Bundle();
                bundle.putStringArrayList(MyFullScreenImageGalleryActivity.KEY_IMAGES, list);
                bundle.putInt(MyFullScreenImageGalleryActivity.KEY_POSITION, 1);
                intent.putExtras(bundle);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                image.getContext().startActivity(intent);
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
