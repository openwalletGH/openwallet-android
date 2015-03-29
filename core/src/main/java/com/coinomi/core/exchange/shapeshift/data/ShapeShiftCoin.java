package com.coinomi.core.exchange.shapeshift.data;

import com.coinomi.core.exchange.shapeshift.ShapeShift;

import org.json.JSONObject;

import java.net.URL;

/**
 * @author John L. Jegutanis
 */
final public class ShapeShiftCoin extends ShapeShiftBase {
    final public String name;
    final public String symbol;
    final public URL image;
    final public boolean isAvailable;

    public ShapeShiftCoin(JSONObject data) throws ShapeShiftException {
        super(data);
        if (!isError) {
            try {
                name = data.getString("name");
                symbol = data.getString("symbol");
                image = new URL(data.getString("image"));
                isAvailable = data.getString("status").equals("available");
            } catch (Exception e) {
                throw new ShapeShiftException("Could not parse object", e);
            }
        } else {
            name = null;
            symbol = null;
            image = null;
            isAvailable = false;
        }
    }
}
