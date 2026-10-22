package com.r4d01.piconnect;

import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;

// A WebView that doesn't connect to the input method while a physical keyboard is
// attached. On tablets the taskbar stays visible, even in immersive mode, whenever an
// IME is connected (to host its keyboard switcher), e.g. while Pi Connect's remote
// shell has a text field focused. Without an InputConnection Chromium still turns
// hardware key events into normal typing.
public class KeyboardWebView extends WebView {

    public KeyboardWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    private boolean hasPhysicalKeyboard() {
        Configuration c = getResources().getConfiguration();
        return c.keyboard == Configuration.KEYBOARD_QWERTY
                && c.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO;
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
