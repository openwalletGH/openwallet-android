package com.coinomi.core.exchange.shapeshift.data;

import com.coinomi.core.coins.CoinID;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.Value;
import com.coinomi.core.exceptions.AddressMalformedException;
import com.coinomi.core.wallet.AbstractAddress;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * @author John L. Jegutanis
 */
public class ShapeShiftTxStatus extends ShapeShiftBase {
    public final Status status;
    public final AbstractAddress address;
    public final AbstractAddress withdraw;
    public final Value incomingValue;
    public final Value outgoingValue;
    public final String transactionId;

    public enum Status {
        NO_DEPOSITS, RECEIVED, COMPLETE, FAILED, UNKNOWN
    }

    public ShapeShiftTxStatus(JSONObject data) throws ShapeShiftException {
        super(data);

        String statusStr = data.optString("status", null);

        if (statusStr != null) {
            try {
                CoinType inType;
                CoinType outType;
                switch (statusStr) {
                    case "no_deposits":
                        status = Status.NO_DEPOSITS;
                        address = null; // FIXME, we don't know the type here
                        withdraw = null;
                        incomingValue = null;
                        outgoingValue = null;
                        transactionId = null;
                        break;
                    case "received":
                        status = Status.RECEIVED;
                        inType = CoinID.typeFromSymbol(data.getString("incomingType"));
                        address = inType.newAddress(data.getString("address"));
                        withdraw = null;
                        incomingValue = parseValueRound(inType, data.getString("incomingCoin"));
                        outgoingValue = null;
                        transactionId = null;
                        break;
                    case "complete":
                        status = Status.COMPLETE;
                        inType = CoinID.typeFromSymbol(data.getString("incomingType"));
                        outType = CoinID.typeFromSymbol(data.getString("outgoingType"));
                        address = inType.newAddress(data.getString("address"));
                        withdraw = outType.newAddress(data.getString("withdraw"));
                        incomingValue = parseValueRound(inType, data.getString("incomingCoin"));
                        outgoingValue = parseValueRound(outType, data.getString("outgoingCoin"));
                        transactionId = data.getString("transaction");
                        break;
                    case "failed":
                        status = Status.FAILED;
                        address = null;
                        withdraw = null;
                        incomingValue = null;
                        outgoingValue = null;
                        transactionId = null;
                        break;
                    default:
                        status = Status.UNKNOWN;
                        address = null;
                        withdraw = null;
                        incomingValue = null;
                        outgoingValue = null;
                        transactionId = null;
                }
            } catch (JSONException e) {
                throw new ShapeShiftException("Could not parse object", e);
            } catch (AddressMalformedException e) {
                throw new ShapeShiftException("Could not parse address", e);
            }
        } else {
            // There should be an error, otherwise we don't know what happened
            if (!isError) throw new ShapeShiftException("Unexpected state: no status and no error");
            status = null;
            address = null;
            withdraw = null;
            incomingValue = null;
            outgoingValue = null;
            transactionId = null;
        }
    }

    public ShapeShiftTxStatus(ShapeShiftTxStatus reply, AbstractAddress address) {
        super(reply.errorMessage);
        status = reply.status;
        this.address = address;
        withdraw = reply.withdraw;
        incomingValue = reply.incomingValue;
        outgoingValue = reply.outgoingValue;
        transactionId = reply.transactionId;
    }

    public ShapeShiftTxStatus(Status status, AbstractAddress address, AbstractAddress withdraw,
                              Value incomingValue, Value outgoingValue, String transactionId) {
        super((String) null);
        this.status = status;
        this.address = address;
        this.withdraw = withdraw;
        this.incomingValue = incomingValue;
        this.outgoingValue = outgoingValue;
        this.transactionId = transactionId;
    }
}

