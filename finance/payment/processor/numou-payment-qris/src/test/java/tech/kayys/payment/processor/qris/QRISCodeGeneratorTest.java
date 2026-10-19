package tech.kayys.payment.processor.qris;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for QRISCodeGenerator
 */
class QRISCodeGeneratorTest {

    @Test
    @DisplayName("Should generate QR code as byte array")
    void testGenerateQRCode() {
        String qrCodeString = "00020101021126580014ID.CO.QRIS.WWW0215ID102000000000003030085405100005802ID5913TEST MERCHANT6007JAKARTA62070503***6304ABCD";
        
        byte[] qrCodeImage = QRISCodeGenerator.generateQRCode(qrCodeString, 300, 300);
        
        assertNotNull(qrCodeImage);
        assertTrue(qrCodeImage.length > 0);
    }

    @Test
    @DisplayName("Should generate QR code with default dimensions")
    void testGenerateQRCodeDefault() {
        String qrCodeString = "00020101021126580014ID.CO.QRIS.WWW0215ID102000000000003030085405100005802ID5913TEST MERCHANT6007JAKARTA62070503***6304ABCD";
        
        byte[] qrCodeImage = QRISCodeGenerator.generateQRCode(qrCodeString);
        
        assertNotNull(qrCodeImage);
        assertTrue(qrCodeImage.length > 0);
    }

    @Test
    @DisplayName("Should generate QR code as base64 string")
    void testGenerateQRCodeBase64() {
        String qrCodeString = "00020101021126580014ID.CO.QRIS.WWW0215ID102000000000003030085405100005802ID5913TEST MERCHANT6007JAKARTA62070503***6304ABCD";
        
        String base64Image = QRISCodeGenerator.generateQRCodeBase64(qrCodeString, 300, 300);
        
        assertNotNull(base64Image);
        assertFalse(base64Image.isEmpty());
        
        // Verify it's valid base64
        assertDoesNotThrow(() -> Base64.getDecoder().decode(base64Image));
    }

    @Test
    @DisplayName("Should generate QR code as base64 with default dimensions")
    void testGenerateQRCodeBase64Default() {
        String qrCodeString = "00020101021126580014ID.CO.QRIS.WWW0215ID102000000000003030085405100005802ID5913TEST MERCHANT6007JAKARTA62070503***6304ABCD";
        
        String base64Image = QRISCodeGenerator.generateQRCodeBase64(qrCodeString);
        
        assertNotNull(base64Image);
        assertFalse(base64Image.isEmpty());
    }

    @Test
    @DisplayName("Should validate valid QRIS code")
    void testIsValidQRISCode() {
        String validQRIS = "00020101021126580014ID.CO.QRIS.WWW0215ID102000000000003030085405100005802ID5913TEST MERCHANT6007JAKARTA62070503***6304ABCD";
        
        assertTrue(QRISCodeGenerator.isValidQRISCode(validQRIS));
    }

    @Test
    @DisplayName("Should reject null QRIS code")
    void testIsValidQRISCodeNull() {
        assertFalse(QRISCodeGenerator.isValidQRISCode(null));
    }

    @Test
    @DisplayName("Should reject empty QRIS code")
    void testIsValidQRISCodeEmpty() {
        assertFalse(QRISCodeGenerator.isValidQRISCode(""));
        assertFalse(QRISCodeGenerator.isValidQRISCode("   "));
    }

    @Test
    @DisplayName("Should reject too short QRIS code")
    void testIsValidQRISCodeTooShort() {
        assertFalse(QRISCodeGenerator.isValidQRISCode("123456789"));
    }

    @Test
    @DisplayName("Should generate different QR codes for different inputs")
    void testDifferentInputs() {
        String qrCodeString1 = "QRIS_CODE_1";
        String qrCodeString2 = "QRIS_CODE_2";
        
        byte[] image1 = QRISCodeGenerator.generateQRCode(qrCodeString1);
        byte[] image2 = QRISCodeGenerator.generateQRCode(qrCodeString2);
        
        assertNotEquals(image1.length, image2.length);
    }

    @Test
    @DisplayName("Should generate larger QR code for larger dimensions")
    void testDifferentDimensions() {
        String qrCodeString = "00020101021126580014ID.CO.QRIS.WWW0215ID102000000000003030085405100005802ID5913TEST MERCHANT";
        
        byte[] small = QRISCodeGenerator.generateQRCode(qrCodeString, 100, 100);
        byte[] large = QRISCodeGenerator.generateQRCode(qrCodeString, 500, 500);
        
        assertTrue(large.length > small.length);
    }

    @Test
    @DisplayName("Should throw exception for invalid QR code content")
    void testInvalidContent() {
        // Very long strings might cause issues
        String veryLongString = "A".repeat(10000);
        
        assertThrows(QRISCodeGenerator.QRISGenerationException.class, () -> {
            QRISCodeGenerator.generateQRCode(veryLongString, 100, 100);
        });
    }
}
