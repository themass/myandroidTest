package com.openapi.feature.content.common;

import android.content.Context;

// 使用现代 Shimmer 替代
// import com.romainpiel.shimmer.Shimmer;
import com.openapi.common.util.log.LogUtil;
import com.openapi.common.util.lang.StringUtils;
import com.openapi.feature.content.common.IndexRecommendAdapter;
import com.openapi.core.model.RecommendVo;
import com.openapi.core.config.Constants;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by openapi on 2017/11/30.
 */

public class ShowTypeContext {
    public static Map<Integer, ShowTypeHandle> map = new HashMap<>();

    static {
        map.put(Constants.ShowType.Blur, new BlurShowTypeHandle());
        map.put(Constants.ShowType.Normal, new NormalShowTypeHandle());
        map.put(Constants.ShowType.Text, new TextShowTypeHandle());
        map.put(Constants.ShowType.Text_Below, new TextBelowShowTypeHandle());
    }

    public static void loadPhoto(final IndexRecommendAdapter.NaviItemViewHolder holder, RecommendVo vo, final Object shimmer, boolean needShimmer, Context context) {
        ShowTypeHandle handle = map.get(vo.showType);
        LogUtil.i(handle);
        if (handle != null) {
            handle.showTitle(holder, vo, shimmer, needShimmer, context);
            if (StringUtils.hasText(vo.img)) {
                handle.loadPhoto(holder, vo, shimmer, needShimmer, context);
            }
//            if(StringUtils.hasText(vo.img) && NetUtils.isWifi(context))
//                handle.loadPhoto(holder,vo,shimmer,needShimmer,context);
        }


    }

}
