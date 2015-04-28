package com.coinomi.wallet.tasks;

import com.coinomi.core.exchange.shapeshift.ShapeShift;
import com.coinomi.core.exchange.shapeshift.data.ShapeShiftMarketInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.TimerTask;

import javax.annotation.Nullable;

/**
 * @author John L. Jegutanis
 */
public abstract class MarketInfoPollTask extends TimerTask {
    private static final Logger log = LoggerFactory.getLogger(MarketInfoPollTask.class);

    private final ShapeShift shapeShift;
    private String pair;

    public MarketInfoPollTask(ShapeShift shapeShift, String pair) {
        this.shapeShift = shapeShift;
        this.pair = pair;
    }

    abstract public void onHandleMarketInfo(ShapeShiftMarketInfo marketInfo);

    public void updatePair(String newPair) {
        this.pair = newPair;
    }

    @Override
    public void run() {
        ShapeShiftMarketInfo marketInfo = getMarketInfoSync(shapeShift, pair);
        if (marketInfo != null) {
            onHandleMarketInfo(marketInfo);
        }
    }

    /**
     * Makes a call to ShapeShift about the market info of a pair. If case of a problem, it will
     * retry 3 times and return null if there was an error.
     *
     * Note: do not call this from the main thread!
     */
    @Nullable
    public static ShapeShiftMarketInfo getMarketInfoSync(ShapeShift shapeShift, String pair) {
        // Try 3 times
        for (int tries = 1; tries <= 3; tries++) {
            try {
                log.info("Polling market info for pair: {}", pair);
                return shapeShift.getMarketInfo(pair);
            } catch (Exception e) {
                log.info("Will retry: {}", e.getMessage());
                    /* ignore and retry, with linear backoff */
                try {
                    Thread.sleep(1000 * tries);
                } catch (InterruptedException ie) { /*ignored*/ }
            }
        }
        return null;
    }
}
