package com.coinomi.core.exchange.shapeshift.data;

import org.json.JSONObject;

/**
 * @author John L. Jegutanis
 */
abstract public class ShapeShiftBase {
    final public String errorMessage;
    final public boolean isError;

    protected ShapeShiftBase(JSONObject data) {
        this.errorMessage = data.optString("error", null);
        isError = errorMessage != null;
    }
}
