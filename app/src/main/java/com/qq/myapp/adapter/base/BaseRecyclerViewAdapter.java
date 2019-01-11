package com.qq.myapp.adapter.base;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;


import com.qq.kuaibo.R;

import java.util.ArrayList;
import java.util.List;

import butterknife.ButterKnife;

/**
 * Created by themass on 2016/8/13.
 */
public abstract class BaseRecyclerViewAdapter<T extends BaseRecyclerViewAdapter.BaseRecyclerViewHolder, V> extends RecyclerView.Adapter implements View.OnClickListener, View.OnLongClickListener {
    protected final Context context;
    protected RecyclerView mRecyclerView;
    protected OnRecyclerViewItemClickListener<V> listener;
    protected OnRecyclerViewItemLongClickListener<V> longClickListener;
    protected List<V> data;
    private int selected = -1;
    //HeaderView, FooterView
    private View mHeaderView;
    private View mFooterView;

    public static final int TYPE_HEADER = 0;  //说明是带有Header的
    public static final int TYPE_FOOTER = 1;  //说明是带有Footer的
    public static final int TYPE_NORMAL = 2;  //说明是不带有header和footer的

    public BaseRecyclerViewAdapter(Context context, RecyclerView recyclerView, List<V> data, OnRecyclerViewItemClickListener<V> listener) {
        this.context = context;
        this.mRecyclerView = recyclerView;
        this.listener = listener;
        if (data != null) {
            this.data = data;
        } else {
            this.data = new ArrayList<>();
        }
    }
    public View getHeaderView() {
        return mHeaderView;
    }
    public void setHeaderView(View headerView) {
        mHeaderView = headerView;
        notifyItemInserted(0);
    }
    public View getFooterView() {
        return mFooterView;
    }
    public void setFooterView(View footerView) {
        mFooterView = footerView;
        notifyItemInserted(getItemCount()-1);
    }
    @Override
    public int getItemViewType(int position) {
        if (mHeaderView == null && mFooterView == null){
            return TYPE_NORMAL;
        }
        if (position == 0 && mHeaderView!=null){
            //第一个item应该加载Header
            return TYPE_HEADER;
        }
        if (position == getItemCount()-1 && mFooterView!=null){
            //最后一个,应该加载Footer
            return TYPE_FOOTER;
        }
        return TYPE_NORMAL;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if(mHeaderView != null && viewType == TYPE_HEADER) {
            return new HeadFooterWarper(mHeaderView);
        }
        if(mFooterView != null && viewType == TYPE_FOOTER){
            return new HeadFooterWarper(mFooterView);
        }
        return onCreateViewHolderData(parent,viewType);
    }
    //返回View中Item的个数，这个时候，总的个数应该是ListView中Item的个数加上HeaderView和FooterView
    @Override
    public int getItemCount() {
        if(mHeaderView == null && mFooterView == null){
            return data.size();
        }else if(mHeaderView == null && mFooterView != null){
            return data.size() + 1;
        }else if (mHeaderView != null && mFooterView == null){
            return data.size() + 1;
        }else {
            return data.size() + 2;
        }
    }
    protected abstract RecyclerView.ViewHolder onCreateViewHolderData(ViewGroup parent, int viewType);
    protected abstract void onBindViewHolderData(RecyclerView.ViewHolder holder, int position);
    protected void onBindHeadViewHolderData(RecyclerView.ViewHolder holder){}
    protected void onBindFooterViewHolderData(RecyclerView.ViewHolder holder){}
    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        BaseRecyclerViewAdapter.BaseRecyclerViewHolder h = (BaseRecyclerViewAdapter.BaseRecyclerViewHolder)holder;
        int div = mHeaderView!=null?1:0;
        if(h instanceof BaseRecyclerViewAdapter.HeadFooterWarper ){
            if(position==0){
                onBindHeadViewHolderData(holder);
            }else{
                onBindFooterViewHolderData(holder);
            }
        }else{
            h.setData(position-div, data.get(position-div));
            onBindViewHolderData(holder,position-div);
        }

    }

    public void setLongClickListener(OnRecyclerViewItemLongClickListener<V> listener) {
        longClickListener = listener;
    }

    @Override
    public void onClick(View v) {
        selected = (int) v.getTag(R.id.tag_pos);
        if (listener != null)
            listener.onItemClick(v, (V) v.getTag(R.id.tag_val), (int) v.getTag(R.id.tag_pos));
    }

    @Override
    public boolean onLongClick(View v) {
        if (longClickListener != null) {
            longClickListener.onItemLongClick(v, (V) v.getTag(R.id.tag_val), (int) v.getTag(R.id.tag_pos));
            return true;
        }
        return false;
    }

    public int getSelected() {
        return selected;
    }

    public void setSelected(int selected) {
        this.selected = selected;
    }

    public interface OnRecyclerViewItemClickListener<T> {
        void onItemClick(View view, T data, int position);
    }

    public interface OnRecyclerViewItemLongClickListener<T> {
        void onItemLongClick(View view, T data, int position);
    }

    public static abstract class BaseRecyclerViewHolder<V> extends RecyclerView.ViewHolder {
        private View v;

        public BaseRecyclerViewHolder(View itemView, View.OnClickListener l, View.OnLongClickListener longListener) {
            super(itemView);
            ButterKnife.bind(this, itemView);
            v = itemView;
            if(l!=null)
                v.setOnClickListener(l);
            if (longListener != null)
                v.setOnLongClickListener(longListener);
        }

        public void setData(int pos, V val) {
            v.setTag(R.id.tag_pos, pos);
            v.setTag(R.id.tag_val, val);
        }
    }
    public class HeadFooterWarper extends BaseRecyclerViewHolder{
        public HeadFooterWarper(View itemView) {
            super(itemView,null,null);
        }
    }
}

