package com.coinomi.wallet.util;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

/**
 * @author John L. Jegutanis
 */
public class Keyboard {

    // FIXME causes problems in older Androids
    @Deprecated
    public static void focusAndShowKeyboard(final TextView textView, final Activity activity) {
        textView.requestFocus();
        // Show keyboard
        // http://stackoverflow.com/questions/23380123/why-android-inputmethodmanager-showsoftinput-returns-false
        textView.postDelayed(new Runnable() {
            @Override
            public void run() {
                InputMethodManager mgr = (InputMethodManager) activity
                        .getSystemService(Context.INPUT_METHOD_SERVICE);
                mgr.showSoftInput(textView, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 100);
    }

    public static void hideKeyboard(Activity activity) {
        InputMethodManager mgr = (InputMethodManager) activity
                .getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (activity.getCurrentFocus() != null) {
            mgr.hideSoftInputFromWindow(activity.getCurrentFocus().getWindowToken(), 0);
        }
    }

}
