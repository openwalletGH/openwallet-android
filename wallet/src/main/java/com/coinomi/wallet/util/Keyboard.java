package com.coinomi.wallet.util;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

/**
 * @author Giannis Dzegoutanis
 */
public class Keyboard {

    public static void focusAndShowKeyboard(final View mnemonicTextView, final Activity activity) {
        mnemonicTextView.requestFocus();
        // Show keyboard
        // http://stackoverflow.com/questions/23380123/why-android-inputmethodmanager-showsoftinput-returns-false
        mnemonicTextView.postDelayed(new Runnable() {
            @Override
            public void run() {
                InputMethodManager mgr = (InputMethodManager) activity.
                        getSystemService(Context.INPUT_METHOD_SERVICE);
                mgr.showSoftInput(mnemonicTextView, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 100);
    }

}
