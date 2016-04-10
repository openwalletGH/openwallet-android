package com.coinomi.wallet.ui;

import android.os.Bundle;

import com.coinomi.wallet.R;

/**
 * @author John L. Jegutanis
 */
public class SignVerifyMessageActivity extends BaseWalletActivity implements UnlockWalletDialog.Listener {
    private static final String SIGN_VERIFY_FRAGMENT = "sign_verify_fragment";

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fragment_wrapper);

        if (savedInstanceState == null) {
            SignVerifyMessageFragment fragment = new SignVerifyMessageFragment();
            fragment.setArguments(getIntent().getExtras());
            getFM().beginTransaction()
                    .add(R.id.container, fragment, SIGN_VERIFY_FRAGMENT)
                    .commit();
        }
    }

    @Override
    public void onPassword(CharSequence password) {
        SignVerifyMessageFragment f =
                (SignVerifyMessageFragment) getFM().findFragmentByTag(SIGN_VERIFY_FRAGMENT);
        if (f != null) {
            f.maybeStartSigningTask(password);
        }
    }
}
