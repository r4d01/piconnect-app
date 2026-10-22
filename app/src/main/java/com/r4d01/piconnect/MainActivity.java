package com.r4d01.piconnect;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
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
    // - asks the app to tap "Enter full screen" as soon as the button appears, since
    //   requestFullscreen() only works inside a real user gesture and a JS click()
    //   isn't one. Once per session: if the user leaves fullscreen it stays left,
    //   and it re-arms when the button disappears (e.g. after disconnecting);
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
            "  var done = false, pending = false;" +
            "  function schedule() {" +
            "    if (!pending) { pending = true; setTimeout(check, 300); }" +
            "  }" +
            "  function check() {" +
            "    pending = false;" +
            "    if (document.fullscreenElement) return;" +
            "    if (!findButton(/full ?screen/i)) { done = false; return; }" +
            "    var btn = findButton(/enter full ?screen/i);" +
            "    if (done || !btn) return;" +
            "    var r = btn.getBoundingClientRect();" +
            "    if (!r.width || !r.height) { schedule(); return; }" +
            "    done = true;" +
            "    PiConnectApp.tap(r.left + r.width / 2, r.top + r.height / 2);" +
            "  }" +
            "  new MutationObserver(schedule).observe(document.documentElement," +
            "      {childList: true, subtree: true, characterData: true});" +
            "  schedule();" +
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
        hideSystemBars();

        prefs =getSharedPreferences(PREFS, MODE_PRIVATE);
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

        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void tap(float cssX, float cssY) {
                runOnUiThread(() -> tapWebView(cssX, cssY));
            }
        }, "PiConnectApp");

        container.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            // Deferred: changing child layout params during a layout pass can be dropped.
            if (r - l != or - ol || b - t != ob - ot) container.post(this::applyScales);
        });

        webView.loadUrl(TARGET_URL);
    }

    // Immersive mode: hides the status bar, navigation bar and tablet taskbar; an edge
    // swipe shows them briefly. Content is laid out as if the bars weren't there, so
    // showing them doesn't resize the WebView and make Pi Connect renegotiate.
    @SuppressWarnings("deprecation")
    private void hideSystemBars() {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(attrs);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    // Sends a touch tap at a point given in CSS px. Chromium treats it as real input,
    // so the page gets the user activation that requestFullscreen() needs. CSS px
    // equal dp here (no wide viewport, page zoom 1), and the event goes straight to
    // the WebView, so its own scale transform doesn't apply.
    private void tapWebView(float cssX, float cssY) {
        if (fullscreenView != null) return;
        float density = getResources().getDisplayMetrics().density;
        float x = cssX * density;
        float y = cssY * density;
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, x, y, 0);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        webView.dispatchTouchEvent(down);
        webView.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
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
        if (hasFocus) {
            // Dialogs, the notification shade etc. can bring the bars back.
            hideSystemBars();
        } else {
            // The stand-in's key-up never arrives if focus moves away while it's held.
            releaseSuper();
        }
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
