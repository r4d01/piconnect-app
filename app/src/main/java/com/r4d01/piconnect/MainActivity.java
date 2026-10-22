package com.r4d01.piconnect;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
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

    // Injected after each page load. While a remote session is open (Pi Connect's
    // full screen button is present) it:
    // - clicks "Enter full screen" on the first tap or key press, since
    //   requestFullscreen() only works inside a real user gesture; re-arms once the
    //   button disappears (e.g. after disconnecting);
    // - keeps the page scrolled to the origin, because Pi Connect focuses a hidden
    //   input for keyboard capture and the WebView scrolls it into view.
    private static final String PAGE_JS =
            "(function() {" +
            "  if (window.__piConnectApp) return;" +
            "  window.__piConnectApp = true;" +
            "  function findButton(re) {" +
            "    return Array.prototype.find.call(document.querySelectorAll('button')," +
            "        function(b) { return re.test(b.textContent); });" +
            "  }" +
            "  var done = false;" +
            "  function onInput() {" +
            "    if (document.fullscreenElement) return;" +
            "    var btn = findButton(/enter full ?screen/i);" +
            "    if (!btn) { done = false; return; }" +
            "    if (done) return;" +
            "    done = true;" +
            "    btn.click();" +
            "  }" +
            "  ['keydown', 'mousedown', 'pointerup', 'touchend'].forEach(function(t) {" +
            "    document.addEventListener(t, onInput, true);" +
            "  });" +
            "  window.addEventListener('scroll', function() {" +
            "    if ((window.scrollX || window.scrollY) && findButton(/full ?screen/i)) window.scrollTo(0, 0);" +
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

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript(PAGE_JS, null);
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
                exitFullscreen();
            }
        });

        container.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            // Deferred: changing child layout params during a layout pass can be dropped.
            if (r - l != or - ol || b - t != ob - ot) container.post(this::applyScales);
        });

        webView.loadUrl(TARGET_URL);
    }

    // Tears down the fullscreen view ourselves rather than waiting for Chromium to
    // call onHideCustomView, which then becomes a no-op.
    private void exitFullscreen() {
        if (fullscreenView == null) return;
        WebChromeClient.CustomViewCallback callback = fullscreenCallback;
        container.removeView(fullscreenView);
        fullscreenView = null;
        fullscreenCallback = null;
        webView.setVisibility(View.VISIBLE);
        callback.onCustomViewHidden();
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

    private View keyTarget() {
        return fullscreenView != null ? fullscreenView : webView;
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

    private void releaseSuper() {
        if (!superDown) return;
        superDown = false;
        long now = SystemClock.uptimeMillis();
        keyTarget().dispatchKeyEvent(
                new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_META_LEFT, 0));
    }

    // Android consumes the real Super key, so Menu / Right Ctrl stands in for it:
    // the page receives genuine Meta key events and Meta-modified combos.
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();

        if (isSuperStandIn(code)) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0 && !superDown) {
                superDown = true;
                keyTarget().dispatchKeyEvent(withMeta(e, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_META_LEFT, 0));
            } else if (e.getAction() == KeyEvent.ACTION_UP) {
                releaseSuper();
            }
            return true;
        }

        if (superDown && code != KeyEvent.KEYCODE_VOLUME_UP
                && code != KeyEvent.KEYCODE_VOLUME_DOWN && code != KeyEvent.KEYCODE_BACK) {
            keyTarget().dispatchKeyEvent(withMeta(e, e.getAction(), code, e.getRepeatCount()));
            return true;
        }

        return super.dispatchKeyEvent(e);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // The stand-in's key-up never arrives if focus moves away while it's held.
        if (!hasFocus) releaseSuper();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (fullscreenView != null) {
                exitFullscreen();
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
