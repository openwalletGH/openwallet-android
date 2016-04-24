package com.coinomi.wallet.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.ShareCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.coinomi.core.uri.CoinURI;
import com.coinomi.core.uri.CoinURIParseException;
import com.coinomi.core.util.GenericUtils;
import com.coinomi.core.wallet.AbstractAddress;
import com.coinomi.core.wallet.Wallet;
import com.coinomi.core.wallet.WalletAccount;
import com.coinomi.wallet.AddressBookProvider;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.R;
import com.coinomi.wallet.ui.AccountDetailsActivity;
import com.coinomi.wallet.ui.EditAccountFragment;
import com.coinomi.wallet.ui.EditAddressBookEntryFragment;

import org.acra.ACRA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author John L. Jegutanis
 */
public class UiUtils {
    private static final Logger log = LoggerFactory.getLogger(UiUtils.class);

    static public void toastGenericError(Context context) {
        Toast.makeText(context, R.string.error_generic, Toast.LENGTH_LONG).show();
    }

    static public void replyAddressRequest(Activity activity, CoinURI uri, WalletAccount pocket) throws CoinURIParseException {
        try {
            String uriString = uri.getAddressRequestUriResponse(pocket.getReceiveAddress()).toString();
            Intent intent = Intent.parseUri(uriString, 0);
            activity.startActivity(intent);
        } catch (Exception e) {
            // Should not happen
            ACRA.getErrorReporter().handleSilentException(e);
            Toast.makeText(activity, R.string.error_generic, Toast.LENGTH_LONG).show();
        }
    }

    static public void share(Activity activity, String text) {
        ShareCompat.IntentBuilder builder = ShareCompat.IntentBuilder.from(activity)
                .setType("text/plain")
                .setText(text);

        activity.startActivity(Intent.createChooser(
                builder.getIntent(),
                activity.getString(R.string.action_share)));
    }

    public static void copy(Context context, String string) {
        Object clipboardService = context.getSystemService(Context.CLIPBOARD_SERVICE);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                ClipboardManager clipboard = (ClipboardManager) clipboardService;
                clipboard.setPrimaryClip(ClipData.newPlainText("simple text", string));
            } else {
                android.text.ClipboardManager clipboard = (android.text.ClipboardManager) clipboardService;
                clipboard.setText(string);
            }
            Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            // Should not normally happen
            ACRA.getErrorReporter().handleSilentException(e);
            Toast.makeText(context, R.string.error_generic, Toast.LENGTH_LONG).show();
        }
    }

    public static void setVisible(View view) {
        setVisibility(view, View.VISIBLE);
    }

    public static void setInvisible(View view) {
        setVisibility(view, View.INVISIBLE);
    }

    public static void setGone(View view) {
        setVisibility(view, View.GONE);
    }

    public static void setVisibility(View view, int visibility) {
        if (view.getVisibility() != visibility) view.setVisibility(visibility);
    }

    public static ActionMode startActionMode(final Activity activity, final ActionMode.Callback callback) {
        if (activity == null || !(activity instanceof AppCompatActivity)) {
            log.warn("To show action mode, your activity must extend " + AppCompatActivity.class);
            return null;
        }
        return ((AppCompatActivity) activity).startSupportActionMode(callback);
    }

    public static ActionMode startAddressActionMode(final AbstractAddress address,
                                                    final Activity activity,
                                                    final FragmentManager fragmentManager) {
        return startActionMode(activity,
                new AddressActionModeCallback(address, activity, fragmentManager));
    }

    public static ActionMode startCopyShareActionMode(final String string,
                                                      final Activity activity) {
        return startActionMode(activity,
                new CopyShareActionModeCallback(string, activity));
    }

    public static ActionMode startAccountActionMode(final WalletAccount account,
                                                    final Activity activity,
                                                    final FragmentManager fragmentManager) {
        return startActionMode(activity,
                new AccountActionModeCallback(account, activity, fragmentManager));
    }

    public static class AddressActionModeCallback implements ActionMode.Callback {
        private final AbstractAddress address;
        private final Context context;
        private final FragmentManager fragmentManager;


        public AddressActionModeCallback(final AbstractAddress address,
                                         final Context context,
                                         final FragmentManager fragmentManager) {
            this.address = address;
            this.context = context;
            this.fragmentManager = fragmentManager;
        }

        public AbstractAddress getAddress() {
            return address;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.address_options, menu);
            final String label = AddressBookProvider.resolveLabel(context, address);
            mode.setTitle(label != null ? label : GenericUtils.addressSplitToGroups(address));
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) { return false; }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem menuItem) {
            switch (menuItem.getItemId()) {
                case R.id.action_edit_label:
                    EditAddressBookEntryFragment.edit(fragmentManager, address);
                    mode.finish();
                    return true;
                case R.id.action_copy:
                    UiUtils.copy(context, CoinURI.convertToCoinURI(address));
                    mode.finish();
                    return true;
            }

            return false;
        }

        @Override public void onDestroyActionMode(ActionMode actionMode) { }
    }

    public static class CopyShareActionModeCallback implements ActionMode.Callback {
        private final String string;
        private final Activity activity;

        public CopyShareActionModeCallback(final String string,
                                         final Activity activity) {
            this.string = string;
            this.activity = activity;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.copy_share_options, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem menuItem) {
            switch (menuItem.getItemId()) {
                case R.id.action_share:
                    UiUtils.share(activity, string);
                    mode.finish();
                    return true;
                case R.id.action_copy:
                    UiUtils.copy(activity, string);
                    mode.finish();
                    return true;
            }

            return false;
        }

        @Override public void onDestroyActionMode(ActionMode actionMode) { }
    }

    public static class AccountActionModeCallback implements ActionMode.Callback {
        private final WalletAccount account;
        private final Activity activity;
        private final FragmentManager fragmentManager;

        public AccountActionModeCallback(final WalletAccount account,
                                         final Activity activity,
                                         final FragmentManager fragmentManager) {
            this.account = account;
            this.activity = activity;
            this.fragmentManager = fragmentManager;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.account_options, menu);
            mode.setTitle(account.getDescriptionOrCoinName());
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) { return false; }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem menuItem) {
            switch (menuItem.getItemId()) {
                case R.id.action_edit_description:
                    EditAccountFragment.edit(fragmentManager, account);
                    mode.finish();
                    return true;
                case R.id.action_account_details:
                    Intent intent = new Intent(activity, AccountDetailsActivity.class);
                    intent.putExtra(Constants.ARG_ACCOUNT_ID, account.getId());
                    activity.startActivity(intent);
                    mode.finish();
                    return true;
                case R.id.action_delete:
                    new AlertDialog.Builder(activity)
                            .setTitle(activity.getString(R.string.edit_account_delete_title,
                                    account.getDescriptionOrCoinName()))
                            .setMessage(R.string.edit_account_delete_description)
                            .setNegativeButton(R.string.button_cancel, null)
                            .setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Wallet wallet = account.getWallet();
                                    wallet.deleteAccount(account.getId());
                                    if (activity instanceof EditAccountFragment.Listener) {
                                        ((EditAccountFragment.Listener) activity)
                                                .onAccountModified(account);
                                    }
                                }
                            })
                            .create().show();
                    mode.finish();
                    return true;
            }

            return false;
        }

        @Override public void onDestroyActionMode(ActionMode actionMode) { }
    }
}
