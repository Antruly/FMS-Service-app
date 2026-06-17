package com.fileservice.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;

/**
 * Main Activity — WebView-based file manager with native bridges.
 *
 * Screen-adaptation strategy:
 *   WindowCompat.setDecorFitsSystemWindows(window, false) makes the window
 *   draw edge-to-edge. The root layout (activity_main.xml) has
 *   fitsSystemWindows="true", so Android automatically applies system-bar
 *   insets as padding. All child views stay within the safe area without
 *   any manual margin/padding calculations.
 */
public class MainActivity extends AppCompatActivity {

    // ── Constants ──────────────────────────────────────────────
    private static final String TAG = "FileService";
    private static final String PREFS = "FileServicePrefs";
    private static final String KEY_SERVER = "server_url";
    private static final String KEY_REMEMBER = "remember_user";
    private static final String KEY_EMAIL = "saved_email";
    private static final String KEY_PASS = "saved_pass";
    private static final String DEFAULT_SERVER = "https://download.ssvr.top:8843";
    private static final int REQ_SCANNER = 1001;
    private static final int REQ_CAMERA = 1002;

    // ── UI references ──────────────────────────────────────────
    private FrameLayout rootLayout;
    private WebView webView;
    private TopOnlySwipeRefreshLayout swipeLayout;
    private ProgressBar progressBar;
    private FrameLayout splashView;
    private long splashStartTime;
    private LinearLayout bottomBar;
    private View customView;
    private volatile boolean mPageCanScrollUp;

    // ── State ──────────────────────────────────────────────────
    private SharedPreferences prefs;
    private String serverUrl;
    private String homeUrl;
    private String loginUrl;
    private String deviceId;
    private boolean checkedUpdate;
    private ValueCallback<Uri[]> filePathCallback;
    private String lastLoginEmail = "";
    private String lastLoginPass = "";

    // ═══════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Enable edge-to-edge: window content draws behind transparent system bars.
        // The root layout's fitsSystemWindows="true" (in XML) then consumes the
        // insets and pads the content area automatically.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        AppLog.init();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        serverUrl = resolveServerUrl();
        homeUrl = serverUrl + "/home.html";
        loginUrl = serverUrl + "/login.html";
        deviceId = getOrCreateDeviceId();

        AppLog.i("Main", "onCreate server=" + serverUrl);
        checkPermissions();
        setupUI();
        AppLog.i("Main", "setupUI complete, loading " + homeUrl);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            if (intent.getParcelableExtra(Intent.EXTRA_STREAM) != null) {
                Intent uploadIntent = new Intent(this, UploadActivity.class);
                uploadIntent.setAction(action);
                uploadIntent.putExtras(intent.getExtras());
                startActivity(uploadIntent);
                AppLog.i("Main", "Redirected file share to UploadActivity");
                return;
            }
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null && webView != null) {
                webView.postDelayed(() -> webView.loadUrl(text), 500);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) webView.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.clearHistory();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    // ═══════════════════════════════════════════════════════════
    // UI SETUP
    // ═══════════════════════════════════════════════════════════

    private void setupUI() {
        // Inflate the fitsSystemWindows layout
        setContentView(R.layout.activity_main);

        rootLayout = findViewById(R.id.root_layout);

        // ── Splash ─────────────────────────────────────────
        splashView = findViewById(R.id.splash_view);
        splashStartTime = System.currentTimeMillis();

        // ── SwipeRefreshLayout ─────────────────────────────
        swipeLayout = findViewById(R.id.swipe_layout);
        swipeLayout.setColorSchemeColors(Color.parseColor("#2196F3"));
        swipeLayout.setProgressBackgroundColorSchemeColor(Color.parseColor("#1c2128"));
        swipeLayout.setDistanceToTriggerSync(dp(120));
        swipeLayout.setOnRefreshListener(() -> webView.reload());
        // Custom scroll-up detection: respect both WebView scroll and JS-reported scroll
        swipeLayout.setOnChildScrollUpCallback((parent, child) -> {
            if (webView != null) {
                if (webView.getScrollY() > 0) return true;
                if (mPageCanScrollUp) return true;
                return webView.canScrollVertically(-1);
            }
            return false;
        });

        // ── WebView ───────────────────────────────────────
        webView = findViewById(R.id.web_view);
        configWebView();
        webView.loadUrl(homeUrl);

        // ── ProgressBar ───────────────────────────────────
        progressBar = findViewById(R.id.progress_bar);
        progressBar.setMax(100);
        progressBar.setVisibility(View.GONE);

        // ── Bottom bar ────────────────────────────────────
        bottomBar = findViewById(R.id.bottom_bar);
        Button btnHome   = findViewById(R.id.btn_home);
        Button btnRefresh = findViewById(R.id.btn_refresh);
        Button btnBack    = findViewById(R.id.btn_back);
        Button btnSettings = findViewById(R.id.btn_settings);
        btnHome.setOnClickListener(v -> webView.loadUrl(homeUrl));
        btnRefresh.setOnClickListener(v -> webView.reload());
        btnBack.setOnClickListener(v -> {
            if (webView.canGoBack()) webView.goBack(); else finish();
        });
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        // ── Back press ────────────────────────────────────
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack(); else finish();
            }
        });
    }

    // ═══════════════════════════════════════════════════════════
    // WEBVIEW CONFIGURATION
    // ═══════════════════════════════════════════════════════════

    @SuppressLint("SetJavaScriptEnabled")
    private void configWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString(s.getUserAgentString() + " FileServiceApp/2.1");

        CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }

        // ── WebViewClient ─────────────────────────────────────
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String url = r.getUrl().toString();
                if (url.startsWith("intent://") || url.startsWith("market://")
                        || url.startsWith("alipays://") || url.startsWith("weixin://")
                        || url.startsWith("dingtalk://")) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    } catch (Exception ignored) {}
                    return true;
                }
                return false;
            }

            @Override
            public void onReceivedSslError(WebView v, SslErrorHandler h, SslError e) {
                h.proceed();
            }

            @Override
            public void onPageStarted(WebView v, String url, Bitmap f) {
                AppLog.i("WebView", "Page started: " + url);
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(0);
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                AppLog.i("WebView", "Page finished: " + url);
                swipeLayout.setRefreshing(false);
                progressBar.setVisibility(View.GONE);

                // Hide splash with fade-out on first page load (min 1s display)
                if (splashView != null) {
                    long elapsed = System.currentTimeMillis() - splashStartTime;
                    long delay = Math.max(0, 1000 - elapsed);
                    splashView.postDelayed(() -> {
                        if (splashView == null) return;
                        splashView.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                            if (splashView != null) {
                                rootLayout.removeView(splashView);
                                splashView = null;
                            }
                        }).start();
                    }, delay);
                }

                // Successful login reached home page
                if (url != null && url.contains("/home.html")) {
                    autoCheckUpdate();
                    if (!lastLoginEmail.isEmpty() && !prefs.getBoolean("auto_login_saved", false)) {
                        runOnUiThread(() -> new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                                .setTitle("保存登录凭据")
                                .setMessage("是否保存账号密码，下次自动登录？")
                                .setPositiveButton("保存", (d, w) -> {
                                    saveCredentials(lastLoginEmail, lastLoginPass);
                                    prefs.edit().putBoolean("auto_login_saved", true).apply();
                                    Toast.makeText(MainActivity.this, "已保存，下次将自动登录", Toast.LENGTH_SHORT).show();
                                })
                                .setNegativeButton("取消", (d, w) -> {
                                    lastLoginEmail = "";
                                    lastLoginPass = "";
                                }).show());
                    }
                    lastLoginEmail = "";
                    lastLoginPass = "";
                }

                if (url != null && url.contains("/login.html")) {
                    tryAutoLogin();
                }

                injectBridge();
            }
        });

        // ── WebChromeClient ───────────────────────────────────
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int p) {
                progressBar.setProgress(p);
                if (p >= 95) progressBar.setVisibility(View.GONE);
            }

            @Override
            public boolean onShowFileChooser(WebView wv, ValueCallback<Uri[]> cb, FileChooserParams ps) {
                filePathCallback = cb;
                try {
                    startActivityForResult(ps.createIntent(), 200);
                } catch (Exception e) {
                    filePathCallback = null;
                }
                return true;
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                swipeLayout.setVisibility(View.GONE);
                rootLayout.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                if (getSupportActionBar() != null) getSupportActionBar().hide();
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;
                rootLayout.removeView(customView);
                customView = null;
                swipeLayout.setVisibility(View.VISIBLE);
                if (getSupportActionBar() != null) getSupportActionBar().show();
            }
        });

        // ── JS Interfaces ─────────────────────────────────────
        webView.addJavascriptInterface(new AndroidAppBridge(), "AndroidApp");
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidNative");
        AppLog.i("WebView", "JS interfaces registered: AndroidApp + AndroidNative");

        // ── Download Listener ─────────────────────────────────
        webView.setDownloadListener((url, ua, cd, mime, len) -> {
            try {
                DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
                r.setMimeType(mime);
                r.addRequestHeader("User-Agent", ua);
                r.addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url));
                String fn = URLUtil.guessFileName(url, cd, mime);
                r.setTitle(fn);
                r.setDescription("下载中...");
                r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fn);
                ((DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(r);
                Toast.makeText(this, "开始下载: " + fn, Toast.LENGTH_SHORT).show();
                if (fn.endsWith(".apk")) {
                    Toast.makeText(this, "下载完成后点击通知安装，如无权限请在设置中允许", Toast.LENGTH_LONG).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, "下载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ═══════════════════════════════════════════════════════════
    // JS BRIDGE INJECTION
    // ═══════════════════════════════════════════════════════════

    private void injectBridge() {
        String js =
                "(function(){" +
                        // ── Scroll state detection (for native pull-to-refresh) ──
                        "var _lastScrollState=false;" +
                        "function _checkPageScroll(){" +
                        "var canScroll=false;" +
                        "if(window.scrollY>8||document.documentElement.scrollTop>8||document.body.scrollTop>8)canScroll=true;" +
                        "var containers=document.querySelectorAll('.main-content,.file-list,.panel-body,.file-manager,.page-content,[id*=\"page-panel\"],.card,.content-area,.recycle-list,.share-list');" +
                        "for(var i=0;i<containers.length;i++){" +
                        "if(containers[i].scrollTop>8){canScroll=true;break;}" +
                        "}" +
                        "if(!canScroll){" +
                        "var all=document.querySelectorAll('*');" +
                        "for(var j=0;j<Math.min(all.length,200);j++){" +
                        "try{if(all[j].scrollTop>8&&all[j].clientHeight>0){canScroll=true;break;}}catch(e){}" +
                        "}" +
                        "}" +
                        "if(canScroll!==_lastScrollState){" +
                        "_lastScrollState=canScroll;" +
                        "try{window.AndroidApp.setPageScrollState(canScroll);}catch(e){}" +
                        "}" +
                        "}" +
                        "window.addEventListener('scroll',_checkPageScroll,{passive:true});" +
                        "document.addEventListener('scroll',_checkPageScroll,{passive:true,capture:true});" +
                        "document.addEventListener('touchmove',_checkPageScroll,{passive:true});" +
                        "_checkPageScroll();" +
                        "setInterval(_checkPageScroll,300);" +

                        // ── Scanner patches ──
                        "var patchScanners=function(){" +
                        "if(window.__fm&&window.__fm.openScanner){" +
                        "var orig=window.__fm.openScanner;" +
                        "window.__fm.openScanner=function(){" +
                        "console.log('[App] __fm.openScanner -> native');" +
                        "try{window.AndroidApp.openScanner();}catch(e){}" +
                        "};" +
                        "console.log('[App] Patched __fm.openScanner');" +
                        "}" +
                        "if(window.openScanner){" +
                        "var orig2=window.openScanner;" +
                        "window.openScanner=function(){" +
                        "console.log('[App] openScanner -> native');" +
                        "try{window.AndroidApp.openScanner();}catch(e){}" +
                        "};" +
                        "console.log('[App] Patched window.openScanner');" +
                        "}" +
                        "if(window.__openScanner){" +
                        "window.__openScanner=function(cb){" +
                        "window._scanCb=cb;try{window.AndroidApp.openScanner();}catch(e){}" +
                        "};" +
                        "}" +
                        "};" +
                        "patchScanners();setTimeout(patchScanners,500);setTimeout(patchScanners,2000);" +

                        // ── NativeBridge fallback ──
                        "if(!window.NativeBridge){" +
                        "window.NativeBridge={" +
                        "scan:function(cb){window._scanCb=cb;window.AndroidApp.openScanner();}," +
                        "share:function(url,t){window.AndroidNative.postMessage(JSON.stringify({action:'share',url:url||'',title:t||''}));}," +
                        "handleScanResult:function(r){if(window._scanCb){var c=window._scanCb;window._scanCb=null;c(r);}" +
                        "window.dispatchEvent(new CustomEvent('scan_result',{detail:r}));}" +
                        "};" +
                        "}" +

                        // ── Device ID injection ──
                        "var _dId='" + deviceId + "';" +
                        "var _origFetch=window.fetch;window.fetch=function(u,o){" +
                        "o=o||{};o.headers=o.headers||{};" +
                        "if(typeof u==='string'&&u.indexOf('/api/auth/login')!==-1){" +
                        "if(o.headers instanceof Headers)o.headers.set('X-Device-Id',_dId);" +
                        "else o.headers['X-Device-Id']=_dId;" +
                        "}" +
                        "return _origFetch.call(window,u,o);" +
                        "};" +
                        "console.log('[App] Bridges injected + scanners patched');" +
                        "})()";
        webView.evaluateJavascript(js, null);
        AppLog.i("Bridge", "Injected on " + webView.getUrl());
    }

    // ═══════════════════════════════════════════════════════════
    // AUTO-LOGIN
    // ═══════════════════════════════════════════════════════════

    private void tryAutoLogin() {
        if (!prefs.getBoolean(KEY_REMEMBER, false)) return;
        String email = prefs.getString(KEY_EMAIL, "");
        String pass = prefs.getString(KEY_PASS, "");
        if (email.isEmpty() || pass.isEmpty()) return;

        lastLoginEmail = email;
        lastLoginPass = pass;
        Log.d(TAG, "Auto-login injecting for: " + email);

        String js = "(function attempLogin(){" +
                "var e=document.querySelector('input[type=email],input[name=email],#email');" +
                "var p=document.querySelector('input[type=password],input[name=password],#password');" +
                "if(e&&p){" +
                "e.value='" + esc(email) + "';e.dispatchEvent(new Event('input',{bubbles:true}));" +
                "p.value='" + esc(pass) + "';p.dispatchEvent(new Event('input',{bubbles:true}));" +
                "var s=document.querySelector('button[type=submit],.login-btn,#btn-login');" +
                "if(s){s.click();return;}" +
                "}" +
                "setTimeout(attempLogin,800);" +
                "})()";
        webView.evaluateJavascript(js, null);
    }

    private void saveCredentials(String email, String pass) {
        prefs.edit()
                .putString(KEY_EMAIL, email)
                .putString(KEY_PASS, pass)
                .putBoolean(KEY_REMEMBER, true)
                .apply();
        Log.d(TAG, "Credentials saved for: " + email);
        Toast.makeText(this, "已记住登录凭据", Toast.LENGTH_SHORT).show();
    }

    private void clearCredentials() {
        lastLoginEmail = "";
        lastLoginPass = "";
        prefs.edit().remove(KEY_EMAIL).remove(KEY_PASS).putBoolean(KEY_REMEMBER, false).apply();
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();
    }

    // ═══════════════════════════════════════════════════════════
    // SCANNER
    // ═══════════════════════════════════════════════════════════

    private void openScanner() {
        AppLog.i("Scanner", "openScanner() called");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED) {
            AppLog.i("Scanner", "Requesting camera permission");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
            return;
        }
        AppLog.i("Scanner", "Starting ScannerActivity");
        startActivityForResult(new Intent(this, ScannerActivity.class), REQ_SCANNER);
    }

    // ═══════════════════════════════════════════════════════════
    // VERSION CHECK
    // ═══════════════════════════════════════════════════════════

    private void autoCheckUpdate() {
        if (checkedUpdate) return;
        checkedUpdate = true;
        new Thread(() -> {
            try {
                URL url = new URL(serverUrl + "/api/version/latest");
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) url.openConnection();
                c.setConnectTimeout(5000);
                BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String l;
                while ((l = br.readLine()) != null) sb.append(l);
                br.close();
                JSONObject j = new JSONObject(sb.toString());
                if (j.optInt("code") == 0 && j.has("data")) {
                    JSONObject d = j.getJSONObject("data");
                    int nc = d.optInt("versionCode", 0);
                    int cc = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
                    if (nc > cc) {
                        runOnUiThread(() ->
                                new androidx.appcompat.app.AlertDialog.Builder(this)
                                        .setTitle("发现新版本 v" + d.optString("version"))
                                        .setMessage(d.optString("notes", "") + "\n\n大小: " + Math.round(d.optLong("size") / 1024f / 1024f) + " MB")
                                        .setPositiveButton("下载", (di, w) -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(serverUrl + d.optString("url")))))
                                        .setNegativeButton("稍后", null).show());
                    }
                }
            } catch (Exception e) { /* silent */ }
        }).start();
    }

    // ═══════════════════════════════════════════════════════════
    // ACTIVITY RESULTS
    // ═══════════════════════════════════════════════════════════

    @Override
    protected void onActivityResult(int req, int res, @Nullable Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_SCANNER && res == RESULT_OK && data != null && webView != null) {
            String r = data.getStringExtra("SCAN_RESULT");
            if (r != null) {
                AppLog.i("Scanner", "Result: " + r);
                if (r.startsWith("http://") || r.startsWith("https://")) {
                    if (r.contains("/api/auth/qr-login/scan?token=")) {
                        String token = r.substring(r.indexOf("token=") + 6);
                        if (token.contains("&")) token = token.substring(0, token.indexOf("&"));
                        webView.loadUrl(serverUrl + "/api/auth/qr-login/confirm?token=" + token);
                        AppLog.i("Scanner", "Navigating directly to confirm page");
                    } else {
                        webView.loadUrl(r);
                    }
                } else {
                    webView.evaluateJavascript(
                            "javascript:window.NativeBridge&&window.NativeBridge.handleScanResult('" + esc(r) + "')", null);
                }
            }
        }
        if (req == 200 && filePathCallback != null) {
            filePathCallback.onReceiveValue(data != null ? new Uri[]{data.getData()} : null);
            filePathCallback = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] p, @NonNull int[] g) {
        super.onRequestPermissionsResult(req, p, g);
        if (req == REQ_CAMERA && g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) {
            openScanner();
        }
    }

    // ═══════════════════════════════════════════════════════════
    // PERMISSIONS
    // ═══════════════════════════════════════════════════════════

    private void checkPermissions() {
        java.util.ArrayList<String> n = new java.util.ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            n.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED) {
            n.add(Manifest.permission.CAMERA);
        }
        if (!n.isEmpty()) {
            ActivityCompat.requestPermissions(this, n.toArray(new String[0]), 100);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // JS BRIDGE CLASSES
    // ═══════════════════════════════════════════════════════════

    /** Lightweight bridge matching the frontend {@code window.AndroidApp} interface. */
    public class AndroidAppBridge {
        @JavascriptInterface
        public void openScanner() {
            runOnUiThread(() -> MainActivity.this.openScanner());
        }

        @JavascriptInterface
        public void showToast(String msg) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
        }

        @JavascriptInterface
        public String getServerUrl() {
            return serverUrl;
        }

        @JavascriptInterface
        public String getString(String key, String fallback) {
            if ("server_url".equals(key)) return prefs.getString(KEY_SERVER, serverUrl);
            return prefs.getString(key, fallback != null ? fallback : "");
        }

        @JavascriptInterface
        public void setString(String key, String value) {
            prefs.edit().putString(key, value).apply();
        }

        @JavascriptInterface
        public void setPageScrollState(boolean canScrollUp) {
            mPageCanScrollUp = canScrollUp;
        }

        @JavascriptInterface
        public boolean isNativeApp() {
            return true;
        }
    }

    /** Full bridge handling {@code AndroidNative.postMessage(json)} calls. */
    public class AndroidBridge {
        @JavascriptInterface
        public void postMessage(String json) {
            try {
                JSONObject msg = new JSONObject(json);
                String action = msg.optString("action", "");
                JSONObject d = msg.optJSONObject("data");
                if (d == null) d = new JSONObject();
                final JSONObject fd = d;

                runOnUiThread(() -> {
                    switch (action) {
                        case "scan":
                            MainActivity.this.openScanner();
                            break;
                        case "share": {
                            Intent si = new Intent(Intent.ACTION_SEND);
                            si.setType("text/plain");
                            si.putExtra(Intent.EXTRA_TEXT, fd.optString("url", ""));
                            if (fd.has("title"))
                                si.putExtra(Intent.EXTRA_SUBJECT, fd.optString("title"));
                            startActivity(Intent.createChooser(si, "分享到"));
                            break;
                        }
                        case "clipboard":
                            ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE))
                                    .setPrimaryClip(ClipData.newPlainText("FileService", fd.optString("text", "")));
                            Toast.makeText(MainActivity.this, "已复制", Toast.LENGTH_SHORT).show();
                            break;
                        case "logout":
                            clearCredentials();
                            prefs.edit().putBoolean("auto_login_saved", false).apply();
                            webView.loadUrl(loginUrl);
                            break;
                        case "saveLogin": {
                            String em = fd.optString("email", "");
                            String pw = fd.optString("pass", "");
                            if (!em.isEmpty()) {
                                lastLoginEmail = em;
                                lastLoginPass = pw;
                                saveCredentials(em, pw);
                            }
                            break;
                        }
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Bridge", e);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════

    /** Load server URL from assets/server_config.json, falling back to DEFAULT_SERVER. */
    private String resolveServerUrl() {
        // First try SharedPreferences (user override via Settings)
        String saved = prefs.getString(KEY_SERVER, "");
        if (!saved.isEmpty()) return trimTrailingSlash(saved);

        // Then try assets/server_config.json (build-time config)
        try {
            InputStream is = getAssets().open("server_config.json");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            is.close();
            JSONObject config = new JSONObject(sb.toString());
            String url = config.optString("server_url", "");
            if (!url.isEmpty() && !url.equals("__AUTO__")) {
                AppLog.i("Main", "Loaded server_url from assets: " + url);
                return trimTrailingSlash(url);
            }
        } catch (Exception e) {
            AppLog.i("Main", "server_config.json not found, using DEFAULT_SERVER");
        }
        return DEFAULT_SERVER;
    }

    /** Generate or load persistent device ID. */
    private String getOrCreateDeviceId() {
        String id = prefs.getString("device_id", "");
        if (id.isEmpty()) {
            id = java.util.UUID.randomUUID().toString();
            prefs.edit().putString("device_id", id).apply();
        }
        return id;
    }

    private static String trimTrailingSlash(String s) {
        if (s.endsWith("/")) return s.substring(0, s.length() - 1);
        return s;
    }

    /** Convert dp to pixels. */
    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    /** Escape string for safe injection into single-quoted JS strings. */
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n");
    }
}
