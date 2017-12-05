package com.timeline.sex.data.showtype;

import android.content.Context;

import com.romainpiel.shimmer.Shimmer;
import com.sspacee.common.util.LogUtil;
import com.sspacee.common.util.StringUtils;
import com.sspacee.yewu.net.NetUtils;
import com.timeline.sex.adapter.IndexRecommendAdapter;
import com.timeline.sex.bean.vo.RecommendVo;
import com.timeline.sex.constant.Constants;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by themass on 2017/11/30.
 */

public class ShowTypeContext {
    public static Map<Integer,ShowTypeHandle> map = new HashMap<>();
    static {
        map.put(Constants.ShowType.Blur,new BlurShowTypeHandle());
        map.put(Constants.ShowType.Normal,new NormalShowTypeHandle());
        map.put(Constants.ShowType.Text,new TextShowTypeHandle());
        map.put(Constants.ShowType.Text_Below,new TextBelowShowTypeHandle());
    }
    public static void loadPhoto(final IndexRecommendAdapter.NaviItemViewHolder holder, RecommendVo vo, final Shimmer shimmer, boolean needShimmer, Context context){
        ShowTypeHandle handle = map.get(vo.showType);
        LogUtil.i(handle);
        if(handle!=null){
            handle.showTitle(holder,vo,shimmer,needShimmer,context);
            if(StringUtils.hasText(vo.img) && NetUtils.isWifi(context))
                handle.loadPhoto(holder,vo,shimmer,needShimmer,context);
        }


    }

}
