package com.coinomi.core.util;

import org.bitcoinj.core.ECKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author John L. Jegutanis
 */
public class HardwareSoftwareCompliance {
    private static final Logger log = LoggerFactory.getLogger(HardwareSoftwareCompliance.class);

    /**
     * Some devices have software or hardware bugs that causes the EC crypto to malfunction.
     * Will return false in case the device is NOT compliant.
     */
    public static boolean isEllipticCurveCryptographyCompliant() {
        boolean isDeviceCompliant;
        try {
            new ECKey().getPubKey();
            isDeviceCompliant = true;
        } catch (Throwable e) {
            log.error("This device failed the EC compliance test", e);
            isDeviceCompliant = false;
        }
        return isDeviceCompliant;
    }
}
