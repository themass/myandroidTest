package com.qq.myapp.data.showtype;

import android.content.Context;

import com.romainpiel.shimmer.Shimmer;
import com.qq.common.util.LogUtil;
import com.qq.common.util.StringUtils;
import com.qq.myapp.adapter.IndexRecommendAdapter;
import com.qq.myapp.bean.vo.RecommendVo;
import com.qq.myapp.constant.Constants;

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
    public static void loadPhoto(final IndexRecommendAdapter.NaviItemViewHolder holder, RecommendVo vo, final Shimmer shimmer, boolean needShimmer, Context context){
        ShowTypeHandle handle = map.get(vo.showType);
        LogUtil.i(handle.toString()+" img="+vo.img);
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
