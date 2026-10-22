package com.r4d01.piconnect;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

public class MainActivity extends Activity {

    private static final String TARGET_URL = "https://connect.raspberrypi.com";

    private WebView webView;
    private FrameLayout container;
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        container = findViewById(R.id.container);
        webView = findViewById(R.id.webview);

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setLoadWithOverviewMode(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // Report physical pixels to the page so Raspberry Pi Connect
                // negotiates a resolution matching the device's actual screen.
                view.evaluateJavascript(
                    "(function() {" +
                    "  var dpr = window.devicePixelRatio || 1;" +
                    "  var meta = document.querySelector('meta[name=viewport]');" +
                    "  if (!meta) { meta = document.createElement('meta'); meta.name='viewport'; document.head.appendChild(meta); }" +
                    "  meta.content = 'width=' + Math.round(screen.width * dpr) + ', initial-scale=' + (1/dpr);" +
                    "})();",
                    null
                );
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (fullscreenView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                fullscreenView = view;
                fullscreenCallback = callback;
                container.addView(fullscreenView, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER));
                webView.setVisibility(View.GONE);
            }

            @Override
            public void onHideCustomView() {
                if (fullscreenView == null) return;
                container.removeView(fullscreenView);
                fullscreenView = null;
                fullscreenCallback.onCustomViewHidden();
                fullscreenCallback = null;
                webView.setVisibility(View.VISIBLE);
            }
        });

        webView.loadUrl(TARGET_URL);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (fullscreenView != null) {
                fullscreenCallback.onCustomViewHidden();
                return true;
            }
            if (webView.canGoBack()) {
                webView.goBack();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }
}
