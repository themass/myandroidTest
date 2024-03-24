package chat.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.SearchView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.openapi.commons.common.ui.view.DividerItemDecoration;
import com.openapi.commons.common.ui.view.MyPullView;
import com.openapi.commons.common.util.LogUtil;
import com.openapi.commons.common.util.StringUtils;
import com.openapi.ks.moviefree1.R;
import com.openapi.ks.myapp.adapter.base.BaseRecyclerViewAdapter;
import com.openapi.ks.myapp.bean.vo.InfoListVo;
import com.openapi.ks.myapp.data.BaseService;
import com.openapi.ks.myapp.ui.base.features.LoadableFragment;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
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
        initPullView();
        mSearchView.setSubmitButtonEnabled(true);
        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            // 当点击搜索按钮时触发该方法
            @Override
            public boolean onQueryTextSubmit(String query) {
                LogUtil.i("搜"+query);
                keyword = query;
                pullView.setRefresh(true);
                mSearchView.clearFocus();
                return false;
            }

            // 当搜索内容改变时触发该方法
            @Override
            public boolean onQueryTextChange(String newText) {
                //没有内容时 在查询
                if (!StringUtils.hasText(newText) && StringUtils.hasText(keyword)) {
                    LogUtil.i("清空文字");
                    keyword = null;
//                    pullView.setRefresh(true);
//                    mSearchView.clearFocus();
                }
                return false;
            }
        });
        pullView.setRefresh(false);
        getActivity().getWindow().setSoftInputMode( WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
    }

    @Override
    public void onResume() {
        super.onResume();
        mSearchView.clearFocus();
    }

    protected void initPullView(){
//        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getActivity());
//        pullView.setLayoutManager(layoutManager);
//        DividerItemDecoration itemDecoration = new DividerItemDecoration(getActivity(), LinearLayoutManager.VERTICAL, R.drawable.divider_item);
//        pullView.getRecyclerView().addItemDecoration(itemDecoration);
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
        if (type == MyChatPullView.OnRefreshListener.FRESH)
            infoListVo.pageNum = 0;
        startQuery(false);
    }

    @Override
    public boolean needLoad() {
        return false;
    }

    protected abstract BaseRecyclerViewAdapter getAdapter();

}
