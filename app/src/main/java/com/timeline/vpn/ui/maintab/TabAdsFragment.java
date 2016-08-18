package com.timeline.vpn.ui.maintab;


import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.androidquery.AQuery;
import com.baidu.mobad.feeds.BaiduNative;
import com.baidu.mobad.feeds.NativeErrorCode;
import com.baidu.mobad.feeds.NativeResponse;
import com.baidu.mobad.feeds.RequestParameters;
import com.timeline.vpn.R;
import com.timeline.vpn.ads.banner.BannerAdsController;
import com.timeline.vpn.ads.banner.BannerProxy;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.constant.Constants;

import java.util.ArrayList;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;

/**
 * Created by gqli on 2015/9/1.
 */
public class TabAdsFragment extends TabBaseAdsFragment {
    @Bind(R.id.ll_ads_banner)
    LinearLayout llAdsBanner;
    @Bind(R.id.rv_ads)
    RecyclerView rvAds;
    List<NativeResponse> nrAdList = new ArrayList<NativeResponse>();
    AdsAdapter adsAdapter;
    BannerProxy proxy;
    BaiduNative baidu ;
    LayoutInflater inflater;
    protected Handler adsHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            LogUtil.i("handleMessage-" + msg.what);
            switch (msg.what){
                case Constants.ADS_NO_MSG:
                    break;
                case Constants.ADS_CLICK_MSG:
                    break;
                case Constants.ADS_PRESENT_MSG:
                    break;
                case Constants.ADS_DISMISS_MSG:
                    break;
                default:
                    break;
            }
        }
    };
    @Override
    protected int getTabBodyViewId() {
        return R.layout.tab_ads_content;
    }
    @Override
    protected int getTabHeaderViewId() {
        return R.layout.tab_ads_header;
    }
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        adsAdapter = new AdsAdapter();
        proxy = new BannerProxy(BannerProxy.DEAFULT_STRATEGY,getActivity(),adsHandler);
        inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }
    @Override
    protected void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        fabUp.setImageResource(R.drawable.fab_xiong);
        final StaggeredGridLayoutManager layoutManager = new StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.VERTICAL);
        rvAds.setLayoutManager(layoutManager);
        rvAds.setItemAnimator(new DefaultItemAnimator());
        baidu = new BaiduNative(getActivity(), Constants.BAIDU_LISTVIEWID, new BaiduNative.BaiduNativeNetworkListener() {
            @Override
            public void onNativeFail(NativeErrorCode arg0) {
                LogUtil.i("onNativeFail reason:" + arg0.name());
            }
            @Override
            public void onNativeLoad(List<NativeResponse> arg0) {
                LogUtil.i("onNativeLoad:" + arg0.size());
                if (arg0 != null && arg0.size() > 0) {
                    nrAdList = arg0;
                    showAdList();
                }
            }
        });
    }
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
//        fetchBannerAd();
        fetchAd();
    }

    private void fetchBannerAd(){
        for(int i=0;i<proxy.size();i++){
            ViewGroup view = (ViewGroup)inflater.inflate(R.layout.tab_ads_body_content_item, null);
            llAdsBanner.addView(view);
            BannerAdsController controller = proxy.getController(i, view );
            LogUtil.i("fetchBannerAd "+controller.toString());
            controller.showAds();
        }
    }
    public void fetchAd() {
        BaiduNative.setAppSid(getActivity(), Constants.BAIDU_APPID);
        RequestParameters requestParameters =
                new RequestParameters.Builder().keywords("游戏,职场,美女,旅游")
                        .confirmDownloading(true).build();
        baidu.makeRequest(requestParameters);
    }
    public void showAdList() {
        rvAds.setAdapter(adsAdapter);
        adsAdapter.notifyDataSetChanged();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        baidu.destroy();
        baidu = null;
    }

    private class AdsAdapter extends RecyclerView.Adapter<AdsItemViewHolder>{
        @Override
        public int getItemCount() {
            return nrAdList.size();
        }

        @Override
        public void onBindViewHolder(AdsItemViewHolder holder, int position) {
            NativeResponse nrAd = nrAdList.get(position);
            final int i = position;
            AQuery aq = new AQuery(holder.parent);
            aq.id(R.id.native_icon_image).image(nrAd.getIconUrl(), false, true);
            aq.id(R.id.native_main_image).image(nrAd.getImageUrl(), false, true);
            aq.id(R.id.native_text).text(nrAd.getDesc());
            aq.id(R.id.native_title).text(nrAd.getTitle());
            String text = nrAd.isDownloadApp() ? "下载" : "查看";
            aq.id(R.id.native_cta).text(text);
            nrAd.recordImpression(holder.parent);
            holder.parent.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    NativeResponse nrAd = nrAdList.get(i);
                    nrAd.handleClick(v);
                }
            });
        }

        @Override
        public AdsItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            final View view = LayoutInflater.from(getActivity()).inflate(R.layout.baidu_listads_native_row, parent, false);
            return new AdsItemViewHolder(view);
        }
    }
    static class AdsItemViewHolder extends RecyclerView.ViewHolder {
        @Bind(R.id.native_outer_view)
        RelativeLayout parent;
        @Bind(R.id.native_icon_image)
        ImageView icon;
        @Bind(R.id.native_main_image)
        ImageView main;
        @Bind(R.id.native_text)
        TextView text;
        @Bind(R.id.native_title)
        TextView title;
        @Bind(R.id.native_cta)
        TextView cta;
        public AdsItemViewHolder(View view) {
            super(view);
            ButterKnife.bind(this, view);
        }
    }
}
