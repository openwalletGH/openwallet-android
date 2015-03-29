package com.coinomi.core.exchange.shapeshift.data;

import com.coinomi.core.coins.CoinID;
import com.coinomi.core.coins.CoinType;
import com.google.common.collect.ImmutableList;

import org.json.JSONObject;

import java.util.Iterator;
import java.util.List;

/**
 * @author John L. Jegutanis
 */
public class ShapeShiftCoins extends ShapeShiftBase {
    public final List<ShapeShiftCoin> coins;
    public final List<CoinType> availableCoinTypes;

    public ShapeShiftCoins(JSONObject data) throws ShapeShiftException {
        super(data);
        if (!isError) {
            try {
                ImmutableList.Builder<ShapeShiftCoin> listBuilder = ImmutableList.builder();
                Iterator iter = data.keys();
                while (iter.hasNext()) {
                    String k = (String) iter.next();
                    listBuilder.add(new ShapeShiftCoin(data.getJSONObject(k)));
                }
                coins = listBuilder.build();

                ImmutableList.Builder<CoinType> typesBuilder = ImmutableList.builder();
                for (ShapeShiftCoin coin : coins) {
                    if (coin.isAvailable && CoinID.isSymbolSupported(coin.symbol)) {
                        typesBuilder.add(CoinID.typeFromSymbol(coin.symbol));
                    }
                }
                availableCoinTypes = typesBuilder.build();
            } catch (Exception e) {
                throw new ShapeShiftException("Could not parse object", e);
            }
        } else {
            coins = null;
            availableCoinTypes = null;
        }
    }
}
