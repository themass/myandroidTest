package com.qq.vpn.support.showtype;

import android.content.Context;

import com.qq.ext.util.LogUtil;
import com.qq.ext.util.StringUtils;
import com.romainpiel.shimmer.Shimmer;
import com.qq.vpn.adapter.RecommendAdapter;
import com.qq.vpn.domain.res.RecommendVo;
import com.qq.Constants;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by dengt on 2017/11/30.
 */

public class ShowTypeContext {
    public static Map<Integer,ShowTypeHandle> map = new HashMap<>();
    static {
        map.put(Constants.ShowType.Blur,new BlurShowTypeHandle());
        map.put(Constants.ShowType.Normal,new NormalShowTypeHandle());
        map.put(Constants.ShowType.Text,new TextShowTypeHandle());
        map.put(Constants.ShowType.Text_Below,new TextBelowShowTypeHandle());
    }
    public static void loadPhoto(final RecommendAdapter.RecommendItemViewHolder holder, RecommendVo vo, final Shimmer shimmer, boolean needShimmer, Context context){
        ShowTypeHandle handle = map.get(vo.showType);
        LogUtil.i(handle.toString());
        if(handle!=null){
            handle.showTitle(holder,vo,shimmer,needShimmer,context);
            if(StringUtils.hasText(vo.img)) {
                handle.loadPhoto(holder, vo, shimmer, needShimmer, context);
            }
//            if(StringUtils.hasText(vo.img) && NetUtils.isWifi(context))
//                handle.loadPhoto(holder,vo,shimmer,needShimmer,context);
        }


    }

}
