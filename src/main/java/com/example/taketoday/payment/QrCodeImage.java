package com.example.taketoday.payment;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

/** QR-код заказа в PNG (ZXing core + стандартный ImageIO, без лишних библиотек). */
public final class QrCodeImage {

    private static final int DARK = 0xFF1B1C1C;
    private static final int LIGHT = 0xFFFFFFFF;

    private QrCodeImage() {
    }

    /** Что зашито в QR: сотрудник на этапе 4 сканирует его и сразу видит нужный заказ и код. */
    public static String content(PaymentOrder order) {
        return "TAKETODAY:" + order.id() + ":" + order.pickupCode();
    }

    public static byte[] png(String text, int size) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 1));
            BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < matrix.getWidth(); x++) {
                for (int y = 0; y < matrix.getHeight(); y++) {
                    image.setRGB(x, y, matrix.get(x, y) ? DARK : LIGHT);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (WriterException e) {
            throw new IllegalStateException("Не удалось построить QR-код", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
