package com.r4d01.piconnect;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Insets;
import android.hardware.input.InputManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
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
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private static final String HOME_URL = "https://connect.raspberrypi.com/devices";
    // Pi Connect pages the app shows: sign-in, the device list and the two session
    // types. Anything else on connect.raspberrypi.com sends the user back home.
    private static final Pattern ALLOWED_PATH = Pattern.compile(
            "/|/devices/?|/sign-in.*|/auth/.*|/devices/[^/]+/(screen-sharing|remote-shell)-session/?");
    private static final String PREFS = "piconnect";
    private static final String PREF_SCALE = "scale";
    // Default scale: 1x on tablets (screens of 8" and up), larger on phones, where
    // 1x text is too small.
    private static final double TABLET_MIN_INCHES = 8;
    private static final float TABLET_SCALE = 1.0f;
    private static final float PHONE_SCALE = 1.75f;
    private static final float MIN_SCALE = 0.5f;
    private static final float MAX_SCALE = 3.0f;
    private static final float SCALE_STEP = 0.25f;
    private static final long SLOW_LOAD_MS = 15000;

    // What the stand-in key (Right Ctrl / Menu) sends to the remote desktop: Left Alt,
    // so stand-in + Tab reaches the Pi as Alt+Tab, which Android would otherwise take.
    private static final int REMOTE_MOD_KEYCODE = KeyEvent.KEYCODE_ALT_LEFT;
    private static final int REMOTE_MOD_SCANCODE = 56; // Linux KEY_LEFTALT
    private static final int REMOTE_MOD_META = KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON;

    // Injected after each page load. While a remote session is open (a Pi Connect
    // full screen control is present) it:
    // - asks the app to tap "Enter full screen" once the control is visible and
    //   enabled, since requestFullscreen() only works inside a real user gesture and
    //   a JS click() isn't one. Retries a few times if fullscreen doesn't start
    //   (e.g. the session was still connecting). Once fullscreen has started, it
    //   stays out of the way if the user leaves; it re-arms when the control
    //   disappears (e.g. after disconnecting);
    // On session pages (marked by URL with the piapp-session class) it also keeps the
    // page scrolled to the origin: CSS blocks scrolling by the user, but Pi Connect
    // focuses a hidden input for keyboard capture and the WebView scrolls it into view.
    // Controls are found by visible label, aria-label or title, not by tag.
    private static final String PAGE_JS =
            "(function() {" +
            "  if (window.__piConnectApp) return;" +
            "  window.__piConnectApp = true;" +
            "  function controls(re) {" +
            "    var found = [];" +
            "    if (!document.body) return found;" +
            "    var w = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT), n;" +
            "    while ((n = w.nextNode())) {" +
            "      if (re.test(n.nodeValue))" +
            "        found.push(n.parentElement.closest('button, a, [role=button]') || n.parentElement);" +
            "    }" +
            "    Array.prototype.forEach.call(document.querySelectorAll('[aria-label], [title]'), function(el) {" +
            "      if (re.test(el.getAttribute('aria-label') || el.getAttribute('title') || '')) found.push(el);" +
            "    });" +
            "    return found;" +
            "  }" +
            "  function usable(el) {" +
            "    if (el.disabled || el.getAttribute('aria-disabled') === 'true') return null;" +
            "    var r = el.getBoundingClientRect();" +
            "    return r.width && r.height ? r : null;" +
            "  }" +
            "  var entered = false, attempts = 0, pending = false;" +
            "  document.addEventListener('fullscreenchange', function() {" +
            "    if (document.fullscreenElement) entered = true;" +
            "  });" +
            "  function schedule(delay) {" +
            "    if (!pending) { pending = true; setTimeout(check, delay || 300); }" +
            "  }" +
            "  function check() {" +
            "    pending = false;" +
            "    if (document.fullscreenElement) return;" +
            "    if (!controls(/full ?screen/i).length) { entered = false; attempts = 0; return; }" +
            "    var candidates = controls(/enter full ?screen/i);" +
            "    if (entered || attempts >= 5 || !candidates.length) return;" +
            "    var r = null;" +
            "    for (var i = 0; i < candidates.length && !r; i++) r = usable(candidates[i]);" +
            "    if (!r) { schedule(500); return; }" +
            "    attempts++;" +
            "    PiConnectApp.tap(r.left + r.width / 2, r.top + r.height / 2);" +
            "    schedule(1500);" +
            "  }" +
            "  new MutationObserver(function() { schedule(); }).observe(document.documentElement," +
            "      {childList: true, subtree: true, characterData: true," +
            "       attributes: true, attributeFilter: ['disabled', 'aria-disabled', 'hidden']});" +
            "  schedule();" +
            "  window.addEventListener('scroll', function() {" +
            "    if ((window.scrollX || window.scrollY) && document.documentElement.classList.contains('piapp-session'))" +
            "      window.scrollTo(0, 0);" +
            "  });" +
            // Pi Connect uses Turbo, whose in-page navigations never reach
            // shouldOverrideUrlLoading, so they're checked here too.
            "  document.addEventListener('turbo:before-visit', function(e) {" +
            "    if (!PiConnectApp.allowInApp(e.detail.url)) e.preventDefault();" +
            "  });" +
            // On the device list, show only its <main> section: hide every element
            // that is neither inside <main> nor one of its ancestors. Device names
            // link to detail pages the app doesn't show, so they aren't links here.
            // Session pages are a fixed screen: they never scroll, by gesture or
            // overscroll. The classes are refreshed after Turbo visits; the <style>
            // survives them.
            "  var style = document.createElement('style');" +
            "  style.textContent =" +
            "      'html.piapp-devices body *:not(:has(main)):not(main):not(main *) { display: none !important; }' +" +
            "      'html.piapp-devices main a.link[href^=\"/devices/\"] { pointer-events: none; text-decoration: none; }' +" +
            "      'html.piapp-session, html.piapp-session body { overflow: hidden !important; overscroll-behavior: none !important; }';" +
            "  document.head.appendChild(style);" +
            "  function markPage() {" +
            "    var c = document.documentElement.classList;" +
            "    c.toggle('piapp-devices', /^\\/devices\\/?$/.test(location.pathname));" +
            "    c.toggle('piapp-session', /^\\/devices\\/[^\\/]+\\/(screen-sharing|remote-shell)-session\\/?$/.test(location.pathname));" +
            "  }" +
            "  markPage();" +
            "  document.addEventListener('turbo:load', markPage);" +
            "})();";

    private WebView webView;
    private FrameLayout container;
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private SharedPreferences prefs;
    private float scale;
    private boolean standInDown;
    private View loadProblem;
    private TextView loadProblemText;
    private boolean loadFailed;
    private final Runnable slowLoad = () -> showLoadProblem(R.string.load_slow);
    private InputManager inputManager;
    private Boolean imeBlocked;
    private int imeHeight;
    private final InputManager.InputDeviceListener keyboardListener =
            new InputManager.InputDeviceListener() {
                @Override
                public void onInputDeviceAdded(int deviceId) {
                    updateImeBlock();
                }

                @Override
                public void onInputDeviceRemoved(int deviceId) {
                    updateImeBlock();
                }

                @Override
                public void onInputDeviceChanged(int deviceId) {
                    updateImeBlock();
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setUpWindow();
        hideSystemBars();

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        scale = prefs.getFloat(PREF_SCALE, defaultScale());
        // The volume keys don't change the volume in this app, so say what they do.
        Toast.makeText(this, String.format(Locale.US,
                "Volume buttons change the scale (now %.2fx), not the volume", scale),
                Toast.LENGTH_LONG).show();

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
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        loadProblem = findViewById(R.id.load_problem);
        loadProblemText = findViewById(R.id.load_problem_text);
        findViewById(R.id.reload).setOnClickListener(v -> reload());

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                loadFailed = false;
                webView.removeCallbacks(slowLoad);
                webView.postDelayed(slowLoad, SLOW_LOAD_MS);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                webView.removeCallbacks(slowLoad);
                // Also called after a failed load, which should keep its message.
                if (!loadFailed) loadProblem.setVisibility(View.GONE);
                view.evaluateJavascript(PAGE_JS, null);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) onLoadFailed();
            }

            @SuppressWarnings("deprecation")
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                // Called instead of the method above before Android 6, for main frames only.
                onLoadFailed();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // Subframes aren't restricted.
                return request.isForMainFrame() && !allowInApp(request.getUrl());
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // Called instead of the method above before Android 7.
                return !allowInApp(Uri.parse(url));
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
                notifyPageResized();
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

            @JavascriptInterface
            public boolean allowInApp(String url) {
                return MainActivity.this.allowInApp(Uri.parse(url));
            }
        }, "PiConnectApp");

        inputManager = (InputManager) getSystemService(INPUT_SERVICE);
        inputManager.registerInputDeviceListener(keyboardListener, null);
        updateImeBlock();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            container.setOnApplyWindowInsetsListener((v, insets) -> {
                boolean imeVisible = insets.isVisible(WindowInsets.Type.ime());
                // Swipe-revealed transient bars don't count as visible here, so this
                // only fires when something has brought the bars back for good. An
                // on-screen keyboard brings the navigation bar with it; leave that be.
                if (insets.isVisible(WindowInsets.Type.navigationBars()) && !imeVisible) {
                    v.post(this::hideSystemBars);
                }
                // The page is shrunk above the on-screen keyboard here, not by the
                // WebView: it measures the keyboard against its own unscaled size
                // (see applyScale) and would leave a gap above it. So the WebView
                // doesn't get the keyboard's inset.
                int height = imeVisible ? insets.getInsets(WindowInsets.Type.ime()).bottom : 0;
                if (height != imeHeight) {
                    imeHeight = height;
                    v.post(this::applyScales);
                }
                return new WindowInsets.Builder(insets)
                        .setInsets(WindowInsets.Type.ime(), Insets.NONE)
                        .build();
            });
        }

        container.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            // Deferred: changing child layout params during a layout pass can be dropped.
            if (r - l != or - ol || b - t != ob - ot) container.post(this::applyScales);
        });

        webView.loadUrl(HOME_URL);
    }

    // The app only shows Pi Connect's device list and sessions (plus Raspberry Pi ID
    // for signing in). Returns whether a navigation may happen in the WebView; if
    // not, handles it: other Pi Connect pages go back to the device list, other sites
    // open in the user's browser. Called on the UI thread and from the JS bridge.
    private boolean allowInApp(Uri uri) {
        String scheme = uri.getScheme();
        String host = uri.getHost();
        String path = uri.getPath() == null || uri.getPath().isEmpty() ? "/" : uri.getPath();
        if ("https".equals(scheme) && "id.raspberrypi.com".equals(host)) return true;
        if ("https".equals(scheme) && "connect.raspberrypi.com".equals(host)) {
            if (ALLOWED_PATH.matcher(path).matches()) return true;
            runOnUiThread(() -> webView.loadUrl(HOME_URL));
            return false;
        }
        if ("about".equals(scheme)) return true;
        if ("http".equals(scheme) || "https".equals(scheme) || "mailto".equals(scheme)) {
            runOnUiThread(() -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (ActivityNotFoundException ignored) {
                    // Nowhere to open it: just stay on the current page.
                }
            });
        }
        return false;
    }

    // Until a page draws, the WebView is just black, so without this a failed or
    // stalled load looks like a broken app. The message covers WebView's own error
    // page and offers a reload; it goes away once a page finishes loading.
    private void onLoadFailed() {
        loadFailed = true;
        webView.removeCallbacks(slowLoad);
        showLoadProblem(R.string.load_failed);
    }

    private void showLoadProblem(int message) {
        loadProblemText.setText(message);
        loadProblem.setVisibility(View.VISIBLE);
        findViewById(R.id.reload).requestFocus();
    }

    private void reload() {
        loadProblem.setVisibility(View.GONE);
        String url = webView.getUrl();
        webView.loadUrl(url != null ? url : HOME_URL);
    }

    @SuppressWarnings("deprecation")
    private float defaultScale() {
        DisplayMetrics m = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(m);
        double inches = Math.hypot(m.widthPixels / m.xdpi, m.heightPixels / m.ydpi);
        return inches >= TABLET_MIN_INCHES ? TABLET_SCALE : PHONE_SCALE;
    }

    // Content is laid out as if the system bars weren't there (including under a
    // display cutout), so showing them doesn't resize the WebView and make Pi Connect
    // renegotiate.
    private void setUpWindow() {
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
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }
    }

    // Immersive mode: hides the status bar, navigation bar and tablet taskbar; an edge
    // swipe shows them briefly.
    @SuppressWarnings("deprecation")
    private void hideSystemBars() {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) controller.hide(WindowInsets.Type.systemBars());
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

    // Keeps the whole window (the WebView and Chromium's fullscreen view alike) away
    // from the input method while a physical keyboard is attached. Any IME connection,
    // even Gboard with nothing on screen, brings the tablet taskbar back, flashing it
    // on every keystroke. Hardware keys still reach the page directly, translated with
    // Android's active physical keyboard layout. Only touches the window when the
    // answer changes: input devices (e.g. BT battery levels) report changes often.
    private void updateImeBlock() {
        boolean block = hasPhysicalKeyboard();
        if (imeBlocked != null && imeBlocked == block) return;
        imeBlocked = block;
        if (block) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        }
    }

    // Detachable tablet keyboards often don't set Configuration.keyboard /
    // hardKeyboardHidden, so look at the connected input devices instead.
    private static boolean hasPhysicalKeyboard() {
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (device != null && !device.isVirtual()
                    && device.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC
                    && (device.getSources() & InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        inputManager.unregisterInputDeviceListener(keyboardListener);
        super.onDestroy();
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
        notifyPageResized();
    }

    // In a browser, entering or leaving fullscreen resizes the window, and Pi Connect
    // renegotiates the remote desktop size on that resize event. Here the fullscreen
    // view is exactly the WebView's size, so no resize fires and a stream started
    // before fullscreen stays sized for the area between Pi Connect's toolbars
    // (letterboxed). Fire the event ourselves once the new layout has settled.
    private void notifyPageResized() {
        String js = "window.dispatchEvent(new Event('resize'));" +
                "if (window.visualViewport) window.visualViewport.dispatchEvent(new Event('resize'));";
        for (long delay : new long[] {200, 1000}) {
            webView.postDelayed(() -> webView.evaluateJavascript(js, null), delay);
        }
    }

    // Lays the view out at (dpr / scale) times the screen size, then shrinks it back
    // with a view transform. The page sees a CSS viewport of physicalWidth / scale,
    // so Pi Connect negotiates a matching resolution and text is `scale`x larger.
    // The height leaves room for the on-screen keyboard, if shown.
    private void applyScale(View v) {
        int cw = container.getWidth();
        int ch = container.getHeight() - imeHeight;
        if (cw <= 0 || ch <= 0) return;

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

    // Delivers a key event the way real keys are delivered after the activity: to
    // whichever view has keyboard focus. Dispatching to the WebView or fullscreen view
    // directly drops the event when that view isn't the focused one.
    private void sendKey(KeyEvent e) {
        getWindow().superDispatchKeyEvent(e);
    }

    private static boolean isStandIn(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT;
    }

    // Chromium derives the DOM `code` (e.g. "AltLeft") from the scan code, and Pi
    // Connect ignores keys without one, so synthetic events must carry a scan code.
    private static KeyEvent withRemoteMod(KeyEvent e, int action, int keyCode, int scanCode, int repeat) {
        int meta = e.getMetaState() | REMOTE_MOD_META;
        // Drop the stand-in Right Ctrl from the modifiers the page sees.
        meta &= ~KeyEvent.META_CTRL_RIGHT_ON;
        if ((meta & KeyEvent.META_CTRL_LEFT_ON) == 0) meta &= ~KeyEvent.META_CTRL_ON;
        return new KeyEvent(e.getDownTime(), e.getEventTime(), action, keyCode, repeat, meta,
                e.getDeviceId(), scanCode, e.getFlags(), e.getSource());
    }

    private void releaseStandIn() {
        if (!standInDown) return;
        standInDown = false;
        long now = SystemClock.uptimeMillis();
        sendKey(new KeyEvent(now, now, KeyEvent.ACTION_UP, REMOTE_MOD_KEYCODE, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, REMOTE_MOD_SCANCODE, 0, InputDevice.SOURCE_KEYBOARD));
    }

    // All of the app's own keys are handled here, before the focused view (the
    // WebView, or Chromium's fullscreen view) can consume them:
    // - volume keys change the scale;
    // - Back leaves fullscreen, then goes back a page;
    // - Android intercepts Alt+Tab, so Menu / Right Ctrl stands in for Alt: the page
    //   receives genuine Alt key events and Alt-modified combos, and holding the
    //   stand-in while pressing Tab repeatedly cycles windows on the Pi.
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;

        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (down) changeScale(code == KeyEvent.KEYCODE_VOLUME_UP ? SCALE_STEP : -SCALE_STEP);
            return true;
        }

        if (code == KeyEvent.KEYCODE_BACK && (fullscreenView != null || webView.canGoBack())) {
            if (e.getAction() == KeyEvent.ACTION_UP) {
                if (fullscreenView != null) exitFullscreen();
                else webView.goBack();
            }
            return true;
        }

        if (isStandIn(code)) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0 && !standInDown) {
                standInDown = true;
                sendKey(withRemoteMod(e, KeyEvent.ACTION_DOWN, REMOTE_MOD_KEYCODE, REMOTE_MOD_SCANCODE, 0));
            } else if (e.getAction() == KeyEvent.ACTION_UP) {
                releaseStandIn();
            }
            return true;
        }

        if (standInDown && code != KeyEvent.KEYCODE_BACK) {
            sendKey(withRemoteMod(e, e.getAction(), code, e.getScanCode(), e.getRepeatCount()));
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
            releaseStandIn();
        }
    }
}
