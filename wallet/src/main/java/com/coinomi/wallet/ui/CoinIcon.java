package com.coinomi.wallet.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;

import java.io.Serializable;

/**
 * @author Giannis Dzegoutanis
 */
public abstract class CoinIcon implements Serializable{
    private static final long serialVersionUID = 1L;

    final private int iconRes;

    public CoinIcon(int iconRes) {
        this.iconRes = iconRes;
    }

    public int getIconRes() {
        return iconRes;
    }

    abstract public Drawable getDrawable(Context context);
}
