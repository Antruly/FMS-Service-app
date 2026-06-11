package com.fileservice.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.ResultPoint;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;
import com.journeyapps.barcodescanner.ViewfinderView;

import java.util.Collections;

public class ScannerActivity extends Activity {

    private DecoratedBarcodeView barcodeView;
    private View overlayView;
    private boolean scanned = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // Full screen
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // Dark immersive
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        // Barcode scanner
        barcodeView = new DecoratedBarcodeView(this);
        barcodeView.getBarcodeView().setDecoderFactory(
                new DefaultDecoderFactory(Collections.singletonList(BarcodeFormat.QR_CODE)));
        barcodeView.setStatusText("");
        root.addView(barcodeView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Custom overlay
        overlayView = createOverlay();
        root.addView(overlayView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);

        barcodeView.decodeContinuous(new BarcodeCallback() {
            @Override
            public void barcodeResult(BarcodeResult result) {
                if (scanned) return;
                String text = result.getText();
                if (text != null && !text.isEmpty()) {
                    scanned = true;
                    try {
                        android.os.Vibrator v = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
                        if (v != null) v.vibrate(80);
                    } catch (Exception e) {}
                    Intent data = new Intent();
                    data.putExtra("SCAN_RESULT", text);
                    setResult(RESULT_OK, data);
                    finish();
                }
            }
            @Override
            public void possibleResultPoints(java.util.List<ResultPoint> points) {}
        });
    }

    private View createOverlay() {
        FrameLayout overlay = new FrameLayout(this);

        // Top gradient bar
        View topBar = new View(this);
        topBar.setBackgroundColor(Color.argb(120, 0, 0, 0));
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        tp.gravity = Gravity.TOP;
        overlay.addView(topBar, tp);

        // Title
        TextView title = new TextView(this);
        title.setText("扫一扫");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        FrameLayout.LayoutParams tip = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tip.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        tip.topMargin = dp(16);
        overlay.addView(title, tip);

        // Close button
        Button closeBtn = new Button(this);
        closeBtn.setText("✕");
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setTextSize(18);
        closeBtn.setBackgroundColor(Color.argb(80, 255, 255, 255));
        closeBtn.setAllCaps(false);
        closeBtn.setPadding(dp(10), dp(4), dp(10), dp(4));
        FrameLayout.LayoutParams cbp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cbp.gravity = Gravity.TOP | Gravity.END;
        cbp.topMargin = dp(12);
        cbp.rightMargin = dp(12);
        closeBtn.setLayoutParams(cbp);
        closeBtn.setOnClickListener(v -> { setResult(RESULT_CANCELED); finish(); });
        overlay.addView(closeBtn, cbp);

        // Bottom hint
        LinearLayout bottomBox = new LinearLayout(this);
        bottomBox.setOrientation(LinearLayout.VERTICAL);
        bottomBox.setGravity(Gravity.CENTER);
        bottomBox.setBackgroundColor(Color.argb(120, 0, 0, 0));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.gravity = Gravity.BOTTOM;
        overlay.addView(bottomBox, bp);

        TextView hint = new TextView(this);
        hint.setText("将二维码放入框内，即可自动扫描");
        hint.setTextColor(Color.WHITE);
        hint.setTextSize(15);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(20), dp(20), dp(20), dp(30));
        bottomBox.addView(hint);

        // Flashlight hint
        TextView flashHint = new TextView(this);
        flashHint.setText("💡 点击屏幕可开关闪光灯");
        flashHint.setTextColor(Color.argb(180, 255, 255, 255));
        flashHint.setTextSize(12);
        flashHint.setGravity(Gravity.CENTER);
        flashHint.setPadding(0, 0, 0, dp(10));
        bottomBox.addView(flashHint);

        // Tap anywhere to toggle flashlight
        overlay.setOnClickListener(v -> {
            if (barcodeView != null) {
                try { barcodeView.setTorchOff(); } catch (Exception e) {}
                try { barcodeView.setTorchOn(); } catch (Exception e) {}
            }
        });

        return overlay;
    }

    @Override protected void onResume() { super.onResume(); if (barcodeView != null) barcodeView.resume(); }
    @Override protected void onPause() { super.onPause(); if (barcodeView != null) barcodeView.pause(); }
    @Override protected void onDestroy() { super.onDestroy(); scanned = true; }

    private int dp(int px) { return (int)(px * getResources().getDisplayMetrics().density); }
}
