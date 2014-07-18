package com.coinomi.wallet.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;

/**
 * @author Giannis Dzegoutanis
 */
public abstract class CoinIcon {
    final private int iconRes;

    public CoinIcon(int iconRes) {
        this.iconRes = iconRes;
    }

    public int getIconRes() {
        return iconRes;
    }

    abstract public Drawable getDrawable(Context context);
}
