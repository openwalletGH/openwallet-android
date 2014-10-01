package com.coinomi.wallet.util;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

/**
 * @author Giannis Dzegoutanis
 */
public class Keyboard {

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

//    public static void hideKeyboard(final TextView textView, final Activity activity) {
//        if (!textView.hasFocus()) return;
//
//        textView.clearFocus();
//        // Hide keyboard
//        textView.postDelayed(new Runnable() {
//            @Override
//            public void run() {
//                InputMethodManager mgr = (InputMethodManager) activity.
//                        getSystemService(Context.INPUT_METHOD_SERVICE);
//                mgr.hideSoftInputFromWindow(textView.getWindowToken(),
//                        InputMethodManager.HIDE_IMPLICIT_ONLY);
//            }
//        }, 100);
//    }

    public static void hideKeyboard(Activity activity) {
        InputMethodManager mgr = (InputMethodManager) activity
                .getSystemService(Activity.INPUT_METHOD_SERVICE);
        mgr.hideSoftInputFromWindow(activity.getCurrentFocus().getWindowToken(), 0);
    }

}
