package com.openapi.ks.myapp.ui.base;

import static io.vov.vitamio.utils.Log.TAG;

import android.annotation.SuppressLint;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.HttpAuthHandler;
import android.webkit.SslErrorHandler;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

import com.openapi.commons.common.util.LogUtil;
import com.openapi.ks.moviefree1.R;
import com.openapi.ks.myapp.constant.Constants;

public class Testwebview extends AppCompatActivity {

        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.test_webview);
            //获得控件
            @SuppressLint({"MissingInflatedId", "LocalSuppress"}) WebView webView = (WebView) findViewById(R.id.wv_webview);
            //访问网页
            String url = getIntent().getStringExtra(Constants.URL);
            LogUtil.i(url);

            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDatabaseEnabled(true);
            settings.setSaveFormData(true);
            settings.setBuiltInZoomControls(true);
            settings.setSupportZoom(true); // 可以缩放
            settings.setUseWideViewPort(true);
            settings.setAllowFileAccess(true);
            settings.setLoadWithOverviewMode(true);
            settings.setDomStorageEnabled(true);
            settings.setJavaScriptCanOpenWindowsAutomatically(true);
//        settings.setAppCacheEnabled(true);
            settings.setGeolocationEnabled(true);

            settings.setJavaScriptEnabled(true);

//支持插件

//设置自适应屏幕，两者合用
            settings.setUseWideViewPort(true); //将图片调整到适合webview的大小
            settings.setLoadWithOverviewMode(true); // 缩放至屏幕的大小

//缩放操作
            settings.setSupportZoom(true); //支持缩放，默认为true。是下面那个的前提。
            settings.setBuiltInZoomControls(true); //设置内置的缩放控件。若为false，则该WebView不可缩放
            settings.setDisplayZoomControls(false); //隐藏原生的缩放控件

//其他细节操作
            settings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK); //关闭webview中缓存
            settings.setAllowFileAccess(true); //设置可以访问文件
            settings.setJavaScriptCanOpenWindowsAutomatically(true); //支持通过JS打开新窗口
            settings.setLoadsImagesAutomatically(true); //支持自动加载图片

            //系统默认会通过手机浏览器打开网页，为了能够直接通过WebView显示网页，则必须设置
            webView.setWebViewClient(new WebViewClient(){
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    //使用WebView加载显示url
                    view.loadUrl(url);
                    //返回true
                    return true;
                }
                @Override
                public void onReceivedError(WebView view, int errorCode,
                                            String description, String failingUrl) {
                    // TODO Auto-generated method stub
                    super.onReceivedError(view, errorCode, description, failingUrl);
                    view.loadUrl("file:///android_asset/error.html");
                    Log.i(TAG, "onReceivedError");
                }

                /**
                 * 如果浏览器需要重新发送POST请求，可以通过这个时机来处理。默认是不重新发送数据。 参数说明
                 *
                 * @param view
                 *            接收WebViewClient的webview
                 * @param dontResend
                 *            浏览器不需要重新发送的参数
                 * @param resend
                 *            浏览器需要重新发送的参数
                 */
                @Override
                public void onFormResubmission(WebView view, Message dontResend,
                                               Message resend) {
                    // TODO Auto-generated method stub
                    super.onFormResubmission(view, dontResend, resend);
                    Log.i(TAG, "onFormResubmission");
                }

                /**
                 * doUpdateVisitedHistory
                 * 通知应用程序可以将当前的url存储在数据库中，意味着当前的访问url已经生效并被记录在内核当中。这个函数在网页加载过程中只会被调用一次。
                 * 注意网页前进后退并不会回调这个函数。
                 *
                 * 参数说明：
                 *
                 * @param view
                 *            接收WebViewClient的那个实例，前面看到webView.setWebViewClient(new
                 *            MyAndroidWebViewClient())，即是这个webview。
                 * @param url
                 *            当前正在访问的url
                 * @param isReload
                 *            如果是true 这个是正在被reload的url
                 */
                @Override
                public void doUpdateVisitedHistory(WebView view, String url,
                                                   boolean isReload) {
                    // TODO Auto-generated method stub
                    super.doUpdateVisitedHistory(view, url, isReload);
                    Log.i(TAG, "doUpdateVisitedHistory");
                }

                /**
                 * 当网页加载资源过程中发现SSL错误会调用此方法。我们应用程序必须做出响应，是取消请求handler.cancel(),还是继续请求handler.
                 * proceed();内核的默认行为是handler.cancel();
                 *
                 * 参数说明：
                 *
                 * @param view
                 *            接收WebViewClient的那个实例，前面看到webView.setWebViewClient(new
                 *            MyAndroidWebViewClient())，即是这个webview。
                 * @param handler
                 *            处理用户请求的对象。
                 * @param error
                 *            SSL错误对象
                 *
                 */
                @Override
                public void onReceivedSslError(WebView view, SslErrorHandler handler,
                                               SslError error) {
                    // view.loadUrl("file:///android_asset/error.html");
                    // TODO Auto-generated method stub
                    super.onReceivedSslError(view, handler, error);
                    Log.i(TAG, "onReceivedSslError");
                }

                /**
                 * onReceivedHttpAuthRequest 通知应用程序WebView接收到了一个Http
                 * auth的请求，应用程序可以使用supplied 设置webview的响应请求。默认行为是cancel 本次请求。
                 *
                 *
                 * 参数说明：
                 *
                 * @param view
                 *            接收WebViewClient的那个实例，前面看到webView.setWebViewClient(new
                 *            MyAndroidWebViewClient())，即是这个webview。
                 * @param handler
                 *            用来响应WebView请求的对象
                 * @param host
                 *            请求认证的host
                 * @param realm
                 *            认真请求所在的域
                 */
                @Override
                public void onReceivedHttpAuthRequest(WebView view,
                                                      HttpAuthHandler handler, String host, String realm) {
                    // TODO Auto-generated method stub
                    super.onReceivedHttpAuthRequest(view, handler, host, realm);
                    Log.i(TAG, "onReceivedHttpAuthRequest");
                }

                /**
                 * shouldOverrideKeyEvent
                 * 提供应用程序同步一个处理按键事件的机会，菜单快捷键需要被过滤掉。如果返回true，webview不处理该事件，如果返回false，
                 * webview会一直处理这个事件，因此在view 链上没有一个父类可以响应到这个事件。默认行为是return false；
                 *
                 *
                 * 参数说明：
                 *
                 * @param view
                 *            接收WebViewClient的那个实例，前面看到webView.setWebViewClient(new
                 *            MyAndroidWebViewClient())，即是这个webview。
                 * @param event
                 *            键盘事件名
                 * @return 如果返回true,应用程序处理该时间，返回false 交有webview处理。
                 */
                @Override
                public boolean shouldOverrideKeyEvent(WebView view, KeyEvent event) {
                    Log.i(TAG, "shouldOverrideKeyEvent");
                    // TODO Auto-generated method stub
                    return super.shouldOverrideKeyEvent(view, event);
                }

                /**
                 * 通知应用程序webview 要被scale。应用程序可以处理改事件，比如调整适配屏幕。
                 */
                @Override
                public void onScaleChanged(WebView view, float oldScale, float newScale) {
                    // TODO Auto-generated method stub
                    super.onScaleChanged(view, oldScale, newScale);
                    Log.i(TAG, "onScaleChanged");
                }

                /**
                 * onReceivedLoginRequest 通知应用程序有个自动登录的帐号过程
                 *
                 * 参数说明：
                 *
                 * @param view
                 *            请求登陆的webview
                 * @param realm
                 *            账户的域名，用来查找账户。
                 * @param account
                 *            一个可选的账户，如果是null 需要和本地的账户进行check， 如果是一个可用的账户，则提供登录。
                 * @param args
                 *            验证制定参数的登录用户
                 */
                @Override
                public void onReceivedLoginRequest(WebView view, String realm,
                                                   String account, String args) {
                    // TODO Auto-generated method stub
                    super.onReceivedLoginRequest(view, realm, account, args);
                    Log.i(TAG, "onReceivedLoginRequest");

                }

            });
            LogUtil.i("-----------"+url);
            webView.loadUrl(url);
        }
    }