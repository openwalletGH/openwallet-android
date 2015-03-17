package com.coinomi.wallet.util;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.ShareCompat;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.util.GenericUtils;
import com.coinomi.wallet.AddressBookProvider;
import com.coinomi.wallet.R;
import com.coinomi.wallet.ui.EditAddressBookEntryFragment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author John L. Jegutanis
 */
public class UiUtils {
    private static final Logger log = LoggerFactory.getLogger(UiUtils.class);

    static public void share(Activity activity, String text) {
        ShareCompat.IntentBuilder builder = ShareCompat.IntentBuilder.from(activity)
                .setType("text/plain")
                .setText(text);

        activity.startActivity(Intent.createChooser(
                builder.getIntent(),
                activity.getString(R.string.action_share)));
    }

    static public void startActionModeForAddress(final String address,
                                           final CoinType type,
                                           final Activity activity,
                                           final FragmentManager fragmentManager) {
        if (!(activity instanceof ActionBarActivity)) {
            log.warn("To show action mode, your activity must extend " + ActionBarActivity.class);
            return;
        }

        ((ActionBarActivity) activity).startSupportActionMode(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                final MenuInflater inflater = mode.getMenuInflater();
                inflater.inflate(R.menu.address_options, menu);

                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                final String label = AddressBookProvider.resolveLabel(activity, type, address);
                mode.setTitle(label != null ? label : GenericUtils.addressSplitToGroups(address));
                return true;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem menuItem) {
                switch (menuItem.getItemId()) {
                    case R.id.action_edit_label:
                        EditAddressBookEntryFragment.edit(fragmentManager, type, address);
                        mode.finish();
                        return true;
                    case R.id.action_copy:
                        UiUtils.copy(activity, address);
                        mode.finish();
                        return true;
                }

                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode actionMode) {
            }
        });
    }

    public static void copy(Activity activity, String string) {
        Object clipboardService = activity.getSystemService(Context.CLIPBOARD_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            ClipboardManager clipboard = (ClipboardManager) clipboardService;
            clipboard.setPrimaryClip(ClipData.newPlainText("simple text", string));
        } else {
            android.text.ClipboardManager clipboard = (android.text.ClipboardManager) clipboardService;
            clipboard.setText(string);
        }
        Toast.makeText(activity, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
    }
}
