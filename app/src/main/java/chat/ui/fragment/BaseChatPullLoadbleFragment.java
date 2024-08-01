package chat.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import com.openai.commonutil.LogUtil;
import com.openapi.ks.chat.R;
import com.openapi.ks.myapp.adapter.base.BaseRecyclerViewAdapter;
import com.openai.net.request.InfoListVo;
import com.openapi.ks.myapp.ui.base.features.LoadableFragment;


import butterknife.BindView;
import chat.ui.MyChatPullView;
import chat.ui.data.model.Message;


/**
 * Created by openapi on 2016/8/12.
 */
public abstract class BaseChatPullLoadbleFragment extends LoadableFragment<InfoListVo<Message>> implements MyChatPullView.OnRefreshListener, BaseRecyclerViewAdapter.OnRecyclerViewItemClickListener<Message> {
    @Nullable
    @BindView(R.id.my_pullview)
    public MyChatPullView pullView;
    @Nullable
    @BindView(R.id.video_full_container)
    public FrameLayout fullContainer;

    public InfoListVo<Message> infoListVo = new InfoListVo<>();
    @Override
    protected void onContentViewCreated(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        inflater.inflate(R.layout.activity_custom_chat_layout_messages, parent);
    }

    @Override
    public void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        mSearchView.setSubmitButtonEnabled(false);
        mSearchView.setVisibility(View.GONE);
        initPullView();
    }
    public void initPullView(){
        pullView.setListener(this);
    }
    @Override
    protected void onDataLoaded(InfoListVo<Message> data) {
        if (pullView != null) {
            if (data != null) {
                if (pullView.isLoadMore()) { //上拉加载
                    loadMoreData(data);
                } else { //下拉刷新 或者首次
                    freshData(data);
                }
                infoListVo.copy(data);
                initSort();
                sortData();
                data.voList.clear();
                data.voList.addAll(infoListVo.voList);
                setData(data);
                LogUtil.i("mData size=" + infoListVo.voList.size());
            }
            pullView.notifyDataSetChanged();
            mSearchView.clearFocus();
        }
    }
    protected void sortData() {}

    protected void initSort() {}
    protected  void loadMoreData(InfoListVo<Message> data){
        infoListVo.voList.addAll(data.voList);
    }
    protected  void freshData(InfoListVo<Message> data){
        infoListVo.voList.clear();
        infoListVo.voList.addAll(data.voList);
    }
    @Override
    public void onItemClick(View view, Message data, int postion) {
        pullView.notifyDataSetChanged();
    }

    @Override
    public void onRefresh(int type) {
        LogUtil.i("onRefresh");
        if (type == MyChatPullView.OnRefreshListener.FRESH){
            infoListVo.pageNum = 0;
            pullView.setRefresh(false);
        }

    }

    @Override
    public boolean needLoad() {
        return false;
    }

    protected abstract BaseRecyclerViewAdapter getAdapter();

}
