package com.fileservice.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS = "FileServicePrefs";
    private static final String KEY_SERVER = "server_url";
    private static final String KEY_REMEMBER = "remember_user";
    private static final String KEY_EMAIL = "saved_email";
    private static final String KEY_PASS = "saved_pass";
    private static final String DEFAULT_URL = "https://download.ssvr.top:8843";

    private SharedPreferences prefs;
    private LinearLayout root;
    private ScrollView scroll;
    private String currentView = "main";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#0d1117"));
        scroll.setFillViewport(true);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(44), dp(20), dp(40));

        scroll.addView(root);
        setContentView(scroll);
        showMainMenu();
    }

    @Override
    public void onBackPressed() {
        if (!"main".equals(currentView)) {
            showMainMenu();
        } else {
            super.onBackPressed();
        }
    }

    // ============ Main Menu ============
    private void showMainMenu() {
        currentView = "main";
        root.removeAllViews();

        // Header
        TextView h = new TextView(this);
        h.setText("设置");
        h.setTextColor(Color.WHITE);
        h.setTextSize(26);
        h.setTypeface(null, Typeface.BOLD);
        h.setPadding(0, 0, 0, dp(6));
        root.addView(h);

        TextView sub = new TextView(this);
        sub.setText("管理你的 FileService 应用");
        sub.setTextColor(Color.parseColor("#8b949e"));
        sub.setTextSize(14);
        sub.setPadding(0, 0, 0, dp(24));
        root.addView(sub);

        // ---- Status card ----
        boolean saved = prefs.getBoolean(KEY_REMEMBER, false) && !prefs.getString(KEY_EMAIL, "").isEmpty();
        boolean cookies = CookieManager.getInstance().hasCookies();
        String statusText = saved ? "● 已保存凭据: " + prefs.getString(KEY_EMAIL, "") :
                cookies ? "● 已登录 (Cookie 有效)" : "● 未登录";
        String statusColor = saved ? "#2ea043" : cookies ? "#2196F3" : "#8b949e";
        addStatusCard(statusText, statusColor);

        // ---- Menu cards ----
        addMenuCard("👤", "账号与登录", "管理自动登录、切换用户", () -> showAccountPage());
        addMenuCard("🌐", "服务器设置", "配置服务器地址", () -> showServerPage());
        addMenuCard("📱", "在线设备", "查看活跃设备和登录记录", () -> showDevicesPage());
        addMenuCard("📂", "下载文件", "管理已下载的文件", () -> showDownloadsPage());
        String verName = "未知";
        try { verName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch(Exception e) {}
        addMenuCard("🔄", "检查更新", "当前版本 v" + verName, () -> checkUpdate());
        addMenuCard("📋", "导出日志", "分享应用运行日志", this::exportLog);
        addMenuCard("ℹ️", "关于", "版本信息与开源许可", this::showAboutPage);

        // Footer
        View sp = new View(this);
        sp.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(sp);

        TextView ver = new TextView(this);
        String appVer = "未知";
        try { appVer = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch(Exception e) {}
        ver.setText("FileService App v" + appVer);
        ver.setTextColor(Color.parseColor("#484f58"));
        ver.setTextSize(11);
        ver.setGravity(Gravity.CENTER);
        ver.setPadding(0, dp(24), 0, dp(8));
        root.addView(ver);
    }

    private void addStatusCard(String text, String color) {
        LinearLayout card = cardBase();
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor(color));
        tv.setTextSize(14);
        card.addView(tv);
        root.addView(card);
        space(12);
    }

    private void addMenuCard(String icon, String title, String desc, Runnable action) {
        LinearLayout card = cardBase();
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setOnClickListener(v -> action.run());

        TextView ic = new TextView(this);
        ic.setText(icon);
        ic.setTextSize(28);
        ic.setPadding(0, 0, dp(14), 0);
        card.addView(ic);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(Color.parseColor("#e6edf3"));
        t.setTextSize(15);
        t.setTypeface(null, Typeface.BOLD);
        info.addView(t);
        TextView d = new TextView(this);
        d.setText(desc);
        d.setTextColor(Color.parseColor("#8b949e"));
        d.setTextSize(12);
        d.setPadding(0, dp(2), 0, 0);
        info.addView(d);
        card.addView(info);

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextColor(Color.parseColor("#484f58"));
        arrow.setTextSize(22);
        card.addView(arrow);
        root.addView(card);
        space(8);
    }

    // ============ Sub Pages ============

    private void showSubPage(String title, Runnable contentBuilder) {
        currentView = title;
        root.removeAllViews();

        // Back button
        LinearLayout backRow = new LinearLayout(this);
        backRow.setOrientation(LinearLayout.HORIZONTAL);
        backRow.setGravity(Gravity.CENTER_VERTICAL);
        backRow.setPadding(0, 0, 0, dp(16));
        TextView back = new TextView(this);
        back.setText("← 返回");
        back.setTextColor(Color.parseColor("#58a6ff"));
        back.setTextSize(15);
        back.setOnClickListener(v -> showMainMenu());
        backRow.addView(back);
        TextView t = new TextView(this);
        t.setText("  " + title);
        t.setTextColor(Color.parseColor("#e6edf3"));
        t.setTextSize(18);
        t.setTypeface(null, Typeface.BOLD);
        backRow.addView(t);
        root.addView(backRow);

        contentBuilder.run();
    }

    private void showAccountPage() {
        showSubPage("账号与登录", () -> {
            CheckBox cb = new CheckBox(this);
            cb.setText("启动时自动登录");
            cb.setTextColor(Color.parseColor("#e6edf3"));
            cb.setChecked(prefs.getBoolean(KEY_REMEMBER, false));
            cb.setOnCheckedChangeListener((b, c) -> prefs.edit().putBoolean(KEY_REMEMBER, c).apply());
            cb.setPadding(0, dp(8), 0, dp(8));
            root.addView(cb);

            EditText emailEt = input("邮箱", prefs.getString(KEY_EMAIL, ""));
            EditText passEt = input("密码", prefs.getString(KEY_PASS, ""));
            passEt.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

            primaryBtn("💾 保存凭据", "#238636", () -> {
                String em = emailEt.getText().toString().trim();
                String pw = passEt.getText().toString().trim();
                if (em.isEmpty()) { toast("请输入邮箱"); return; }
                prefs.edit().putString(KEY_EMAIL, em).putString(KEY_PASS, pw)
                        .putBoolean(KEY_REMEMBER, cb.isChecked()).apply();
                toast("已保存");
            });
            dangerBtn("🚪 切换用户（清除登录）", () -> {
                CookieManager.getInstance().removeAllCookies(null);
                CookieManager.getInstance().flush();
                prefs.edit().remove(KEY_EMAIL).remove(KEY_PASS).putBoolean(KEY_REMEMBER, false).apply();
                toast("已清除，下次启动需重新登录");
            });
        });
    }

    private void showServerPage() {
        showSubPage("服务器设置", () -> {
            EditText et = input("服务器地址", prefs.getString(KEY_SERVER, DEFAULT_URL));
            et.setTextColor(Color.parseColor("#58a6ff"));
            primaryBtn("💾 保存并重启", "#1f6feb", () -> {
                String url = et.getText().toString().trim();
                if (url.isEmpty()) { toast("不能为空"); return; }
                if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
                if (!url.startsWith("http")) url = "https://" + url;
                prefs.edit().putString(KEY_SERVER, url).apply();
                toast("已保存，重启生效");
            });
        });
    }

    private void showDevicesPage() {
        showSubPage("在线设备", () -> {
            TextView status = new TextView(this);
            status.setText("正在加载...");
            status.setTextColor(Color.parseColor("#8b949e"));
            status.setTextSize(13);
            status.setPadding(0, dp(8), 0, dp(8));
            root.addView(status);

            LinearLayout list = new LinearLayout(this);
            list.setOrientation(LinearLayout.VERTICAL);
            root.addView(list);

            new Thread(() -> {
                try {
                    String srv = prefs.getString(KEY_SERVER, DEFAULT_URL);
                    if (srv.endsWith("/")) srv = srv.substring(0, srv.length() - 1);
                    URL url = new URL(srv + "/api/auth/devices");
                    HttpURLConnection c = (HttpURLConnection) url.openConnection();
                    c.setRequestProperty("Cookie", CookieManager.getInstance().getCookie(srv));
                    c.setConnectTimeout(5000);
                    BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String l;
                    while ((l = br.readLine()) != null) sb.append(l);
                    br.close();
                    JSONObject resp = new JSONObject(sb.toString());
                    if (resp.optInt("code") == 0) {
                        JSONArray devices = resp.optJSONObject("data").optJSONArray("devices");
                        runOnUiThread(() -> {
                            root.removeView(status);
                            if (devices == null || devices.length() == 0) {
                                TextView empty = new TextView(this);
                                empty.setText("暂无活跃设备");
                                empty.setTextColor(Color.parseColor("#8b949e"));
                                empty.setPadding(0, dp(20), 0, 0);
                                list.addView(empty);
                                return;
                            }
                            for (int i = 0; i < devices.length(); i++) {
                                try {
                                    JSONObject d = devices.getJSONObject(i);
                                    boolean curr = d.optBoolean("isCurrent");
                                    boolean online = d.optBoolean("online");
                                    addDeviceItem(list, d.optString("device"), d.optString("ip"),
                                            online, curr, d.optString("sid"), d.optString("loginAt"),
                                            d.optString("deviceId"));
                                } catch (Exception e) {}
                            }
                        });
                    } else {
                        runOnUiThread(() -> status.setText("加载失败: " + resp.optString("message")));
                    }
                } catch (Exception e) {
                    runOnUiThread(() -> status.setText("请求失败: " + e.getMessage()));
                }
            }).start();

            // Login history
            space(8);
            TextView htitle = new TextView(this);
            htitle.setText("最近登录记录");
            htitle.setTextColor(Color.parseColor("#e6edf3"));
            htitle.setTextSize(15);
            htitle.setTypeface(null, Typeface.BOLD);
            htitle.setPadding(0, dp(8), 0, dp(4));
            root.addView(htitle);
            TextView hlist = new TextView(this);
            hlist.setText("加载中...");
            hlist.setTextColor(Color.parseColor("#8b949e"));
            hlist.setTextSize(12);
            root.addView(hlist);

            new Thread(() -> {
                try {
                    String srv = prefs.getString(KEY_SERVER, DEFAULT_URL);
                    if (srv.endsWith("/")) srv = srv.substring(0, srv.length() - 1);
                    URL url = new URL(srv + "/api/auth/login-history");
                    HttpURLConnection c = (HttpURLConnection) url.openConnection();
                    c.setRequestProperty("Cookie", CookieManager.getInstance().getCookie(srv));
                    BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String l;
                    while ((l = br.readLine()) != null) sb.append(l);
                    br.close();
                    JSONObject resp = new JSONObject(sb.toString());
                    if (resp.optInt("code") == 0) {
                        JSONArray logs = resp.optJSONObject("data").optJSONArray("history");
                        runOnUiThread(() -> {
                            if (logs == null || logs.length() == 0) { hlist.setText("暂无记录"); return; }
                            StringBuilder t = new StringBuilder();
                            for (int i = 0; i < Math.min(logs.length(), 15); i++) {
                                JSONObject lo = logs.optJSONObject(i);
                                if (lo == null) continue;
                                String lt = lo.optString("created_at", "").replace("T", " ").substring(0, 16);
                                String ls = "success".equals(lo.optString("status")) ? "✅" : "❌";
                                t.append(ls).append(" ").append(lt).append("  IP:").append(lo.optString("ip", "-")).append("\n");
                            }
                            hlist.setText(t.length() > 0 ? t.toString().trim() : "暂无记录");
                        });
                    }
                } catch (Exception e) { runOnUiThread(() -> hlist.setText("加载失败")); }
            }).start();
        });
    }

    private void addDeviceItem(LinearLayout parent, String device, String ip, boolean online, boolean isCurrent, String sid, String time, String deviceId) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(8), dp(10));
        row.setBackgroundColor(Color.parseColor("#161b22"));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.topMargin = dp(4);
        row.setLayoutParams(rp);

        TextView icon = new TextView(this);
        icon.setText(isCurrent ? "●" : "○");
        icon.setTextColor(Color.parseColor(online ? "#2ea043" : "#8b949e"));
        icon.setTextSize(14);
        icon.setPadding(0, 0, dp(10), 0);
        row.addView(icon);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView dt = new TextView(this);
        dt.setText(device + (isCurrent ? " (当前)" : ""));
        dt.setTextColor(Color.WHITE);
        dt.setTextSize(13);
        info.addView(dt);
        TextView di = new TextView(this);
        String did = !deviceId.isEmpty() ? "  设备: " + deviceId : "";
        di.setText("IP: " + ip + did + "  " + (online ? "在线" : "离线"));
        di.setTextColor(Color.parseColor("#8b949e"));
        di.setTextSize(11);
        info.addView(di);
        row.addView(info);

        if (!isCurrent) {
            Button kick = new Button(this);
            kick.setText("下线");
            kick.setTextColor(Color.parseColor("#f85149"));
            kick.setTextSize(11);
            kick.setAllCaps(false);
            kick.setBackgroundColor(Color.parseColor("#30363d"));
            kick.setPadding(dp(8), dp(4), dp(8), dp(4));
            kick.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("强制下线")
                    .setMessage("确定要下线 " + device + " (" + ip + ") 吗？")
                    .setPositiveButton("确定", (diag, w) -> forceLogout(sid))
                    .setNegativeButton("取消", null).show());
            row.addView(kick);
        }
        parent.addView(row);
    }

    private void forceLogout(String sid) {
        new Thread(() -> {
            try {
                String srv = prefs.getString(KEY_SERVER, DEFAULT_URL);
                if (srv.endsWith("/")) srv = srv.substring(0, srv.length() - 1);
                URL url = new URL(srv + "/api/auth/devices/logout");
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                c.setRequestProperty("Cookie", CookieManager.getInstance().getCookie(srv));
                String csrf = null;
                try { csrf = prefs.getString("csrfToken_" + srv, ""); } catch (Exception e) {}
                if (csrf != null && !csrf.isEmpty()) c.setRequestProperty("X-CSRF-Token", csrf);
                c.getOutputStream().write(("{\"sid\":\"" + sid + "\"}").getBytes());
                c.getOutputStream().flush();
                c.getOutputStream().close();
                int code = c.getResponseCode();
                runOnUiThread(() -> {
                    if (code == 200) { toast("已下线"); showDevicesPage(); }
                    else { toast("操作失败"); }
                });
            } catch (Exception e) { runOnUiThread(() -> toast("请求失败")); }
        }).start();
    }

    private void showDownloadsPage() {
        showSubPage("下载文件", () -> {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File[] files = dir.listFiles();
            if (files == null || files.length == 0) {
                TextView empty = new TextView(this);
                empty.setText("暂无下载文件");
                empty.setTextColor(Color.parseColor("#8b949e"));
                empty.setPadding(0, dp(20), 0, 0);
                root.addView(empty);
                return;
            }
            Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (File f : files) {
                addDownloadItem(f);
            }
        });
    }

    private void addDownloadItem(File f) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(10), dp(8), dp(10));
        row.setBackgroundColor(Color.parseColor("#161b22"));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.topMargin = dp(4);
        row.setLayoutParams(rp);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView n = new TextView(this);
        n.setText(f.getName());
        n.setTextColor(Color.WHITE);
        n.setTextSize(13);
        n.setMaxLines(1);
        info.addView(n);
        TextView d = new TextView(this);
        d.setText(formatSize(f.length()) + " · " + new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date(f.lastModified())));
        d.setTextColor(Color.parseColor("#8b949e"));
        d.setTextSize(11);
        info.addView(d);
        row.addView(info);

        Button open = new Button(this);
        open.setText("打开");
        open.setTextColor(Color.WHITE);
        open.setTextSize(11);
        open.setAllCaps(false);
        open.setBackgroundColor(Color.parseColor("#1f6feb"));
        open.setPadding(dp(10), dp(4), dp(10), dp(4));
        open.setOnClickListener(v -> {
            try {
                Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
                String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                        f.getName().substring(f.getName().lastIndexOf('.') + 1));
                Intent oi = new Intent(Intent.ACTION_VIEW);
                oi.setDataAndType(uri, mime != null ? mime : "*/*");
                oi.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(oi, "打开"));
            } catch (Exception e) { toast("无法打开"); }
        });
        row.addView(open);

        Button share = new Button(this);
        share.setText("分享");
        share.setTextColor(Color.WHITE);
        share.setTextSize(11);
        share.setAllCaps(false);
        share.setBackgroundColor(Color.parseColor("#30363d"));
        share.setPadding(dp(10), dp(4), dp(10), dp(4));
        share.setOnClickListener(v -> {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
            Intent si = new Intent(Intent.ACTION_SEND);
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                    f.getName().substring(f.getName().lastIndexOf('.') + 1));
            si.setType(mime != null ? mime : "*/*");
            si.putExtra(Intent.EXTRA_STREAM, uri);
            si.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(si, "分享"));
        });
        row.addView(share);

        Button del = new Button(this);
        del.setText("删");
        del.setTextColor(Color.parseColor("#f85149"));
        del.setTextSize(11);
        del.setAllCaps(false);
        del.setBackgroundColor(Color.TRANSPARENT);
        del.setPadding(dp(8), dp(4), dp(8), dp(4));
        del.setOnClickListener(v -> {
            if (f.delete()) { toast("已删除"); showDownloadsPage(); }
            else { toast("删除失败"); }
        });
        row.addView(del);

        root.addView(row);
    }

    private void showAboutPage() {
        showSubPage("关于", () -> {
            String appVer = "未知";
            try { appVer = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch(Exception e) {}
            int appCode = 0;
            try { appCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode; } catch(Exception e) {}

            // App 信息卡片
            LinearLayout infoCard = cardBase();
            infoCard.setPadding(dp(20), dp(20), dp(20), dp(20));
            infoCard.setOrientation(LinearLayout.VERTICAL);

            // 名称 + 版本
            TextView name = new TextView(this);
            name.setText("FMS 文件管理系统");
            name.setTextColor(Color.WHITE);
            name.setTextSize(18);
            name.setTypeface(null, Typeface.BOLD);
            name.setGravity(Gravity.CENTER);
            infoCard.addView(name);

            TextView ver = new TextView(this);
            ver.setText("App v" + appVer + " (" + appCode + ")");
            ver.setTextColor(Color.parseColor("#58a6ff"));
            ver.setTextSize(13);
            ver.setGravity(Gravity.CENTER);
            ver.setPadding(0, dp(4), 0, dp(12));
            infoCard.addView(ver);

            // 分隔线
            View div = new View(this);
            div.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
            div.setBackgroundColor(Color.parseColor("#21262d"));
            infoCard.addView(div);

            // 功能列表
            String[][] features = {
                {"🔐", "端到端加密", "AES-256-GCM 文件加密"},
                {"🔗", "文件分享", "链接分享 + 提取码 + 下载限制"},
                {"📡", "WebDAV", "个人/公共目录 WebDAV 支持"},
                {"📦", "存储管理", "多镜像 + 加权分配 + 迁移"},
            };
            for (String[] f : features) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, dp(10), 0, dp(10));
                TextView ic = new TextView(this);
                ic.setText(f[0]);
                ic.setTextSize(18);
                ic.setPadding(0, 0, dp(10), 0);
                row.addView(ic);
                LinearLayout col = new LinearLayout(this);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                TextView t = new TextView(this);
                t.setText(f[1]);
                t.setTextColor(Color.parseColor("#e6edf3"));
                t.setTextSize(13);
                t.setTypeface(null, Typeface.BOLD);
                col.addView(t);
                TextView d = new TextView(this);
                d.setText(f[2]);
                d.setTextColor(Color.parseColor("#8b949e"));
                d.setTextSize(11);
                col.addView(d);
                row.addView(col);
                infoCard.addView(row);
            }
            root.addView(infoCard);
            space(12);

            // GitHub 链接
            addLinkCard("服务端源码", "Antruly/FMS-Service", "https://github.com/Antruly/FMS-Service");
            addLinkCard("App 源码", "Antruly/FMS-Service-app", "https://github.com/Antruly/FMS-Service-app");

            // 底部版权
            TextView copy = new TextView(this);
            copy.setText("© 2026 FMS 文件管理系统\nMIT License");
            copy.setTextColor(Color.parseColor("#484f58"));
            copy.setTextSize(11);
            copy.setGravity(Gravity.CENTER);
            copy.setPadding(0, dp(20), 0, dp(8));
            root.addView(copy);
        });
    }

    private void addLinkCard(String label, String repo, String url) {
        LinearLayout card = cardBase();
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(Color.parseColor("#e6edf3"));
        t.setTextSize(14);
        t.setTypeface(null, Typeface.BOLD);
        info.addView(t);
        TextView d = new TextView(this);
        d.setText("github.com/" + repo);
        d.setTextColor(Color.parseColor("#58a6ff"));
        d.setTextSize(12);
        d.setPadding(0, dp(2), 0, 0);
        info.addView(d);
        card.addView(info);

        // GitHub 图标
        TextView gh = new TextView(this);
        gh.setText("⎋");
        gh.setTextColor(Color.parseColor("#8b949e"));
        gh.setTextSize(20);
        gh.setPadding(dp(8), 0, 0, 0);
        card.addView(gh);

        root.addView(card);
        space(8);
    }

    // ============ Helpers ============

    private void checkUpdate() {
        toast("正在检查...");
        new Thread(() -> {
            try {
                String srv = prefs.getString(KEY_SERVER, DEFAULT_URL);
                if (srv.endsWith("/")) srv = srv.substring(0, srv.length() - 1);
                URL url = new URL(srv + "/api/version/latest");
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setConnectTimeout(5000);
                BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String l;
                while ((l = br.readLine()) != null) sb.append(l);
                br.close();
                JSONObject j = new JSONObject(sb.toString());
                if (j.optInt("code") == 0 && j.has("data")) {
                    final JSONObject dd = j.getJSONObject("data");
                    final String fsrv = srv;
                    int nc = dd.optInt("versionCode", 0);
                    int currentCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
                    if (nc > currentCode) {
                        final String fmsg = dd.optString("notes", "(无更新日志)") +
                            "\n\nv" + dd.optString("version") + " · " + Math.round(dd.optLong("size") / 1024f / 1024f) + " MB";
                        runOnUiThread(() -> new AlertDialog.Builder(this)
                                .setTitle("发现新版本 v" + dd.optString("version"))
                                .setMessage(fmsg)
                                .setPositiveButton("下载", (di, w) -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(fsrv + dd.optString("url")))))
                                .setNegativeButton("取消", null).show());
                    } else { runOnUiThread(() -> toast("已是最新版本")); }
                }
            } catch (Exception e) { runOnUiThread(() -> toast("检查失败")); }
        }).start();
    }

    private void exportLog() {
        String path = AppLog.export();
        if (path != null) {
            Intent si = new Intent(Intent.ACTION_SEND);
            si.setType("text/plain");
            si.putExtra(Intent.EXTRA_TEXT, AppLog.getRecent());
            startActivity(Intent.createChooser(si, "分享日志"));
        } else { toast("导出失败"); }
    }

    private LinearLayout cardBase() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#161b22"));
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), Color.parseColor("#21262d"));
        c.setBackground(bg);
        return c;
    }

    private EditText input(String hint, String value) {
        EditText et = new EditText(this);
        et.setText(value);
        et.setHint(hint);
        et.setHintTextColor(Color.parseColor("#484f58"));
        et.setTextColor(Color.WHITE);
        et.setBackgroundColor(Color.parseColor("#161b22"));
        et.setPadding(dp(14), dp(12), dp(14), dp(12));
        et.setTextSize(14);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#161b22"));
        bg.setCornerRadius(dp(8));
        bg.setStroke(dp(1), Color.parseColor("#21262d"));
        et.setBackground(bg);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ep.topMargin = dp(8);
        et.setLayoutParams(ep);
        root.addView(et);
        return et;
    }

    private void primaryBtn(String text, String color, Runnable action) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setTextSize(14);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(color));
        bg.setCornerRadius(dp(8));
        b.setBackground(bg);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        bp.topMargin = dp(8);
        b.setLayoutParams(bp);
        b.setOnClickListener(v -> action.run());
        root.addView(b);
    }

    private void dangerBtn(String text, Runnable action) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.parseColor("#f85149"));
        b.setAllCaps(false);
        b.setTextSize(13);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#161b22"));
        bg.setStroke(dp(1), Color.parseColor("#30363d"));
        bg.setCornerRadius(dp(8));
        b.setBackground(bg);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        bp.topMargin = dp(6);
        b.setLayoutParams(bp);
        b.setOnClickListener(v -> action.run());
        root.addView(b);
    }

    private void space(int dp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(dp)));
        root.addView(v);
    }

    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), "KMGTPE".charAt(exp - 1));
    }

    private int dp(int px) { return (int) (px * getResources().getDisplayMetrics().density); }
}
