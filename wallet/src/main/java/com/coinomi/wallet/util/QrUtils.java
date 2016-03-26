package com.coinomi.wallet.util;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.widget.ImageView;

import com.coinomi.wallet.R;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.Encoder;
import com.google.zxing.qrcode.encoder.QRCode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Hashtable;

/**
 * @author John L. Jegutanis
 */
public class QrUtils {
    private final static QRCodeWriter QR_CODE_WRITER = new QRCodeWriter();

    private static final Logger log = LoggerFactory.getLogger(QrUtils.class);
    private static final ErrorCorrectionLevel ERROR_CORRECTION_LEVEL = ErrorCorrectionLevel.M;

    private static final int DARK_COLOR = 0xdd000000;
    private static final int LIGHT_COLOR = 0;

    public static boolean setQr(final ImageView view, final Resources res, final String content) {
        return setQr(view, res, content, R.dimen.qr_code_size, R.dimen.qr_code_quite_zone_pixels);
    }

    private static boolean setQr(ImageView view, Resources res, String content,
                                 int viewSizeResId, int qrQuiteZoneResId) {

        int qrCodeViewSize = res.getDimensionPixelSize(viewSizeResId);
        int qrQuiteZone = (int) res.getDimension(qrQuiteZoneResId);

        Bitmap bitmap = create(content, qrQuiteZone);
        if (bitmap == null) {
            return false;
        }

        BitmapDrawable qr = new BitmapDrawable(res, bitmap);
        qr.setFilterBitmap(false);
        int qrSize = (qrCodeViewSize / qr.getIntrinsicHeight()) * qr.getIntrinsicHeight();
        view.getLayoutParams().height = qrSize;
        view.getLayoutParams().width = qrSize;
        view.requestLayout();
        view.setImageDrawable(qr);

        return true;
    }

    public static Bitmap create(final String content, final int marginSize) {
        return create(content, DARK_COLOR, LIGHT_COLOR, marginSize);
    }

    public static Bitmap create(final String content, final int darkColor, final int lightColor,
                                   final int marginSize) {
        try {
            QRCode code = Encoder.encode(content, ERROR_CORRECTION_LEVEL, null);
            int size = code.getMatrix().getWidth();

            final Hashtable<EncodeHintType, Object> hints = new Hashtable<>();
            hints.put(EncodeHintType.MARGIN, marginSize);
            hints.put(EncodeHintType.ERROR_CORRECTION, ERROR_CORRECTION_LEVEL);
            final BitMatrix result =
                    QR_CODE_WRITER.encode(content, BarcodeFormat.QR_CODE, size, size, hints);

            final int width = result.getWidth();
            final int height = result.getHeight();
            final int[] pixels = new int[width * height];

            for (int y = 0; y < height; y++) {
                final int offset = y * width;
                for (int x = 0; x < width; x++) {
                    pixels[offset + x] = result.get(x, y) ? darkColor : lightColor;
                }
            }

            final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            return bitmap;
        } catch (final WriterException x) {
            log.info("Could not create qr code", x);
            return null;
        }
    }
}
