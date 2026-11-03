package com.openapi.myapp.ui.user

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openapi.common.util.SystemUtils
import com.openapi.ks.free1.R
import com.openapi.myapp.constant.Constants
import com.openapi.myapp.data.VersionUpdater
import com.openapi.myapp.service.LogUploadService
import com.openapi.myapp.ui.feedback.FeedbackChooseFragment
import com.openapi.myapp.ui.user.SupportActivity.Companion.PAGE_CONTACT
import com.openapi.myapp.ui.user.SupportActivity.Companion.PAGE_FEEDBACK
import com.openapi.myapp.ui.user.SupportActivity.Companion.PAGE_PRIVACY

/**
 * 关于、隐私、联系、反馈四个独立页面（同一个 Activity，用 PAGE 参数区分）。
 * 视觉与设置页保持一致：深色背景、卡片、紫色主按钮。
 */
class SupportActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PAGE = "page"
        const val PAGE_ABOUT = "about"
        const val PAGE_PRIVACY = "privacy"
        const val PAGE_CONTACT = "contact"
        const val PAGE_FEEDBACK = "feedback"

        @JvmStatic
        fun start(context: Context, page: String) {
            context.startActivity(Intent(context, SupportActivity::class.java).putExtra(EXTRA_PAGE, page))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = intent.getStringExtra(EXTRA_PAGE) ?: PAGE_ABOUT
        setContent {
            SupportScreen(
                page = page,
                onBack = { finish() },
                onOpenUrl = { url -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                onCheckUpdate = { VersionUpdater.checkUpdate(this, true) },
                onFeedback = { FeedbackChooseFragment.startFragment(this) },
                onUploadLogs = {
                    startService(Intent(this, LogUploadService::class.java))
                    Toast.makeText(this, R.string.menu_btn_report_log, Toast.LENGTH_SHORT).show()
                },
                onCopyEmail = {
                    SystemUtils.copy(this, Constants.SUPPORT_EMAIL)
                    Toast.makeText(this, R.string.menu_copy_emai, Toast.LENGTH_SHORT).show()
                },
                onSendEmail = {
                    startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Constants.SUPPORT_EMAIL)))
                },
            )
        }
    }
}

private val SupBg = Color(0xFF1C1B24)
private val SupCard = Color(0xFF292733)
private val SupText = Color(0xFFE8E8EE)
private val SupTextSecondary = Color(0xFFA7A4B5)
private val SupAccent = Color(0xFF6D4AFF)

@Composable
private fun SupportScreen(
    page: String,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onCheckUpdate: () -> Unit,
    onFeedback: () -> Unit,
    onUploadLogs: () -> Unit,
    onCopyEmail: () -> Unit,
    onSendEmail: () -> Unit,
) {
    val title = when (page) {
        PAGE_PRIVACY -> stringResource(R.string.privacy_policy)
        PAGE_CONTACT -> stringResource(R.string.support_contact)
        PAGE_FEEDBACK -> stringResource(R.string.support_feedback)
        else -> stringResource(R.string.support_about)
    }
    MaterialTheme(colorScheme = darkColorScheme(background = SupBg, surface = SupCard, primary = SupAccent)) {
        Column(modifier = Modifier.fillMaxSize().background(SupBg).padding(horizontal = 20.dp, vertical = 12.dp)) {
            TextButton(onClick = onBack) {
                Text(text = "←  " + stringResource(R.string.password_back), color = SupTextSecondary)
            }
            Text(text = title, color = SupText, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))

            when (page) {
                PAGE_PRIVACY -> {
                    Card {
                        Text(text = stringResource(R.string.support_privacy_summary), color = SupText, fontSize = 15.sp)
                    }
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton(stringResource(R.string.support_open_policy)) { onOpenUrl(Constants.PRIVACY_URL) }
                }
                PAGE_CONTACT -> {
                    Card {
                        Text(text = stringResource(R.string.support_contact_hint), color = SupTextSecondary, fontSize = 14.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(text = Constants.SUPPORT_EMAIL, color = SupText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton(stringResource(R.string.support_send_email)) { onSendEmail() }
                    Spacer(Modifier.height(10.dp))
                    OutlineButton(stringResource(R.string.support_copy_email)) { onCopyEmail() }
                }
                PAGE_FEEDBACK -> {
                    Card {
                        Text(text = stringResource(R.string.support_feedback_hint), color = SupTextSecondary, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton(stringResource(R.string.support_send_feedback)) { onFeedback() }
                    Spacer(Modifier.height(10.dp))
                    OutlineButton(stringResource(R.string.support_upload_logs)) { onUploadLogs() }
                }
                else -> {
                    Card {
                        Text(text = stringResource(R.string.support_about_desc), color = SupText, fontSize = 15.sp)
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = stringResource(R.string.menu_btn_version) + "  ", color = SupTextSecondary, fontSize = 14.sp)
                            Text(text = VersionUpdater.getVersion(), color = SupText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton(stringResource(R.string.support_visit_website)) { onOpenUrl(Constants.ABOUT) }
                    Spacer(Modifier.height(10.dp))
                    OutlineButton(stringResource(R.string.support_check_update)) { onCheckUpdate() }
                }
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SupCard)
            .padding(16.dp),
    ) {
        content()
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = SupAccent),
    ) {
        Text(text = text, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun OutlineButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = SupCard),
    ) {
        Text(text = text, color = SupText, fontWeight = FontWeight.Bold)
    }
}
