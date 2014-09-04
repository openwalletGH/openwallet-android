package com.coinomi.wallet.util;

import android.content.res.AssetManager;
import android.graphics.Typeface;
import android.view.View;
import android.widget.TextView;

import java.util.HashMap;

/**
 * @author Giannis Dzegoutanis
 */
public class Fonts {

    private final static HashMap<Font, Typeface> typefaces = new HashMap<Font, Typeface>();

    public enum Font {
        UBUNTU_MONO_REGULAR("fonts/UbuntuMono-R.ttf"),
        ROBOTO_REGULAR("fonts/Roboto-Regular.ttf"),
        ROBOTO_LIGHT("fonts/Roboto-Light.ttf"),
        ROBOTO_THIN("fonts/Roboto-Thin.ttf");

        private final String fontPath;

        private Font(final String path) {
            this.fontPath = path;
        }
    }

    public static synchronized void initFonts(AssetManager assets) {
        if (!typefaces.isEmpty()) return; // already initialized

        for (Font font : Font.values()) {
            Typeface typeface = Typeface.createFromAsset(assets, font.fontPath);
            typefaces.put(font, typeface);
        }
    }

    public static synchronized void setTypeface(View textView, Font font) {
        if (typefaces.containsKey(font) && textView instanceof TextView) {
            ((TextView)textView).setTypeface(typefaces.get(font));
        }
    }
}
