package tech.kayys.payment.processor.qris;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.client.j2se.MatrixToImageWriter;

/**
 * Utility class for generating QR codes for QRIS payments.
 * 
 * Uses ZXing library to generate QR code images from QRIS code strings.
 * 
 * @author Numou Platform
 */
public final class QRISCodeGenerator {

    private static final QRCodeWriter QR_CODE_WRITER = new QRCodeWriter();
    private static final String DEFAULT_CHARSET = "UTF-8";
    private static final ErrorCorrectionLevel DEFAULT_ERROR_CORRECTION = ErrorCorrectionLevel.M;

    private QRISCodeGenerator() {
        // Utility class, prevent instantiation
    }

    /**
     * Generate QR code image as byte array from QR code string.
     * 
     * @param qrCodeString The QR code content string (QRIS code)
     * @param width Image width in pixels
     * @param height Image height in pixels
     * @return Byte array containing PNG image data
     */
    public static byte[] generateQRCode(String qrCodeString, int width, int height) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, DEFAULT_CHARSET);
            hints.put(EncodeHintType.ERROR_CORRECTION, DEFAULT_ERROR_CORRECTION);
            hints.put(EncodeHintType.MARGIN, 1);

            BitMatrix bitMatrix = QR_CODE_WRITER.encode(qrCodeString, BarcodeFormat.QR_CODE, 
                                                       width, height, hints);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);

            return outputStream.toByteArray();
        } catch (WriterException e) {
            throw new QRISGenerationException("Failed to generate QR code", e);
        } catch (Exception e) {
            throw new QRISGenerationException("Error generating QR code image", e);
        }
    }

    /**
     * Generate QR code image with default dimensions (300x300).
     * 
     * @param qrCodeString The QR code content string (QRIS code)
     * @return Byte array containing PNG image data
     */
    public static byte[] generateQRCode(String qrCodeString) {
        return generateQRCode(qrCodeString, 300, 300);
    }

    /**
     * Generate QR code image as base64 encoded string.
     * 
     * @param qrCodeString The QR code content string (QRIS code)
     * @param width Image width in pixels
     * @param height Image height in pixels
     * @return Base64 encoded PNG image
     */
    public static String generateQRCodeBase64(String qrCodeString, int width, int height) {
        byte[] qrCodeImage = generateQRCode(qrCodeString, width, height);
        return java.util.Base64.getEncoder().encodeToString(qrCodeImage);
    }

    /**
     * Generate QR code image as base64 encoded string with default dimensions.
     * 
     * @param qrCodeString The QR code content string (QRIS code)
     * @return Base64 encoded PNG image
     */
    public static String generateQRCodeBase64(String qrCodeString) {
        return generateQRCodeBase64(qrCodeString, 300, 300);
    }

    /**
     * Validate QRIS code string format.
     * 
     * QRIS code typically starts with specific header format.
     * 
     * @param qrCodeString The QR code string to validate
     * @return true if valid QRIS format
     */
    public static boolean isValidQRISCode(String qrCodeString) {
        if (qrCodeString == null || qrCodeString.trim().isEmpty()) {
            return false;
        }

        // Basic validation - QRIS codes typically contain specific patterns
        // This is a simplified validation; actual validation depends on QRIS spec
        return qrCodeString.length() > 10 && qrCodeString.length() < 1000;
    }

    /**
     * Custom exception for QRIS code generation errors.
     */
    public static class QRISGenerationException extends RuntimeException {
        public QRISGenerationException(String message) {
            super(message);
        }

        public QRISGenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
