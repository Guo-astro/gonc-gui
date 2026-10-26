package cn.threatexpert.gonc;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;

import static org.junit.Assert.*;

public class QrScanPolicyTest {
    private RGBLuminanceSource barcode(String text, BarcodeFormat format) throws Exception {
        BitMatrix matrix = new MultiFormatWriter().encode(text, format, 600, 600);
        int[] pixels = new int[matrix.getWidth() * matrix.getHeight()];
        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < matrix.getWidth(); x++) {
                pixels[y * matrix.getWidth() + x] = matrix.get(x, y) ? 0xff000000 : 0xffffffff;
            }
        }
        return new RGBLuminanceSource(matrix.getWidth(), matrix.getHeight(), pixels);
    }

    @Test public void profileQrDecodesAndClassifiesAsProfile() throws Exception {
        String payload = "{\"type\":\"gonc.vpn.profile\",\"version\":1,\"profile\":{\"passphrase\":\"Test12345\"}}";
        Result result = QrScanPolicy.decoderFactory().createDecoder(Collections.emptyMap())
                .decode(barcode(payload, BarcodeFormat.QR_CODE));
        assertNotNull(result);
        assertEquals(payload, result.getText());
        assertTrue(QrScanPolicy.accepts(result.getBarcodeFormat(), result.getText()));
        assertEquals(VpnQrPayload.Kind.PROFILE, VpnQrPayload.parse(result.getText()).kind);
    }

    @Test public void genuineNumericAndOrdinaryPassphraseQrsStillWork() throws Exception {
        for (String payload : new String[]{"123456789012", "Test12345"}) {
            Result result = QrScanPolicy.decoderFactory().createDecoder(Collections.emptyMap())
                    .decode(barcode(payload, BarcodeFormat.QR_CODE));
            assertNotNull(result);
            assertEquals(payload, result.getText());
            assertTrue(QrScanPolicy.accepts(result.getBarcodeFormat(), result.getText()));
            assertEquals(VpnQrPayload.Kind.PASSPHRASE, VpnQrPayload.parse(result.getText()).kind);
        }
    }

    @Test public void numericOneDimensionalBarcodesAreNotDecoded() throws Exception {
        for (BarcodeFormat format : new BarcodeFormat[]{BarcodeFormat.CODE_128, BarcodeFormat.ITF, BarcodeFormat.EAN_13}) {
            RGBLuminanceSource source = barcode("123456789012", format);
            // Demonstrate the old unconstrained decoder accepts this numeric barcode.
            assertNotNull(new DefaultDecoderFactory().createDecoder(Collections.emptyMap()).decode(source));
            assertNull(QrScanPolicy.decoderFactory().createDecoder(Collections.emptyMap()).decode(source));
        }
    }

    @Test public void callbackRejectsEveryNonQrFormatRegardlessOfText() {
        for (BarcodeFormat format : BarcodeFormat.values()) {
            if (format == BarcodeFormat.QR_CODE) continue;
            assertFalse(QrScanPolicy.accepts(format, "123456789012"));
            assertFalse(QrScanPolicy.accepts(format, "{\"type\":\"gonc.vpn.profile\"}"));
        }
        assertFalse(QrScanPolicy.accepts(null, "123456789012"));
    }

    @Test public void emptyQrResultsAreRejected() {
        assertFalse(QrScanPolicy.accepts(BarcodeFormat.QR_CODE, null));
        assertFalse(QrScanPolicy.accepts(BarcodeFormat.QR_CODE, " \n "));
    }

    @Test public void activityAppliesPolicyBeforeScanningAndChecksCallbacks() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/cn/threatexpert/gonc/QrScanActivity.java")), StandardCharsets.UTF_8);
        int setup = source.indexOf("barcodeView.setDecoderFactory(QrScanPolicy.decoderFactory())");
        assertTrue(setup >= 0);
        assertTrue(setup < source.indexOf("barcodeView.decodeContinuous("));
        assertTrue(source.contains("!QrScanPolicy.accepts(result.getBarcodeFormat(), result.getText())"));
    }
}
