package com.openapi.myapp.ui.user;

import android.app.AlertDialog;
import android.content.Context;

import com.openapi.ks.free1.R;
import com.openapi.myapp.service.LogUploadService;

/**
 * 「上报问题」：用户主动选择后，才把诊断日志上传到服务器。
 * <p>
 * 合规要点（应用市场审核会看）：
 * <ol>
 *   <li><b>明示</b>：上传前逐一列出会收集什么、不会收集什么，而不是一句"帮助改进"。</li>
 *   <li><b>同意</b>：必须用户点"同意并上传"才发请求，没有默认勾选、没有后台静默上传。</li>
 *   <li><b>可撤回</b>：用户不上传，本地日志照常保留，不影响功能。</li>
 *   <li><b>最小化</b>：不含账号、UUID、密码、节点密钥、剪贴板、位置和手机号；
 *       设备标识由服务端从请求头取，客户端不单独打包。</li>
 *   <li>隐私政策入口就在弹窗里，可当场查看。</li>
 * </ol>
 */
public final class ReportIssueDialog {

    private ReportIssueDialog() {
    }

    /** 弹出上传确认。用户同意后才启动上传服务。 */
    public static void show(Context context) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.report_issue_title)
                .setMessage(R.string.report_issue_body)
                .setNeutralButton(R.string.report_issue_policy,
                        (d, w) -> SupportActivity.start(context, SupportActivity.PAGE_PRIVACY))
                .setPositiveButton(R.string.report_issue_agree, (d, w) -> upload(context))
                .setNegativeButton(R.string.report_issue_cancel, null)
                .show();
    }

    private static void upload(Context context) {
        Context app = context.getApplicationContext();
        app.startService(new android.content.Intent(app, LogUploadService.class));
        android.widget.Toast.makeText(app, R.string.report_issue_thanks,
                android.widget.Toast.LENGTH_SHORT).show();
    }
}
