package com.fileservice.app;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

public class UploadActivity extends AppCompatActivity {

    private static final String PREFS = "FileServicePrefs";
    private static final String KEY_SERVER = "server_url";
    private static final String DEFAULT_SERVER = "https://download.ssvr.top:8843";

    private String serverUrl;
    private Uri fileUri;
    private String fileName;
    private long fileSize;
    private String cookieStr;
    private List<DirInfo> dirs = new ArrayList<>();
    private int selectedDirId = 0;
    private TextView statusText;
    private LinearLayout dirList;
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        serverUrl = prefs.getString(KEY_SERVER, DEFAULT_SERVER);
        if (serverUrl.endsWith("/")) serverUrl = serverUrl.substring(0, serverUrl.length() - 1);

        Intent intent = getIntent();
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) {
            toast("无效的分享请求");
            finish();
            return;
        }

        fileUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (fileUri == null) {
            // Try reading text from clipboard-style share
            toast("暂不支持文本分享");
            finish();
            return;
        }

        fileName = getFileName(fileUri);
        fileSize = getFileSize(fileUri);

        // Get cookies from WebView
        android.webkit.CookieManager cm = android.webkit.CookieManager.getInstance();
        cookieStr = cm.getCookie(serverUrl);
        if (cookieStr == null || cookieStr.isEmpty()) {
            toast("请先在 FileService 中登录");
            finish();
            return;
        }

        setupUI();
        fetchDirectories();
    }

    private void setupUI() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#0d1117"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(40), dp(20), dp(40));

        TextView title = new TextView(this);
        title.setText("上传文件到 FileService");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        root.addView(title);

        TextView info = new TextView(this);
        info.setText("📄 " + fileName + "\n📦 " + formatSize(fileSize));
        info.setTextColor(Color.parseColor("#8b949e"));
        info.setTextSize(14);
        info.setPadding(0, dp(8), 0, dp(16));
        root.addView(info);

        statusText = new TextView(this);
        statusText.setText("正在加载目录...");
        statusText.setTextColor(Color.parseColor("#d29922"));
        statusText.setTextSize(13);
        root.addView(statusText);

        dirList = new LinearLayout(this);
        dirList.setOrientation(LinearLayout.VERTICAL);
        root.addView(dirList);

        Button cancelBtn = new Button(this);
        cancelBtn.setText("取消");
        cancelBtn.setTextColor(Color.WHITE);
        cancelBtn.setBackgroundColor(Color.parseColor("#30363d"));
        cancelBtn.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        bp.topMargin = dp(16);
        cancelBtn.setLayoutParams(bp);
        root.addView(cancelBtn);

        scroll.addView(root);
        setContentView(scroll);
    }

    private void fetchDirectories() {
        new Thread(() -> {
            try {
                URL url = new URL(serverUrl + "/api/files/dirs");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Cookie", cookieStr);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                int code = conn.getResponseCode();
                if (code == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();
                    JSONObject resp = new JSONObject(sb.toString());
                    if (resp.optInt("code") == 0) {
                        JSONArray arr = resp.optJSONArray("data");
                        if (arr != null) {
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject d = arr.getJSONObject(i);
                                dirs.add(new DirInfo(d.optInt("id"), d.optString("name")));
                            }
                        }
                        handler.post(() -> showDirList());
                        return;
                    }
                }
                handler.post(() -> statusText.setText("加载目录失败 (HTTP " + code + ")"));
            } catch (Exception e) {
                handler.post(() -> statusText.setText("加载失败: " + e.getMessage()));
            }
        }).start();
    }

    private void showDirList() {
        dirList.removeAllViews();
        // Root directory option
        addDirItem(0, "📁 根目录");
        for (DirInfo d : dirs) {
            addDirItem(d.id, "📁 " + d.name);
        }
        statusText.setText("请选择目标目录，然后点击上传");
        statusText.setTextColor(Color.parseColor("#8b949e"));

        // Upload button
        Button uploadBtn = new Button(this);
        uploadBtn.setText("⬆ 上传到选中目录");
        uploadBtn.setTextColor(Color.WHITE);
        uploadBtn.setBackgroundColor(Color.parseColor("#238636"));
        uploadBtn.setOnClickListener(v -> startUpload());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        bp.topMargin = dp(12);
        uploadBtn.setLayoutParams(bp);
        dirList.addView(uploadBtn);
    }

    private void addDirItem(int id, String label) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setTextColor(id == selectedDirId ? Color.WHITE : Color.parseColor("#8b949e"));
        btn.setBackgroundColor(id == selectedDirId ? Color.parseColor("#1f6feb") : Color.parseColor("#161b22"));
        btn.setAllCaps(false);
        btn.setTextSize(14);
        btn.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        btn.setPadding(dp(12), 0, dp(12), 0);
        btn.setOnClickListener(v -> {
            selectedDirId = id;
            // Refresh UI
            for (int i = 0; i < dirList.getChildCount(); i++) {
                View child = dirList.getChildAt(i);
                if (child instanceof Button && child.getTag() instanceof Integer) {
                    int cid = (Integer) child.getTag();
                    child.setBackgroundColor(cid == selectedDirId ? Color.parseColor("#1f6feb") : Color.parseColor("#161b22"));
                    ((Button) child).setTextColor(cid == selectedDirId ? Color.WHITE : Color.parseColor("#8b949e"));
                }
            }
        });
        btn.setTag(id);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        bp.topMargin = dp(4);
        btn.setLayoutParams(bp);
        dirList.addView(btn);
    }

    private void startUpload() {
        ProgressDialog pd = new ProgressDialog(this);
        pd.setTitle("上传中");
        pd.setMessage("正在上传 " + fileName + "...");
        pd.setCancelable(false);
        pd.show();

        new Thread(() -> {
            try {
                // Copy file from URI to temp
                File tmpFile = new File(getCacheDir(), fileName);
                InputStream is = getContentResolver().openInputStream(fileUri);
                FileOutputStream fos = new FileOutputStream(tmpFile);
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) fos.write(buf);
                is.close();
                fos.close();

                // Build multipart request
                String boundary = "----FileServiceUpload" + System.currentTimeMillis();
                URL url = new URL(serverUrl + "/api/files/upload");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                conn.setRequestProperty("Cookie", cookieStr);
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(60000);

                OutputStream os = new BufferedOutputStream(conn.getOutputStream());

                // dir_id field
                writeField(os, boundary, "dir_id", String.valueOf(selectedDirId));

                // file field
                writeFile(os, boundary, "file", fileName, tmpFile);

                // End boundary
                os.write(("--" + boundary + "--\r\n").getBytes());
                os.flush();
                os.close();

                int code = conn.getResponseCode();
                BufferedReader br = new BufferedReader(new InputStreamReader(
                        code == 200 ? conn.getInputStream() : conn.getErrorStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                JSONObject resp = new JSONObject(sb.toString());
                String msg = resp.optString("message", "");

                tmpFile.delete();

                handler.post(() -> {
                    pd.dismiss();
                    if (resp.optInt("code") == 0) {
                        toast("✅ 上传成功: " + fileName);
                        setResult(RESULT_OK);
                        finish();
                    } else {
                        toast("上传失败: " + (msg.isEmpty() ? "HTTP " + code : msg));
                    }
                });
            } catch (Exception e) {
                handler.post(() -> {
                    pd.dismiss();
                    toast("上传失败: " + e.getMessage());
                });
            }
        }).start();
    }

    private void writeField(OutputStream os, String boundary, String name, String value) throws Exception {
        os.write(("--" + boundary + "\r\n").getBytes());
        os.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes());
        os.write(value.getBytes());
        os.write("\r\n".getBytes());
    }

    private void writeFile(OutputStream os, String boundary, String fieldName, String fileName, File file) throws Exception {
        os.write(("--" + boundary + "\r\n").getBytes());
        os.write(("Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + fileName + "\"\r\n").getBytes());
        os.write(("Content-Type: application/octet-stream\r\n\r\n").getBytes());
        FileInputStream fis = new FileInputStream(file);
        byte[] buf = new byte[8192];
        int n;
        while ((n = fis.read(buf)) != -1) os.write(buf, 0, n);
        fis.close();
        os.write("\r\n".getBytes());
    }

    private String getFileName(Uri uri) {
        String name = "unknown_file";
        try {
            Cursor c = getContentResolver().query(uri, null, null, null, null);
            if (c != null) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0 && c.moveToFirst()) name = c.getString(idx);
                c.close();
            }
        } catch (Exception e) {}
        return name;
    }

    private long getFileSize(Uri uri) {
        long size = 0;
        try {
            Cursor c = getContentResolver().query(uri, null, null, null, null);
            if (c != null) {
                int idx = c.getColumnIndex(OpenableColumns.SIZE);
                if (idx >= 0 && c.moveToFirst()) size = c.getLong(idx);
                c.close();
            }
        } catch (Exception e) {}
        return size;
    }

    private static class DirInfo {
        int id; String name;
        DirInfo(int id, String name) { this.id = id; this.name = name; }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int)(Math.log(bytes) / Math.log(1024));
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), "KMGTPE".charAt(exp-1));
    }

    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_LONG).show(); }
    private int dp(int px) { return (int)(px * getResources().getDisplayMetrics().density); }
}
