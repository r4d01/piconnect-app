package com.r4d01.piconnect;

import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.view.InputDevice;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;

// A WebView that doesn't connect to the input method while a physical keyboard is
// attached. On tablets the taskbar stays visible, even in immersive mode, whenever an
// IME is shown (to host its keyboard switcher), e.g. while Pi Connect's remote shell
// has a text field focused. Without an InputConnection Chromium still turns hardware
// key events into normal typing.
// This only covers the WebView itself: in fullscreen Chromium's own view takes focus,
// so MainActivity also blocks the IME for the whole window.
public class KeyboardWebView extends WebView {

    public KeyboardWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    // Detachable tablet keyboards often don't set Configuration.keyboard /
    // hardKeyboardHidden, so look at the connected input devices instead.
    static boolean hasPhysicalKeyboard() {
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
    public boolean onCheckIsTextEditor() {
        return !hasPhysicalKeyboard() && super.onCheckIsTextEditor();
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        if (hasPhysicalKeyboard()) return null;
        return super.onCreateInputConnection(outAttrs);
    }

    // Attaching or detaching the keyboard changes the answer above.
    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        InputMethodManager imm =
                (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.restartInput(this);
    }
}
