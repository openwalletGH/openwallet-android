package com.coinomi.core.exchange.shapeshift.data;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.Value;
import com.coinomi.core.exchange.shapeshift.ShapeShift;
import com.coinomi.core.util.ExchangeRate;
import com.coinomi.core.util.ExchangeRateBase;

import org.bitcoinj.core.Address;
import org.json.JSONObject;

import java.util.Date;

import static com.coinomi.core.Preconditions.checkState;

/**
 * @author John L. Jegutanis
 */
public class ShapeShiftAmountTx extends ShapeShiftBase {
    public final String pair;
    public final Address deposit;
    public final Value depositAmount;
    public final Address withdrawal;
    public final Value withdrawalAmount;
    public final Date expiration;
    public final ExchangeRate rate;

    public ShapeShiftAmountTx(JSONObject data) throws ShapeShiftException {
        super(data);
        if (!isError) {
            try {
                JSONObject innerData = data.getJSONObject("success");
                pair = innerData.getString("pair");
                CoinType[] coinTypePair = ShapeShift.parsePair(pair);
                CoinType typeDeposit = coinTypePair[0];
                CoinType typeWithdrawal = coinTypePair[1];
                deposit = new Address(typeDeposit, innerData.getString("deposit"));
                depositAmount = Value.parse(typeDeposit, innerData.getString("depositAmount"));
                withdrawal = new Address(typeWithdrawal, innerData.getString("withdrawal"));
                withdrawalAmount = Value.parse(typeWithdrawal,
                        innerData.getString("withdrawalAmount"));
                expiration = new Date(innerData.getLong("expiration"));
                rate = new ShapeShiftExchangeRate(typeDeposit, typeWithdrawal,
                        innerData.getString("quotedRate"), innerData.optString("minerFee", null));
            } catch (Exception e) {
                throw new ShapeShiftException("Could not parse object", e);
            }
        } else {
            pair = null;
            deposit = null;
            depositAmount = null;
            withdrawal = null;
            withdrawalAmount = null;
            expiration = null;
            rate = null;
        }
    }
}
