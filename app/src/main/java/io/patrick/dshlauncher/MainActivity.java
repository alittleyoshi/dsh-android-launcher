package io.patrick.dshlauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
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

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQUEST_TERMUX_PERMISSION = 2101;
    private static final String DSH_URL = "http://127.0.0.1:3080";
    private static final int START_TIMEOUT_MS = 25000;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private FrameLayout root;
    private WebView webView;
    private LinearLayout statusPanel;
    private TextView statusText;
    private ProgressBar progressBar;
    private Button actionButton;
    private volatile boolean stopRequested = false;
    private volatile boolean destroyed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();

        try {
            startService(new Intent(this, TaskSentinelService.class));
        } catch (Exception ignored) {
        }

        beginStartup();
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(14, 16, 21));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                view.setPadding(
                        insets.getSystemWindowInsetLeft(),
                        insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(),
                        insets.getSystemWindowInsetBottom());
            }
            return insets;
        });

        webView = new WebView(this);
        webView.setVisibility(View.GONE);
        webView.setBackgroundColor(Color.rgb(14, 16, 21));
        configureWebView(webView);
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        statusPanel = new LinearLayout(this);
        statusPanel.setOrientation(LinearLayout.VERTICAL);
        statusPanel.setGravity(Gravity.CENTER);
        int pad = dp(28);
        statusPanel.setPadding(pad, pad, pad, pad);

        ProgressBar spinner = new ProgressBar(this);
        progressBar = spinner;
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        spinnerParams.bottomMargin = dp(22);
        statusPanel.addView(spinner, spinnerParams);

        TextView title = new TextView(this);
        title.setText("DeepSeek Harness");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = dp(10);
        statusPanel.addView(title, titleParams);

        statusText = new TextView(this);
        statusText.setText("正在启动 Harness…");
        statusText.setTextColor(Color.rgb(158, 164, 176));
        statusText.setTextSize(15);
        statusText.setGravity(Gravity.CENTER);
        statusPanel.addView(statusText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        actionButton = new Button(this);
        actionButton.setVisibility(View.GONE);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonParams.topMargin = dp(20);
        statusPanel.addView(actionButton, buttonParams);

        root.addView(statusPanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    private void configureWebView(WebView view) {
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        view.setWebChromeClient(new WebChromeClient());
        view.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String host = uri.getHost();
                if ("127.0.0.1".equals(host) || "localhost".equals(host)) {
                    return false;
                }
                openExternal(uri);
                return true;
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Uri uri = Uri.parse(url);
                String host = uri.getHost();
                if ("127.0.0.1".equals(host) || "localhost".equals(host)) {
                    return false;
                }
                openExternal(uri);
                return true;
            }
        });
    }

    private void beginStartup() {
        showLoading("正在检查 Termux…");

        if (!TermuxBridge.isTermuxInstalled(this)) {
            showError("未检测到 Termux。请先安装并配置 Termux，然后重新打开本应用。",
                    "重试", v -> beginStartup());
            return;
        }

        if (checkSelfPermission(TermuxBridge.RUN_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{TermuxBridge.RUN_PERMISSION}, REQUEST_TERMUX_PERMISSION);
            return;
        }

        launchBackendAndWait();
    }

    private void launchBackendAndWait() {
        showLoading("正在启动 Harness 后端…");
        try {
            TermuxBridge.startBackend(this);
        } catch (SecurityException e) {
            showPermissionError();
            return;
        } catch (Exception e) {
            showError("无法调用 Termux：" + safeMessage(e), "重试", v -> beginStartup());
            return;
        }

        executor.execute(() -> {
            long deadline = System.currentTimeMillis() + START_TIMEOUT_MS;
            while (!destroyed && System.currentTimeMillis() < deadline) {
                if (isBackendReady()) {
                    mainHandler.post(this::showHarness);
                    return;
                }
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            if (!destroyed) {
                mainHandler.post(() -> showError(
                        "后端在 25 秒内没有响应。请到 Termux 查看 ~/.dsh-android/dsh.log。",
                        "重试", v -> launchBackendAndWait()));
            }
        });
    }

    private boolean isBackendReady() {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(DSH_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(500);
            connection.setReadTimeout(800);
            connection.setInstanceFollowRedirects(false);
            int status = connection.getResponseCode();
            return status >= 200 && status < 500;
        } catch (IOException ignored) {
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void showHarness() {
        if (destroyed) return;
        statusPanel.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        webView.loadUrl(DSH_URL);
    }

    private void showLoading(String text) {
        webView.setVisibility(View.GONE);
        statusPanel.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.VISIBLE);
        statusText.setText(text);
        actionButton.setVisibility(View.GONE);
    }

    private void showError(String message, String buttonText, View.OnClickListener listener) {
        webView.setVisibility(View.GONE);
        statusPanel.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.GONE);
        statusText.setText(message);
        actionButton.setText(buttonText);
        actionButton.setOnClickListener(listener);
        actionButton.setVisibility(View.VISIBLE);
    }

    private void showPermissionError() {
        showError(
                "需要“Run commands in Termux environment”权限，才能启动 Harness 后端。",
                "打开应用设置",
                v -> openOwnAppSettings());
    }

    private void openOwnAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void openExternal(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException ignored) {
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_TERMUX_PERMISSION) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            launchBackendAndWait();
        } else {
            showPermissionError();
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.getVisibility() == View.VISIBLE && webView.canGoBack()) {
            webView.goBack();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("退出 DeepSeek Harness？")
                .setMessage("退出应用将同时关闭 Termux 中的 Harness 后端。")
                .setNegativeButton("取消", null)
                .setPositiveButton("退出", (dialog, which) -> exitAndStopBackend())
                .show();
    }

    private void exitAndStopBackend() {
        requestStopBackend();
        finishAndRemoveTask();
    }

    private void requestStopBackend() {
        if (stopRequested) return;
        stopRequested = true;
        try {
            TermuxBridge.stopBackend(this);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        if (isFinishing()) requestStopBackend();
        executor.shutdownNow();
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null ? throwable.getClass().getSimpleName() : message;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
