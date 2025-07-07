package com.openapi.ks.myapp.ui.base.app;

import androidx.annotation.Nullable;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Context;
import android.content.Intent;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.navigation.NavigationView;
import com.openai.CommonConstants;
import com.openai.commonevent.UserNeedLogin;
import com.openai.commonutil.EventBusUtil;
import com.openai.commonutil.PreferenceUtils;
import com.openai.commonutil.StringUtils;
import com.openai.commonutil.DateUtils;
import com.openai.commonutil.LogUtil;
import com.openai.commonutil.ShareUtil;
import com.openai.commonutil.SystemUtils;
import com.openai.commonutil.ToastUtil;
import com.openai.ads.yewu.ads.base.AdsManager;
import com.openai.ads.yewu.um.MobAgent;
import com.openai.commonview.view.DividerItemDecoration;
import com.openai.commonview.view.MyPullView;
import com.openapi.ks.chat.R;
import com.openapi.ks.chatfree.ui.main.MainFragmentViewPage;
import com.openapi.ks.myapp.adapter.AppRecommendViewAdapter;
import com.openapi.ks.myapp.adapter.ChatListViewAdapter;
import com.openapi.ks.myapp.adapter.base.BaseRecyclerViewAdapter;
import com.openapi.ks.myapp.base.MyApplication;
import com.openapi.ks.myapp.bean.form.ChatSessionLog;
import com.openapi.ks.myapp.bean.vo.AppInfo;
import com.openapi.ks.myapp.bean.vo.RecommendVo;
import com.openapi.ks.myapp.bean.vo.UserInfoVo;
import com.openapi.ks.myapp.constant.Constants;
import com.openai.net.BaseService;
import com.openapi.ks.myapp.data.DBManager;
import com.openapi.ks.myapp.data.UserLoginUtil;
import com.openapi.ks.myapp.data.VersionUpdater;
import com.openapi.ks.myapp.data.config.ChatSessionEvent;
import com.openapi.ks.myapp.data.config.LocationChooseEvent;
import com.openapi.ks.myapp.data.config.StateUseEvent;
import com.openapi.ks.myapp.data.config.UserLoginEvent;
import com.openapi.ks.myapp.data.config.VipDescEvent;
import com.openapi.ks.myapp.ui.base.CommonFragmentActivity;
import com.openapi.ks.myapp.ui.base.WebViewActivity;
import com.openapi.ks.myapp.ui.feedback.IWannaFragment;
import com.openapi.ks.myapp.ui.fragment.AppListFragment;
import com.openapi.ks.myapp.ui.fragment.ChatSessionFragment;
import com.openapi.ks.myapp.ui.fragment.DonationListFragment;
import com.openapi.ks.myapp.ui.fragment.FavoriteFragment;
import com.openapi.ks.myapp.ui.user.LoginActivity;
import com.openapi.ks.myapp.ui.user.SettingActivity;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import butterknife.BindView;

/**
 * Created by Miroslaw Stanek on 15.07.15.
 */
public class BaseDrawerActivity extends BaseToolBarActivity implements BaseRecyclerViewAdapter.OnRecyclerViewItemClickListener<ChatSessionLog>, MyPullView.OnRefreshListener {
    @Nullable
    @BindView(R.id.drawerLayout)
    public DrawerLayout drawerLayout;
    @Nullable
    @BindView(R.id.nv_drawer)
    NavigationView nvDrawer;
    View headerView;
    public BaseService baseService;
    @Nullable
    @BindView(R.id.my_pullview)
    MyPullView myPullView;
    private ChatListViewAdapter adapter;
    List<ChatSessionLog> dataList = new ArrayList<>();
    public void login(View view) {
        startActivity(LoginActivity.class);
    }

    @Override
    public void setContentView(int layoutResID) {
        LogUtil.i("setContentView   " + "drawer");
        super.setContentViewWithoutInject(R.layout.menu_drawer);
        ViewGroup viewGroup = (ViewGroup) findViewById(R.id.fl_content_root);
        LayoutInflater.from(this).inflate(layoutResID, viewGroup, true);
        bindViews();
        setupToolbar();
        headerView = nvDrawer.getHeaderView(0);
//        myPullView = headerView.findViewById(R.id.my_pullview);
        nvDrawer.setItemIconTintList(null);
        baseService = new BaseService();
        baseService.setup(this);
        initPullView();
    }
    protected void initPullView() {
        adapter = new ChatListViewAdapter(this, myPullView.getRecyclerView(), dataList, this);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        myPullView.setLayoutManager(layoutManager);
        DividerItemDecoration itemDecoration = new DividerItemDecoration(this, LinearLayoutManager.VERTICAL, com.openai.commonutil.R.drawable.divider_item);
        myPullView.setAdapter(adapter);
        myPullView.getRecyclerView().addItemDecoration(itemDecoration);
        myPullView.setListener(this);
    }
    public boolean closeDrawer() {
        if (drawerLayout.isDrawerOpen(Gravity.LEFT)) {
            drawerLayout.closeDrawer(Gravity.LEFT);
            return true;
        }
        return false;
    }

    @Override
    protected void setupToolbar() {
        super.setupToolbar();
        if (getToolbar() != null) {
            getToolbar().setNavigationIcon(R.drawable.ic_menu_drawer);
            getToolbar().setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        if (!closeDrawer()) {
                            drawerLayout.openDrawer(Gravity.LEFT);
                        }
                    } catch (Exception e) {
                        LogUtil.e(e);
                    }
                }
            });
        }
        drawerLayout.addDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {

            }

            @Override
            public void onDrawerOpened(View drawerView) {
                myPullView.setRefresh(true);
                LogUtil.i("load data");
            }

            @Override
            public void onDrawerClosed(View drawerView) {

            }

            @Override
            public void onDrawerStateChanged(int newState) {

            }
        });
        nvDrawer.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem item) {
                if (item.getItemId() == R.id.menu_test) {
                    Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    startActivityForResult(intent, 11);
//                    startActivity(WaveTest2.class);
//                    startActivity(MainActivity.class);
//                    startActivity(RippleActivity.class);
//                    startFragment();
//                    startActivity(HorizonActivity.class);
//                    startActivity(AudioWavesMainActivity.class);
//                    startActivity(AudioVisualizeViewMainActivity.class);
//                    startActivity(FireZenkMainActivity.class);
//                    startActivity(BuilderExampleActivity.class);
                }
                return false;
            }
        });
    }


    @Override
    protected void onPause() {
        super.onPause();
        MobAgent.onPauseForFragmentActiviy(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        MobAgent.onResumeForFragmentActiviy(this);
    }

    @Override
    public void onItemClick(View view, ChatSessionLog data, int position) {
        closeDrawer();
        clickMenuItem(data);
        LogUtil.i("click "+data.id);
    }
    public void clickMenuItem(ChatSessionLog data){
        ChatSessionEvent event = new ChatSessionEvent(data.getId(), data.getName());
        EventBusUtil.getEventBus().post(event);
    }
    @Override
    public void onRefresh(int type) {
        dataList.clear();
        dataList.addAll(DBManager.getInstance().getDaoSession().getChatSessionLogDao().loadAll());
        adapter.notifyDataSetChanged();
        myPullView.setRefresh(false);
    }

    @Override
    public boolean needLoad() {
        return true;
    }
}
