package com.openapi.data.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.openapi.common.util.LogUtil;
import com.openapi.myapp.bean.form.UserNodeDelForm;
import com.openapi.myapp.bean.form.UserNodeSaveForm;
import com.openapi.myapp.bean.vo.UserNodeVo;
import com.openapi.myapp.constant.Constants;
import com.openapi.myapp.data.BaseService;
import com.openapi.myapp.data.UserLoginUtil;
import com.openapi.yewu.net.request.CommonResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 用户自定义节点的数据入口：服务端为准，本地只做缓存。
 * <ul>
 *   <li>已登录：增删改先调服务端，成功后才更新本地缓存；服务端失败则本地不变。</li>
 *   <li>未登录：只读本地缓存，增删改直接拒绝。</li>
 *   <li>登录后调用 {@link #pull(Context)} 整体覆盖本地缓存，换设备也能看到同样的节点。</li>
 * </ul>
 * 同步方法，必须在后台线程调用。
 */
public final class UserNodeRepository {

    private static final String TAG = "USER_NODE_TAG";
    private static final long TIMEOUT_SEC = 15;

    private UserNodeRepository() {
    }

    /** 当前是否可以访问服务端（已登录）。 */
    public static boolean isLoggedIn() {
        return UserLoginUtil.getUserCache() != null;
    }

    /** 从服务端拉取并覆盖本地缓存。失败返回 false，本地保持不变。 */
    public static boolean pull(@NonNull Context context) {
        if (!isLoggedIn()) {
            return false;
        }
        BaseService service = newService(context);
        final Object[] result = new Object[1];
        CountDownLatch latch = new CountDownLatch(1);
        service.getData(Constants.getUrl(Constants.API_NODE_LIST_URL),
                new CommonResponse.ResponseOkListener<UserNodeVo[]>() {
                    @Override
                    public void onResponse(UserNodeVo[] vo) {
                        super.onResponse(vo);
                        result[0] = vo;
                        latch.countDown();
                    }
                },
                failed(latch), TAG, UserNodeVo[].class);
        if (!await(latch)) {
            return false;
        }
        UserNodeVo[] remote = (UserNodeVo[]) result[0];
        if (remote == null) {
            return false;
        }
        List<UserNodeStore.UserNode> nodes = new ArrayList<>();
        for (UserNodeVo vo : remote) {
            nodes.add(toLocal(vo));
        }
        UserNodeStore.replaceAll(context, nodes);
        return true;
    }

    /**
     * 新增或修改。成功返回保存后的节点（已更新本地缓存）；失败返回 null，本地不变。
     *
     * @param id 为空表示新增
     */
    @Nullable
    public static UserNodeStore.UserNode save(@NonNull Context context, @Nullable String id,
                                              @Nullable String name, @NonNull String link) {
        if (!isLoggedIn()) {
            LogUtil.w(TAG + ": 未登录，拒绝保存节点");
            return null;
        }
        UserNodeSaveForm form = new UserNodeSaveForm();
        form.id = id;
        form.name = name;
        form.link = link;
        BaseService service = newService(context);
        final Object[] result = new Object[1];
        CountDownLatch latch = new CountDownLatch(1);
        service.postData(Constants.getUrl(Constants.API_NODE_SAVE_URL), form,
                new CommonResponse.ResponseOkListener<UserNodeVo>() {
                    @Override
                    public void onResponse(UserNodeVo vo) {
                        super.onResponse(vo);
                        result[0] = vo;
                        latch.countDown();
                    }
                },
                failed(latch), TAG, UserNodeVo.class);
        if (!await(latch) || result[0] == null) {
            return null;
        }
        UserNodeStore.UserNode saved = toLocal((UserNodeVo) result[0]);
        UserNodeStore.upsert(context, saved);
        return saved;
    }

    /** 删除。服务端成功后才删本地缓存。 */
    public static boolean delete(@NonNull Context context, @NonNull String id) {
        if (!isLoggedIn()) {
            return false;
        }
        UserNodeDelForm form = new UserNodeDelForm();
        form.id = id;
        BaseService service = newService(context);
        CountDownLatch latch = new CountDownLatch(1);
        final boolean[] ok = new boolean[1];
        service.postData(Constants.getUrl(Constants.API_NODE_DEL_URL), form,
                new CommonResponse.ResponseOkListener<Object>() {
                    @Override
                    public void onResponse(Object vo) {
                        super.onResponse(vo);
                        ok[0] = true;
                        latch.countDown();
                    }
                },
                failed(latch), TAG, Object.class);
        if (!await(latch) || !ok[0]) {
            return false;
        }
        UserNodeStore.remove(context, id);
        return true;
    }

    private static BaseService newService(Context context) {
        BaseService service = new BaseService();
        service.setup(context);
        return service;
    }

    private static CommonResponse.ResponseErrorListener failed(CountDownLatch latch) {
        return new CommonResponse.ResponseErrorListener() {
            @Override
            protected void onError() {
                super.onError();
                latch.countDown();
            }
        };
    }

    private static boolean await(CountDownLatch latch) {
        try {
            return latch.await(TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static UserNodeStore.UserNode toLocal(UserNodeVo vo) {
        UserNodeStore.UserNode node = new UserNodeStore.UserNode();
        node.id = vo.id;
        node.name = vo.name;
        node.protocol = vo.protocol;
        node.server = vo.server;
        node.port = vo.port;
        node.link = vo.link;
        node.outboundJson = UserNodeStore.outboundFromLink(vo.link);
        return node;
    }
}
