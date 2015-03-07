package com.coinomi.wallet.util;

import android.content.Context;
import android.text.TextUtils;

/**
 * @author John L. Jegutanis
 */
public class SystemUtils {
    /**
     * http://stackoverflow.com/questions/10809438/how-to-know-an-application-is-installed-from-google-play-or-side-load/16862957#16862957
     */
    public static boolean isStoreVersion(Context context) {
        boolean result = false;

        try {
            String installer = context.getPackageManager()
                    .getInstallerPackageName(context.getPackageName());
            result = !TextUtils.isEmpty(installer);
        } catch (Throwable e) { /* ignore */ }

        return result;
    }
}
