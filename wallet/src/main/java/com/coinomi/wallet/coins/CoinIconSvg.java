package com.coinomi.wallet.coins;

import android.content.Context;
import android.graphics.drawable.Drawable;

import com.larvalabs.svgandroid.SVG;
import com.larvalabs.svgandroid.SVGParser;

/**
 * @author Giannis Dzegoutanis
 */
final public class CoinIconSvg extends CoinIcon{

    public CoinIconSvg(int iconRes) {
        super(iconRes);
    }

    public Drawable getDrawable(Context context) {
        // Parse the SVG file from the resource
        SVG svg = SVGParser.getSVGFromResource(context.getResources(), getIconRes());
        // Return a drawable from the parsed SVG
        return svg.createPictureDrawable();
    }
}
