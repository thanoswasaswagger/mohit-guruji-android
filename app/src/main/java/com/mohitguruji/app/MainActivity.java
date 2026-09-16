package com.mohitguruji.app;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.MimeTypeMap;
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

import java.util.Locale;

public class MainActivity extends Activity {
    private static final String HOME_URL = "https://www.mohitguruji.com/home";
    private static final String HOST = "www.mohitguruji.com";
    private static final int FILE_CHOOSER_REQUEST = 4242;

    private WebView webView;
    private ProgressBar progressBar;
    private FrameLayout splash;
    private LinearLayout offlineCard;
    private ValueCallback<Uri[]> fileCallback;
    private long lastBackPressed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(Color.rgb(194, 65, 12));
        getWindow().setNavigationBarColor(Color.rgb(17, 24, 39));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        configureWebView(webView);
        root.addView(webView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(249, 115, 22)));
        progressBar.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(255, 237, 213)));
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
        progressParams.gravity = Gravity.TOP;
        root.addView(progressBar, progressParams);

        offlineCard = buildOfflineCard();
        offlineCard.setVisibility(View.GONE);
        FrameLayout.LayoutParams offlineParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        offlineParams.gravity = Gravity.CENTER;
        offlineParams.setMargins(dp(24), 0, dp(24), 0);
        root.addView(offlineCard, offlineParams);

        splash = buildSplash();
        root.addView(splash, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        if (savedInstanceState != null) webView.restoreState(savedInstanceState); else loadHome();
    }

    private void configureWebView(WebView view) {
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUserAgentString(settings.getUserAgentString() + " MohitGurujiAndroid/1.0");

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(view, true);

        view.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) { return handleUri(request.getUrl()); }
            @Override public void onPageFinished(WebView v, String url) {
                super.onPageFinished(v, url);
                splash.animate().alpha(0f).setDuration(260).withEndAction(() -> splash.setVisibility(View.GONE)).start();
                offlineCard.setVisibility(View.GONE);
                progressBar.setVisibility(View.GONE);
                injectPolish(v);
            }
            @Override public void onReceivedError(WebView v, WebResourceRequest request, android.webkit.WebResourceError error) {
                super.onReceivedError(v, request, error);
                if (request.isForMainFrame()) showOffline();
            }
        });

        view.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView v, int newProgress) {
                progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
                progressBar.setProgress(newProgress);
            }
            @Override public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = cb;
                try {
                    startActivityForResult(params.createIntent(), FILE_CHOOSER_REQUEST);
                    return true;
                } catch (ActivityNotFoundException e) {
                    fileCallback = null;
                    Toast.makeText(MainActivity.this, "No compatible file picker found.", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        });

        view.setDownloadListener(new DownloadListener() {
            @Override public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                enqueueDownload(url, userAgent, mimetype);
            }
        });
    }

    private boolean handleUri(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (scheme.equals("http") || scheme.equals("https")) {
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (host.equals(HOST) || host.equals("mohitguruji.com") || host.endsWith("googleusercontent.com") || host.endsWith("google.com")) return false;
            openExternal(uri);
            return true;
        }
        openExternal(uri);
        return true;
    }

    private void openExternal(Uri uri) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
        catch (Exception e) { Toast.makeText(this, "Unable to open this link.", Toast.LENGTH_SHORT).show(); }
    }

    private void enqueueDownload(String url, String userAgent, String mimeType) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setMimeType(mimeType);
            request.addRequestHeader("User-Agent", userAgent);
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null) request.addRequestHeader("Cookie", cookie);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
            String filename = "MohitGuruji_" + System.currentTimeMillis() + (extension == null ? "" : "." + extension);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            manager.enqueue(request);
            Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show();
        } catch (Exception e) { openExternal(Uri.parse(url)); }
    }

    private void injectPolish(WebView v) {
        String script = "(function(){try{document.documentElement.style.background='#ffffff';document.body.style.background='#ffffff';document.documentElement.style.webkitTapHighlightColor='rgba(249,115,22,.15)';var s=document.getElementById('mg-native-polish');if(!s){s=document.createElement('style');s.id='mg-native-polish';s.innerHTML='html{scroll-behavior:smooth}body{-webkit-font-smoothing:antialiased;text-rendering:optimizeLegibility}::-webkit-scrollbar{width:0;height:0}a,button{touch-action:manipulation}';document.head.appendChild(s);}}catch(e){}})();";
        v.evaluateJavascript(script, null);
    }

    private FrameLayout buildSplash() {
        FrameLayout container = new FrameLayout(this);
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Color.rgb(124,45,18), Color.rgb(234,88,12), Color.rgb(249,115,22)});
        container.setBackground(background);

        LinearLayout stack = new LinearLayout(this);
        stack.setOrientation(LinearLayout.VERTICAL);
        stack.setGravity(Gravity.CENTER);
        stack.setPadding(dp(28), dp(28), dp(28), dp(28));

        TextView monogram = new TextView(this);
        monogram.setText("MG");
        monogram.setTextColor(Color.WHITE);
        monogram.setTextSize(34);
        monogram.setGravity(Gravity.CENTER);
        monogram.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        GradientDrawable badge = new GradientDrawable();
        badge.setColor(0x28FFFFFF);
        badge.setCornerRadius(dp(28));
        badge.setStroke(dp(1), 0x55FFFFFF);
        monogram.setBackground(badge);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(dp(112), dp(112));
        badgeParams.bottomMargin = dp(24);
        stack.addView(monogram, badgeParams);

        TextView title = new TextView(this);
        title.setText("Mohit Guruji");
        title.setTextColor(Color.WHITE);
        title.setTextSize(27);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        stack.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("A refined experience, made for mobile");
        subtitle.setTextColor(0xE6FFFFFF);
        subtitle.setTextSize(14);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = dp(8);
        stack.addView(subtitle, subParams);

        ProgressBar spinner = new ProgressBar(this);
        spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        LinearLayout.LayoutParams spinParams = new LinearLayout.LayoutParams(dp(34), dp(34));
        spinParams.topMargin = dp(28);
        stack.addView(spinner, spinParams);

        FrameLayout.LayoutParams stackParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stackParams.gravity = Gravity.CENTER;
        container.addView(stack, stackParams);
        return container;
    }

    private LinearLayout buildOfflineCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(28), dp(28), dp(28), dp(28));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(22));
        bg.setStroke(dp(1), Color.rgb(254,215,170));
        card.setBackground(bg);
        card.setElevation(dp(10));

        TextView title = new TextView(this);
        title.setText("You're offline");
        title.setTextSize(22);
        title.setTextColor(Color.rgb(17,24,39));
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        card.addView(title);

        TextView body = new TextView(this);
        body.setText("Reconnect to the internet, then try again.");
        body.setTextSize(14);
        body.setTextColor(Color.rgb(75,85,99));
        body.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyParams.topMargin = dp(8);
        bodyParams.bottomMargin = dp(20);
        card.addView(body, bodyParams);

        Button retry = new Button(this);
        retry.setText("TRY AGAIN");
        retry.setTextColor(Color.WHITE);
        retry.setAllCaps(false);
        GradientDrawable buttonBg = new GradientDrawable();
        buttonBg.setColor(Color.rgb(234,88,12));
        buttonBg.setCornerRadius(dp(14));
        retry.setBackground(buttonBg);
        retry.setOnClickListener(v -> loadHome());
        card.addView(retry, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        return card;
    }

    private void showOffline() {
        splash.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        offlineCard.setVisibility(View.VISIBLE);
    }

    private boolean hasNetwork() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return true;
        Network active = cm.getActiveNetwork();
        return active != null;
    }

    private void loadHome() {
        offlineCard.setVisibility(View.GONE);
        if (!hasNetwork()) { showOffline(); return; }
        splash.setAlpha(1f);
        splash.setVisibility(View.VISIBLE);
        webView.loadUrl(HOME_URL);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (fileCallback == null) return;
            Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            fileCallback.onReceiveValue(results);
            fileCallback = null;
        }
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override public void onBackPressed() {
        if (webView.canGoBack()) { webView.goBack(); return; }
        long now = System.currentTimeMillis();
        if (now - lastBackPressed < 1800) super.onBackPressed();
        else { lastBackPressed = now; Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show(); }
    }

    @Override protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
        }
        super.onDestroy();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
