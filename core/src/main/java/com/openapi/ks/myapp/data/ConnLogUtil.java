package com.openapi.ks.myapp.data;

import android.content.Context;

import com.openapi.common.util.android.Background;
import com.openapi.common.util.lang.CollectionUtils;
import com.openapi.common.util.android.StaticDataUtil;
import com.openapi.common.net.BaseService;
import com.openapi.common.util.lang.DateUtils;
import com.openapi.common.util.json.GsonUtils;
import com.openapi.common.util.log.LogUtil;
import com.openapi.common.util.lang.StringUtils;
import com.openapi.common.net.NetUtils;
import com.openapi.core.model.ConnLog;
import com.openapi.core.model.ConnLogForm;
import com.openapi.core.model.NullReturnVo;
import com.openapi.core.config.Constants;
import com.openapi.core.db.AppDatabase;

import java.util.Date;
import java.util.List;

public class ConnLogUtil {

    /** 把本地连接日志上报给服务端，上报前先清空本地表（与旧逻辑一致）。 */
    public static void sendAllLog(Context context) {
        BaseService indexService = new BaseService();
        indexService.setup(context);
        Background.serial(() -> {
            try {
                List<ConnLog> logs = AppDatabase.getInstance().connLogDao().loadAll();
                AppDatabase.getInstance().connLogDao().deleteAll();
                if (!CollectionUtils.isEmpty(logs)) {
                    ConnLogForm form = new ConnLogForm();
                    form.log = GsonUtils.getInstance().toJson(logs);
                    indexService.postData(Constants.getUrl(Constants.API_CONNLOG_URL), form, null, null, "tag", NullReturnVo.class);
                }
            } catch (Throwable e) {
                LogUtil.e(e);
            }
        });
    }
}
