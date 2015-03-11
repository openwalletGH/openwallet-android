package com.coinomi.wallet.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.ShareCompat;
import android.view.MenuItem;

import com.coinomi.wallet.R;

/**
 * @author John L. Jegutanis
 */
public class ShareHelper {
    static public void share(Activity activity, String text) {
        ShareCompat.IntentBuilder builder = ShareCompat.IntentBuilder.from(activity)
                .setType("text/plain")
                .setText(text);

        activity.startActivity(Intent.createChooser(
                builder.getIntent(),
                activity.getString(R.string.action_share)));
    }
}
