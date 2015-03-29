package com.coinomi.wallet.util;

import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;

import java.lang.ref.WeakReference;

/**
 * @author John L. Jegutanis
 */
public abstract class WeakHandler<T> extends Handler {
    private final WeakReference<T> reference;
    public WeakHandler(T ref) {
        this.reference = new WeakReference<T>(ref);
    }

    @Override
    public void handleMessage(Message msg) {
        T ref = reference.get();
        if (ref != null) {
            // Do not call if is a detached fragment
            if (ref instanceof Fragment) {
                Fragment f = (Fragment) ref;
                if (f.isRemoving() || f.isDetached() || f.getActivity() == null) return;
            }

            weakHandleMessage(ref, msg);
        }
    }

    protected abstract void weakHandleMessage(T ref, Message msg);
}
