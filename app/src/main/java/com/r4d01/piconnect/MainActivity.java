package com.r4d01.piconnect;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {

    private static final String TARGET_URL = "https://connect.raspberrypi.com";
    private static final String PREFS = "piconnect";
    private static final String PREF_SCALE = "scale";
    private static final float DEFAULT_SCALE = 1.5f;
    private static final float MIN_SCALE = 0.5f;
    private static final float MAX_SCALE = 3.0f;
    private static final float SCALE_STEP = 0.25f;

    // Clicks Pi Connect's "Enter full screen" button on the first tap or key press of a
    // remote session, since requestFullscreen() only works inside a real user gesture.
    // Re-arms once the button disappears (e.g. after disconnecting).
    private static final String AUTO_FULLSCREEN_JS =
            "(function() {" +
            "  if (window.__piAutoFs) return;" +
            "  window.__piAutoFs = true;" +
            "  var done = false;" +
            "  function onInput() {" +
            "    if (document.fullscreenElement) return;" +
            "    var btn = Array.prototype.find.call(document.querySelectorAll('button')," +
            "        function(b) { return /enter full ?screen/i.test(b.textContent); });" +
            "    if (!btn) { done = false; return; }" +
            "    if (done) return;" +
            "    done = true;" +
            "    btn.click();" +
            "  }" +
            "  ['keydown', 'mousedown', 'pointerup', 'touchend'].forEach(function(t) {" +
            "    document.addEventListener(t, onInput, true);" +
            "  });" +
            "})();";

    private WebView webView;
    private FrameLayout container;
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private SharedPreferences prefs;
    private float scale;
    private boolean superDown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        scale = prefs.getFloat(PREF_SCALE, DEFAULT_SCALE);

        container = findViewById(R.id.container);
        webView = findViewById(R.id.webview);

        // Without wide viewport the page's CSS width equals the WebView's width
        // in dp, which is what applyScales() relies on.
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setUseWideViewPort(false);
        webView.getSettings().setLoadWithOverviewMode(false);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);

        // Pi Connect focuses a hidden input for keyboard capture, and the WebView
        // scrolls it into view, shifting the desktop. Pin the page at the origin.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            webView.setOnScrollChangeListener((v, x, y, ox, oy) -> {
                if (x != 0 || y != 0) v.scrollTo(0, 0);
            });
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript(AUTO_FULLSCREEN_JS, null);
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
                        Gravity.TOP | Gravity.START));
                applyScales();
                // INVISIBLE (not GONE) keeps the WebView's size so the page doesn't resize.
                webView.setVisibility(View.INVISIBLE);
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

        container.addOnLayoutChangeListener(
                (v, l, t, r, b, ol, ot, or, ob) -> applyScales());

        webView.loadUrl(TARGET_URL);
    }

    // Lays the view out at (dpr / scale) times the screen size, then shrinks it back
    // with a view transform. The page sees a CSS viewport of physicalWidth / scale,
    // so Pi Connect negotiates a matching resolution and text is `scale`x larger.
    private void applyScale(View v) {
        int cw = container.getWidth();
        int ch = container.getHeight();
        if (cw == 0 || ch == 0) return;

        float k = getResources().getDisplayMetrics().density / scale;
        int w = Math.round(cw * k);
        int h = Math.round(ch * k);

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
        if (lp.width != w || lp.height != h) {
            lp.width = w;
            lp.height = h;
            lp.gravity = Gravity.TOP | Gravity.START;
            v.setLayoutParams(lp);
        }
        v.setPivotX(0);
        v.setPivotY(0);
        v.setScaleX(1f / k);
        v.setScaleY(1f / k);
    }

    private void applyScales() {
        applyScale(webView);
        if (fullscreenView != null) applyScale(fullscreenView);
    }

    private void changeScale(float delta) {
        float newScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale + delta));
        if (newScale == scale) return;
        scale = newScale;
        prefs.edit().putFloat(PREF_SCALE, scale).apply();
        applyScales();
        Toast.makeText(this, String.format(Locale.US, "Scale %.2fx", scale), Toast.LENGTH_SHORT).show();
    }

    private static boolean isSuperStandIn(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT;
    }

    private static KeyEvent withMeta(KeyEvent e, int action, int keyCode, int repeat) {
        int meta = e.getMetaState() | KeyEvent.META_META_ON | KeyEvent.META_META_LEFT_ON;
        // Drop the stand-in Right Ctrl from the modifiers the page sees.
        meta &= ~KeyEvent.META_CTRL_RIGHT_ON;
        if ((meta & KeyEvent.META_CTRL_LEFT_ON) == 0) meta &= ~KeyEvent.META_CTRL_ON;
        return new KeyEvent(e.getDownTime(), e.getEventTime(), action, keyCode, repeat, meta);
    }

    // Android consumes the real Super key, so Menu / Right Ctrl stands in for it:
    // the page receives genuine Meta key events and Meta-modified combos.
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        View target = fullscreenView != null ? fullscreenView : webView;

        if (isSuperStandIn(code)) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0 && !superDown) {
                superDown = true;
                target.dispatchKeyEvent(withMeta(e, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_META_LEFT, 0));
            } else if (e.getAction() == KeyEvent.ACTION_UP && superDown) {
                superDown = false;
                target.dispatchKeyEvent(withMeta(e, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_META_LEFT, 0));
            }
            return true;
        }

        if (superDown && code != KeyEvent.KEYCODE_VOLUME_UP
                && code != KeyEvent.KEYCODE_VOLUME_DOWN && code != KeyEvent.KEYCODE_BACK) {
            target.dispatchKeyEvent(withMeta(e, e.getAction(), code, e.getRepeatCount()));
            return true;
        }

        return super.dispatchKeyEvent(e);
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
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            changeScale(SCALE_STEP);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            changeScale(-SCALE_STEP);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
