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
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;
import android.util.Log;
import org.json.JSONObject;
import android.view.Gravity;
import android.view.KeyEvent;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "FileService";
    private static final String PREFS = "FileServicePrefs";
    private static final String KEY_SERVER = "server_url";
    private static final String KEY_REMEMBER = "remember_user";
    private static final String KEY_EMAIL = "saved_email";
    private static final String KEY_PASS = "saved_pass";
    private static final String DEFAULT_SERVER = "https://download.ssvr.top:8843";
    private static final int REQ_SCANNER = 1001;
    private static final int REQ_CAMERA = 1002;

    private WebView webView;
    private SwipeRefreshLayout swipeLayout;
    private ProgressBar progressBar;
    private FrameLayout rootLayout;
    private View customView;
    private View splashView;
    private SharedPreferences prefs;
    private String serverUrl;
    private String homeUrl;
    private String loginUrl;
    private String deviceId;
    private boolean checkedUpdate = false;
    private ValueCallback<Uri[]> filePathCallback;
    private String lastLoginEmail = "";
    private String lastLoginPass = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppLog.init();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        // 从 assets/server_config.json 读取默认服务器地址（构建时/apk修改时写入）
        String defaultServer = loadDefaultServerFromAssets();
        serverUrl = prefs.getString(KEY_SERVER, defaultServer);
        if (serverUrl.endsWith("/")) serverUrl = serverUrl.substring(0, serverUrl.length() - 1);
        homeUrl = serverUrl + "/home.html";
        loginUrl = serverUrl + "/login.html";
        // Generate persistent device ID
        deviceId = prefs.getString("device_id", "");
        if (deviceId.isEmpty()) {
            deviceId = java.util.UUID.randomUUID().toString();
            prefs.edit().putString("device_id", deviceId).apply();
        }
        AppLog.i("Main", "onCreate server=" + serverUrl);
        checkPermissions();
        setupUI();
        // Start with home page - server redirects to login if no valid session
        AppLog.i("Main", "setupUI complete, loading " + homeUrl);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            // File share from other apps → open upload dialog
            if (intent.getParcelableExtra(Intent.EXTRA_STREAM) != null) {
                Intent uploadIntent = new Intent(this, UploadActivity.class);
                uploadIntent.setAction(action);
                uploadIntent.putExtras(intent.getExtras());
                startActivity(uploadIntent);
                AppLog.i("Main", "Redirected file share to UploadActivity");
                return;
            }
            // Text share
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null && webView != null) {
                webView.postDelayed(() -> webView.loadUrl(text), 500);
            }
        }
    }

    // 从 assets/server_config.json 读取默认服务器地址（构建时写入或上传时动态注入）
    private String loadDefaultServerFromAssets() {
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
                return url;
            }
        } catch (Exception e) {
            AppLog.i("Main", "server_config.json not found, using DEFAULT_SERVER");
        }
        return DEFAULT_SERVER;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupUI() {
        rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.parseColor("#0d1117"));
        setContentView(rootLayout);

        // Splash screen
        splashView = createSplashView();
        rootLayout.addView(splashView);

        swipeLayout = new SwipeRefreshLayout(this) {
            @Override
            public boolean canChildScrollUp() {
                // 用 WebView.canScrollVertically(-1) 准确判断页面是否还能上滚
                // 覆盖 WebView 内部 overflow:scroll 的 div/列表等所有滚动场景
                if (webView != null) {
                    return webView.canScrollVertically(-1);
                }
                return super.canChildScrollUp();
            }
        };
        swipeLayout.setColorSchemeColors(Color.parseColor("#2196F3"));
        swipeLayout.setProgressBackgroundColorSchemeColor(Color.parseColor("#1c2128"));
        swipeLayout.setOnRefreshListener(() -> webView.reload());
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        sp.bottomMargin = dp(48);
        swipeLayout.setLayoutParams(sp);

        webView = new WebView(this);
        configWebView();
        swipeLayout.addView(webView);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(5));
        pp.gravity = Gravity.TOP;
        progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#00d4ff")));
        progressBar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.TRANSPARENT));
        progressBar.setMax(100);
        progressBar.setZ(100); // Ensure it's above everything
        rootLayout.addView(swipeLayout);
        rootLayout.addView(progressBar);

        setupBottomBar(rootLayout);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack(); else finish();
            }
        });

        // Start with home page - server redirects to login if no valid session
        webView.loadUrl(homeUrl);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true); s.setAllowContentAccess(true);
        s.setSupportZoom(true); s.setBuiltInZoomControls(true); s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true); s.setLoadWithOverviewMode(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString(s.getUserAgentString() + " FileServiceApp/2.1");

        CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String url = r.getUrl().toString();
                if (url.startsWith("intent://") || url.startsWith("market://") ||
                        url.startsWith("alipays://") || url.startsWith("weixin://") ||
                        url.startsWith("dingtalk://")) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception e) {}
                    return true;
                }
                return false;
            }
            @Override public void onReceivedSslError(WebView v, SslErrorHandler h, SslError e) { h.proceed(); }
            @Override public void onPageStarted(WebView v, String url, Bitmap f) {
                AppLog.i("WebView", "Page started: " + url);
                progressBar.setVisibility(View.VISIBLE); progressBar.setProgress(0);
            }
            @Override public void onPageFinished(WebView v, String url) {
                AppLog.i("WebView", "Page finished: " + url);
                swipeLayout.setRefreshing(false);
                progressBar.setVisibility(View.GONE);
                // Hide splash on first page load
                if (splashView != null) {
                    splashView.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                        if (splashView != null) {
                            rootLayout.removeView(splashView);
                            splashView = null;
                        }
                    }).start();
                }
                // Detect successful login (reached home.html)
                if (url != null && url.contains("/home.html")) {
                    autoCheckUpdate();
                    if (!lastLoginEmail.isEmpty() && !prefs.getBoolean("auto_login_saved", false)) {
                        // First successful login - ask to save
                        runOnUiThread(() -> new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                            .setTitle("保存登录凭据")
                            .setMessage("是否保存账号密码，下次自动登录？")
                            .setPositiveButton("保存", (d,w) -> {
                                saveCredentials(lastLoginEmail, lastLoginPass);
                                prefs.edit().putBoolean("auto_login_saved", true).apply();
                                Toast.makeText(MainActivity.this, "已保存，下次将自动登录", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("取消", (d,w) -> {
                                lastLoginEmail = "";
                                lastLoginPass = "";
                            }).show());
                    }
                    lastLoginEmail = "";
                    lastLoginPass = "";
                }
                // Auto-login on login page
                if (url != null && url.contains("/login.html")) {
                    tryAutoLogin();
                }
                injectBridge();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView v, int p) {
                progressBar.setProgress(p); if (p >= 95) progressBar.setVisibility(View.GONE);
            }
            @Override public boolean onShowFileChooser(WebView wv, ValueCallback<Uri[]> cb, FileChooserParams ps) {
                filePathCallback = cb;
                try { startActivityForResult(ps.createIntent(), 200); } catch (Exception e) { filePathCallback = null; }
                return true;
            }
            @Override public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) { callback.onCustomViewHidden(); return; }
                customView = view;
                swipeLayout.setVisibility(View.GONE);
                rootLayout.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                if (getSupportActionBar() != null) getSupportActionBar().hide();
            }
            @Override public void onHideCustomView() {
                if (customView == null) return;
                rootLayout.removeView(customView);
                customView = null;
                swipeLayout.setVisibility(View.VISIBLE);
                if (getSupportActionBar() != null) getSupportActionBar().show();
            }
        });

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidNative");
        webView.addJavascriptInterface(new AndroidAppBridge(), "AndroidApp");
        AppLog.i("WebView", "JS interfaces registered: AndroidNative + AndroidApp");

        // Handle downloads (including APK install prompt)
        webView.setDownloadListener((url, ua, cd, mime, len) -> {
            try {
                DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
                r.setMimeType(mime); r.addRequestHeader("User-Agent", ua);
                r.addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url));
                String fn = URLUtil.guessFileName(url, cd, mime);
                r.setTitle(fn); r.setDescription("下载中...");
                r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fn);
                ((DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(r);
                Toast.makeText(this, "开始下载: " + fn, Toast.LENGTH_SHORT).show();
                // If APK, offer to install when done
                if (fn.endsWith(".apk")) {
                    Toast.makeText(this, "下载完成后点击通知安装，如无权限请在设置中允许", Toast.LENGTH_LONG).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, "下载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Auto-login: inject saved credentials into login form
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

    // Save credentials after successful login
    private void saveCredentials(String email, String pass) {
        prefs.edit()
                .putString(KEY_EMAIL, email)
                .putString(KEY_PASS, pass)
                .putBoolean(KEY_REMEMBER, true)
                .apply();
        Log.d(TAG, "Credentials saved for: " + email);
        Toast.makeText(this, "已记住登录凭据", Toast.LENGTH_SHORT).show();
    }

    // Clear credentials on manual logout
    private void clearCredentials() {
        lastLoginEmail = "";
        lastLoginPass = "";
        prefs.edit().remove(KEY_EMAIL).remove(KEY_PASS).putBoolean(KEY_REMEMBER, false).apply();
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();
    }

    // JS Bridge - injected AFTER page load to override frontend scanner
    private void injectBridge() {
        String js =
        "(function(){" +
            // CRITICAL: Override the page's scanner to use native bridge
            "var patchScanners=function(){" +
                // Method 1: window.__fm.openScanner (main file manager)
                "if(window.__fm&&window.__fm.openScanner){" +
                    "var orig=window.__fm.openScanner;" +
                    "window.__fm.openScanner=function(){" +
                        "console.log('[App] __fm.openScanner -> native');" +
                        "try{window.AndroidApp.openScanner();}catch(e){}" +
                    "};" +
                    "console.log('[App] Patched __fm.openScanner');" +
                "}" +
                // Method 2: window.openScanner (global)
                "if(window.openScanner){" +
                    "var orig2=window.openScanner;" +
                    "window.openScanner=function(){" +
                        "console.log('[App] openScanner -> native');" +
                        "try{window.AndroidApp.openScanner();}catch(e){}" +
                    "};" +
                    "console.log('[App] Patched window.openScanner');" +
                "}" +
                // Method 3: window.__openScanner (legacy Capacitor)
                "if(window.__openScanner){" +
                    "window.__openScanner=function(cb){" +
                        "window._scanCb=cb;try{window.AndroidApp.openScanner();}catch(e){}" +
                    "};" +
                "}" +
            "};" +
            // Try immediately, then retry after a delay (page JS may load async)
            "patchScanners();setTimeout(patchScanners,500);setTimeout(patchScanners,2000);" +
            // Also ensure NativeBridge exists
            "if(!window.NativeBridge){" +
                "window.NativeBridge={" +
                    "scan:function(cb){window._scanCb=cb;window.AndroidApp.openScanner();}," +
                    "share:function(url,t){window.AndroidNative.postMessage(JSON.stringify({action:'share',url:url||'',title:t||''}));}," +
                    "handleScanResult:function(r){if(window._scanCb){var c=window._scanCb;window._scanCb=null;c(r);}" +
                    "window.dispatchEvent(new CustomEvent('scan_result',{detail:r}));}" +
                "};" +
            "}" +
            // Inject device ID into login headers
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
        AppLog.i("Bridge", "Injected + scanner patched on " + webView.getUrl());
    }

    private void openScanner() {
        AppLog.i("Scanner", "openScanner() called");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED) {
            AppLog.i("Scanner", "Requesting camera permission");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
            return;
        }
        AppLog.i("Scanner", "Starting ScannerActivity");
        startActivityForResult(new Intent(this, ScannerActivity.class), REQ_SCANNER);
    }

    // Bottom bar: Home | Refresh | Back | Scan | Settings
    private void setupBottomBar(FrameLayout root) {
        FrameLayout bar = new FrameLayout(this);
        int h = dp(48);
        bar.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h,
                Gravity.BOTTOM));
        bar.setBackgroundColor(Color.parseColor("#161b22"));

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setGravity(Gravity.CENTER);
        btns.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));

        // Home | Refresh | Back | Settings
        navBtn(btns, "⌂", "首页", () -> webView.loadUrl(homeUrl));
        navBtn(btns, "⟳", "刷新", () -> webView.reload());
        navBtn(btns, "◀", "返回", () -> { if (webView.canGoBack()) webView.goBack(); else finish(); });
        navBtn(btns, "⚙", "设置", () -> startActivity(new Intent(this, SettingsActivity.class)));

        bar.addView(btns);
        root.addView(bar);
    }

    private void navBtn(LinearLayout p, String icon, String label, Runnable action) {
        Button b = new Button(this);
        b.setText(icon); b.setTextColor(Color.parseColor("#8b949e")); b.setTextSize(18);
        b.setAllCaps(false); b.setBackgroundColor(Color.TRANSPARENT); b.setTooltipText(label);
        b.setOnClickListener(v -> action.run());
        p.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
    }

    // Legacy bridge - matches frontend's window.AndroidApp expectations
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
        public boolean isNativeApp() { return true; }
    }

    // Native bridge class
    public class AndroidBridge {
        @JavascriptInterface
        public void postMessage(String json) {
            try {
                org.json.JSONObject msg = new org.json.JSONObject(json);
                String action = msg.optString("action", "");
                org.json.JSONObject d = msg.optJSONObject("data");
                if (d == null) d = new org.json.JSONObject();
                final org.json.JSONObject fd = d;

                runOnUiThread(() -> {
                    switch (action) {
                        case "scan": MainActivity.this.openScanner(); break;
                        case "share": {
                            Intent si = new Intent(Intent.ACTION_SEND); si.setType("text/plain");
                            si.putExtra(Intent.EXTRA_TEXT, fd.optString("url", ""));
                            if (fd.has("title")) si.putExtra(Intent.EXTRA_SUBJECT, fd.optString("title"));
                            startActivity(Intent.createChooser(si, "分享到")); break;
                        }
                        case "clipboard":
                            ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE))
                                    .setPrimaryClip(ClipData.newPlainText("FileService", fd.optString("text", "")));
                            Toast.makeText(MainActivity.this, "已复制", Toast.LENGTH_SHORT).show(); break;
                        case "logout":
                            clearCredentials();
                            prefs.edit().putBoolean("auto_login_saved", false).apply();
                            webView.loadUrl(loginUrl); break;
                        case "saveLogin":
                            String em = fd.optString("email", "");
                            String pw = fd.optString("pass", "");
                            if (!em.isEmpty()) { lastLoginEmail = em; lastLoginPass = pw; saveCredentials(em, pw); }
                            break;
                    }
                });
            } catch (Exception e) { Log.e(TAG, "Bridge", e); }
        }
    }

    @Override
    protected void onActivityResult(int req, int res, @Nullable Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_SCANNER && res == RESULT_OK && data != null && webView != null) {
            String r = data.getStringExtra("SCAN_RESULT");
            if (r != null) {
                AppLog.i("Scanner", "Result: " + r);
                // If it's a QR login URL, navigate directly to confirm page
                if (r.startsWith("http://") || r.startsWith("https://")) {
                    if (r.contains("/api/auth/qr-login/scan?token=")) {
                        // Skip the redundant scan page, go straight to confirm
                        String token = r.substring(r.indexOf("token=") + 6);
                        if (token.contains("&")) token = token.substring(0, token.indexOf("&"));
                        webView.loadUrl(serverUrl + "/api/auth/qr-login/confirm?token=" + token);
                        AppLog.i("Scanner", "Navigating directly to confirm page");
                    } else {
                        webView.loadUrl(r);
                    }
                } else {
                    // Non-URL result - pass to WebView JS
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
        if (req == REQ_CAMERA && g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) openScanner();
    }

    @Override protected void onResume() { super.onResume(); if (webView != null) webView.onResume(); }

    // Auto check for updates on first page load
    private void autoCheckUpdate() {
        if (checkedUpdate) return;
        checkedUpdate = true;
        new Thread(() -> {
            try {
                URL url = new URL(serverUrl + "/api/version/latest");
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) url.openConnection();
                c.setConnectTimeout(5000);
                java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(c.getInputStream()));
                StringBuilder sb = new StringBuilder(); String l;
                while ((l = br.readLine()) != null) sb.append(l); br.close();
                org.json.JSONObject j = new org.json.JSONObject(sb.toString());
                if (j.optInt("code") == 0 && j.has("data")) {
                    org.json.JSONObject d = j.getJSONObject("data");
                    int nc = d.optInt("versionCode", 0);
                    int cc = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
                    if (nc > cc) runOnUiThread(() ->
                        new androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("发现新版本 v" + d.optString("version"))
                            .setMessage(d.optString("notes", "") + "\n\n大小: " + Math.round(d.optLong("size")/1024f/1024f) + " MB")
                            .setPositiveButton("下载", (di, w) -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(serverUrl + d.optString("url")))))
                            .setNegativeButton("稍后", null).show());
                }
            } catch (Exception e) { /* silent */ }
        }).start();
    }
    @Override protected void onPause() { super.onPause(); if (webView != null) webView.onPause(); }
    @Override protected void onDestroy() {
        if (webView != null) { webView.loadUrl("about:blank"); webView.clearHistory(); webView.destroy(); webView = null; }
        super.onDestroy();
    }

    private void checkPermissions() {
        java.util.ArrayList<String> n = new java.util.ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) n.add(Manifest.permission.POST_NOTIFICATIONS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            n.add(Manifest.permission.CAMERA);
        if (!n.isEmpty()) ActivityCompat.requestPermissions(this, n.toArray(new String[0]), 100);
    }

    private int dp(int px) { return (int)(px * getResources().getDisplayMetrics().density); }
    private static String esc(String s) { if(s==null)return""; return s.replace("\\","\\\\").replace("'","\\'").replace("\n","\\n"); }

    private View createSplashView() {
        FrameLayout splash = new FrameLayout(this);
        splash.setBackgroundColor(Color.parseColor("#0d1117"));
        splash.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        splash.setClickable(true); // Block clicks through to WebView

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.gravity = Gravity.CENTER;
        content.setLayoutParams(cp);

        // Folder icon with animation
        TextView icon = new TextView(this);
        icon.setText("\uD83D\uDCC1"); // 📁
        icon.setTextSize(72);
        icon.setGravity(Gravity.CENTER);
        content.addView(icon);

        // App name
        TextView name = new TextView(this);
        name.setText("FileService");
        name.setTextColor(Color.parseColor("#e0e6f0"));
        name.setTextSize(22);
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, dp(16), 0, 0);
        content.addView(name);

        // Loading dots
        ProgressBar pb = new ProgressBar(this);
        pb.setIndeterminate(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            pb.getIndeterminateDrawable().setTint(Color.parseColor("#2196F3"));
        }
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(28), dp(28));
        pp.gravity = Gravity.CENTER;
        pp.topMargin = dp(28);
        content.addView(pb, pp);

        splash.addView(content);
        return splash;
    }
}
