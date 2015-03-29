package com.coinomi.core.exchange.shapeshift.data;

import com.coinomi.core.exchange.shapeshift.ShapeShift;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Stack;

/**
 * @author John L. Jegutanis
 */
public class ShapeShiftTime extends ShapeShiftBase {
    public final Status status;
    public final int secondsRemaining;

    public static enum Status {
        PENDING, EXPIRED, UNKNOWN
    }

    public ShapeShiftTime(JSONObject data) throws ShapeShiftException {
        super(data);
        if (!isError) {
            try {
                secondsRemaining = data.getInt("seconds_remaining");
                String statusStr = data.getString("status");
                switch (statusStr) {
                    case "pending":
                        status = Status.PENDING;
                        break;
                    case "expired":
                        status = Status.EXPIRED;
                        break;
                    default:
                        status = Status.UNKNOWN;
                }
            } catch (JSONException e) {
                throw new ShapeShiftException("Could not parse object", e);
            }
        } else {
            status = null;
            secondsRemaining = -1;
        }
    }
}

