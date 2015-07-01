package com.coinomi.core.exchange.shapeshift.data;

import com.coinomi.core.coins.CoinID;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.Value;
import com.coinomi.core.exchange.shapeshift.ShapeShift;
import com.coinomi.core.util.ExchangeRate;

import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static com.coinomi.core.Preconditions.checkState;

/**
 * @author John L. Jegutanis
 */
public class ShapeShiftMarketInfo  extends ShapeShiftPairBase {
    public final ShapeShiftExchangeRate rate;
    public final Value limit;
    public final Value minimum;

    public ShapeShiftMarketInfo(JSONObject data) throws ShapeShiftException {
        super(data);
        if (!isError) {
            // In market info minerFee is mandatory
            if (!data.has("minerFee")) {
                throw new ShapeShiftException("Missing value minerfee");
            }
            try {
                CoinType[] types = ShapeShift.parsePair(pair);
                rate = new ShapeShiftExchangeRate(types[0], types[1],
                        data.getString("rate"), data.getString("minerFee"));
                limit = parseValue(types[0], data.getString("limit"), RoundingMode.DOWN);
                minimum = parseValue(types[0], data.getString("minimum"), RoundingMode.UP);
            } catch (JSONException e) {
                throw new ShapeShiftException("Could not parse object", e);
            }
        } else {
            rate = null;
            limit = null;
            minimum = null;
        }
    }
}
