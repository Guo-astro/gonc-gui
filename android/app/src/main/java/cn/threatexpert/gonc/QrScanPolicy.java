package cn.threatexpert.gonc;

import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;

import java.util.Collections;

/** Shared camera decoder configuration and a second check at the result boundary. */
final class QrScanPolicy {
    private QrScanPolicy() {}

    static DefaultDecoderFactory decoderFactory() {
        return new DefaultDecoderFactory(Collections.singletonList(BarcodeFormat.QR_CODE));
    }

    static boolean accepts(BarcodeFormat format, String text) {
        return format == BarcodeFormat.QR_CODE && text != null && !text.trim().isEmpty();
    }
}
