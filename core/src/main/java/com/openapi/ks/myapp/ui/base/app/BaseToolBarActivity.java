package com.openapi.ks.myapp.ui.base.app;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.Service;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.openapi.core.util.LogUtil;
import com.openapi.core.R;
import com.openapi.core.databinding.CommonToolbarViewBinding;

import java.lang.reflect.Field;

/**
 * Created by Miroslaw Stanek on 19.01.15.
 *
 * 页面绑定约定（取代 butterknife）：
 * 1. 每一层基类在 [bindBaseViews] 里用 findViewById 绑定自己布局里的控件
 *    （这些外层布局标了 tools:viewBindingIgnore，不生成绑定类）。
 * 2. 叶子页面重写 [onBindContent]，在里面用自己布局的 ViewBinding 绑定内容区。
 *    参数是内容容器：布局根不是 &lt;merge&gt; 时内容是容器的最后一个子 View，
 *    用 [contentRoot] 取；根是 &lt;merge&gt; 时直接用容器本身。
 * 3. 绑定完成后才调用 [setupView]，所以 setupView 里可以放心用已绑定的字段。
 * 4. 页面销毁时调用 [unbindViews]，叶子页面在里面把绑定字段置空。
 */
public abstract class BaseToolBarActivity extends BaseToolbarMenuActivity {

    @Nullable
    Toolbar toolbar;
    TextView tvTitle;
    @Nullable
    ViewGroup flBody;

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(R.layout.base_toobar_activity);
        getLayoutInflater().inflate(layoutResID, (ViewGroup) findViewById(R.id.fl_body), true);
        bindViews();
        setupToolbar();
    }

    public void setupView() {

    }

    protected void bindViews() {
        bindBaseViews();
        onBindContent(contentContainer());
        setupView();
    }

    /** 绑定本层基类自己布局里的控件；子层基类重写时先调用 super。 */
    protected void bindBaseViews() {
        // common_toolbar_view 的根就是 Toolbar：先找到 include 进来的根，再用它的 Binding 取标题
        CommonToolbarViewBinding b = CommonToolbarViewBinding.bind(findViewById(R.id.toolbar));
        toolbar = b.toolbar;
        tvTitle = b.tvTitle;
        flBody = findViewById(R.id.fl_body);
    }

    /** 叶子页面内容布局被放进的那个容器。 */
    protected ViewGroup contentContainer() {
        return flBody;
    }

    /** 叶子页面在这里创建自己的 ViewBinding 并给字段赋值。默认什么都不做。 */
    protected void onBindContent(ViewGroup container) {
    }

    /** 容器里刚填充进去的内容布局的根（非 merge 布局用）。 */
    protected static View contentRoot(ViewGroup container) {
        return container.getChildAt(container.getChildCount() - 1);
    }

    /** 页面销毁时释放对控件的引用，叶子页面重写并置空自己的字段。 */
    protected void unbindViews() {
    }

    protected void setupToolbar() {
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }
    }

    public void setContentViewWithoutInject(int layoutResId) {
        super.setContentView(R.layout.base_toobar_activity);
        getLayoutInflater().inflate(layoutResId, (ViewGroup) findViewById(R.id.fl_body), true);
    }

    public Toolbar getToolbar() {
        return toolbar;
    }

    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        LogUtil.i("onConfigurationChanged->" + newConfig.orientation);
    }

    public View getNaviButton() {
        Field field = null;
        try {
            field = Toolbar.class.getDeclaredField("mNavButtonView");
            field.setAccessible(true);
            return (View) field.get(getToolbar());
        } catch (NoSuchFieldException e) {
            LogUtil.e(e);
        } catch (IllegalAccessException e) {
            LogUtil.e(e);
        }
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unbindViews();
    }

    public void startActivity(Class<? extends Activity> c) {
        Intent intent = new Intent(BaseToolBarActivity.this, c);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // 使用现代Activity过渡动画
            ActivityOptions options = ActivityOptions.makeCustomAnimation(this, 
                R.anim.slide_in_from_right, R.anim.slide_out_to_left);
            startActivity(intent, options.toBundle());
        } else {
            // 兼容旧版本
            startActivity(intent);
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.slide_in_from_right, R.anim.slide_out_to_left);
        }
    }

    public void startService(Class<? extends Service> c) {
        Intent intent = new Intent(BaseToolBarActivity.this, c);
        startService(intent);
    }

    public void stopService(Class<? extends Service> c) {
        Intent intent = new Intent(BaseToolBarActivity.this, c);
        stopService(intent);
    }

    public void showToolbar(boolean show) {
        if (show) {
            toolbar.setVisibility(View.VISIBLE);
        } else {
            toolbar.setVisibility(View.GONE);
        }
    }

    public void setToolbarTitle(int id, boolean initNav) {
        tvTitle.setText(id);
        if (initNav)
            setNavigationOut();
    }

    public void setToolbarTitle(String title, boolean initNav) {
        tvTitle.setText(title);
        if (initNav)
            setNavigationOut();
    }

    public TextView getToolbarTitle() {
        return tvTitle;
    }

    public void setToolbarTitle(String title) {
        tvTitle.setText(title);
        setNavigationOut();
    }

    public void setNavigationOut() {
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_white);
        getToolbar().setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }
}
