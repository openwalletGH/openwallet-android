package com.coinomi.wallet.util;

import android.content.res.Resources;

import com.coinomi.wallet.R;

/**
 * @author John L. Jegutanis
 */
public class LayoutUtils {

    /**
     * Qr-code size calculation
     */
    static public int calculateMaxQrCodeSize(Resources resources) {
        int qrPadding = resources.getDimensionPixelSize(R.dimen.qr_code_padding);
        int qrCodeViewSize = resources.getDimensionPixelSize(R.dimen.qr_code_size);
        return qrCodeViewSize - 2 * qrPadding;
    }
}
