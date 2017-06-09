package com.openwallet.wallet.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.openwallet.wallet.R;
import com.openwallet.wallet.util.Fonts;

import butterknife.ButterKnife;
import butterknife.OnClick;

public class AboutActivity extends BaseWalletActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_about);
        ButterKnife.bind(this);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(false);

        TextView version = (TextView) findViewById(R.id.about_version);
        if (getWalletApplication().packageInfo() != null) {
            version.setText(getWalletApplication().packageInfo().versionName);
        } else {
            version.setVisibility(View.INVISIBLE);
        }

        Fonts.setTypeface(findViewById(R.id.translation_globe), Fonts.Font.OPENWALLET_FONT_ICONS);
    }

    @OnClick(R.id.terms_of_service_button)
    void onTermsOfUseClick() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.terms_of_service_title)
                .setMessage(R.string.terms_of_service)
                .setPositiveButton(R.string.button_ok, null).create().show();
    }
}
