package tech.kayys.payment.processor.bank.va;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.payment.processor.bank.config.BankTransferConfig;

/**
 * Utility class for generating Virtual Account numbers.
 * 
 * VA number format varies by bank:
 * - BCA: Prefix (5 digits) + Customer Code/Random (10-11 digits)
 * - Mandiri: Prefix (5 digits) + Customer Code/Random (10-11 digits)
 * - BNI: Prefix (4 digits) + Customer Code/Random (11-12 digits)
 * - BRI: Prefix (5 digits) + Customer Code/Random (10-11 digits)
 * - Permata: Prefix (5 digits) + Customer Code/Random (10-11 digits)
 * 
 * @author Numou Platform
 */
@ApplicationScoped
public class VAGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyMMdd");

    @Inject
    BankTransferConfig bankTransferConfig;

    /**
     * Generate a Virtual Account number for the specified bank
     * 
     * @param bankCode Bank code (BCA, MANDIRI, BNI, BRI, PERMATA)
     * @param customerId Customer ID (optional, can be null for random)
     * @param transactionId Transaction ID (optional, can be null for random)
     * @return Generated VA number
     */
    public String generate(String bankCode, String customerId, String transactionId) {
        String prefix = getVAPrefix(bankCode);
        String uniquePart = generateUniquePart(bankCode, customerId, transactionId);
        
        return prefix + uniquePart;
    }

    /**
     * Generate a Virtual Account number with random unique part
     * 
     * @param bankCode Bank code
     * @return Generated VA number
     */
    public String generateRandom(String bankCode) {
        return generate(bankCode, null, null);
    }

    /**
     * Generate a Virtual Account number based on customer ID
     * 
     * @param bankCode Bank code
     * @param customerId Customer ID
     * @return Generated VA number
     */
    public String generateByCustomer(String bankCode, String customerId) {
        return generate(bankCode, customerId, null);
    }

    /**
     * Generate a Virtual Account number based on transaction ID
     * 
     * @param bankCode Bank code
     * @param transactionId Transaction ID
     * @return Generated VA number
     */
    public String generateByTransaction(String bankCode, String transactionId) {
        return generate(bankCode, null, transactionId);
    }

    /**
     * Get VA prefix for a bank
     */
    private String getVAPrefix(String bankCode) {
        switch (bankCode.toUpperCase()) {
            case "BCA":
                return bankTransferConfig.getBCAVAPrefix();
            case "MANDIRI":
                return bankTransferConfig.getMandiriVAPrefix();
            case "BNI":
                return bankTransferConfig.getBNIVAPrefix();
            case "BRI":
                return bankTransferConfig.getBRIVAPrefix();
            case "PERMATA":
                return bankTransferConfig.getPermataVAPrefix();
            default:
                return bankTransferConfig.getBCAVAPrefix(); // Default to BCA
        }
    }

    /**
     * Generate unique part of VA number
     */
    private String generateUniquePart(String bankCode, String customerId, String transactionId) {
        // If customer ID is provided, use it as base
        if (customerId != null && !customerId.trim().isEmpty()) {
            return generateFromCustomerId(bankCode, customerId);
        }
        
        // If transaction ID is provided, use it as base
        if (transactionId != null && !transactionId.trim().isEmpty()) {
            return generateFromTransactionId(bankCode, transactionId);
        }
        
        // Generate random VA number
        return generateRandomUniquePart(bankCode);
    }

    /**
     * Generate unique part from customer ID
     */
    private String generateFromCustomerId(String bankCode, String customerId) {
        // Remove non-numeric characters
        String numericId = customerId.replaceAll("[^0-9]", "");
        
        // Get target length based on bank
        int targetLength = getTargetLength(bankCode);
        
        // Add date component for uniqueness
        String dateComponent = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        
        // Combine customer ID with date and pad/truncate to target length
        String combined = numericId + dateComponent;
        
        if (combined.length() >= targetLength) {
            // Use last N digits of customer ID + date
            return combined.substring(combined.length() - targetLength);
        } else {
            // Pad with random digits
            return padWithRandom(combined, targetLength);
        }
    }

    /**
     * Generate unique part from transaction ID
     */
    private String generateFromTransactionId(String bankCode, String transactionId) {
        // Remove non-numeric characters
        String numericId = transactionId.replaceAll("[^0-9]", "");
        
        // Get target length based on bank
        int targetLength = getTargetLength(bankCode);
        
        if (numericId.length() >= targetLength) {
            // Use last N digits
            return numericId.substring(numericId.length() - targetLength);
        } else {
            // Pad with random digits
            return padWithRandom(numericId, targetLength);
        }
    }

    /**
     * Generate random unique part
     */
    private String generateRandomUniquePart(String bankCode) {
        int targetLength = getTargetLength(bankCode);
        return generateRandomDigits(targetLength);
    }

    /**
     * Get target length for unique part based on bank
     */
    private int getTargetLength(String bankCode) {
        switch (bankCode.toUpperCase()) {
            case "BCA":
                return 10; // Total VA: 5 (prefix) + 10 = 15 digits
            case "MANDIRI":
                return 10; // Total VA: 5 (prefix) + 10 = 15 digits
            case "BNI":
                return 11; // Total VA: 4 (prefix) + 11 = 15 digits
            case "BRI":
                return 10; // Total VA: 5 (prefix) + 10 = 15 digits
            case "PERMATA":
                return 10; // Total VA: 5 (prefix) + 10 = 15 digits
            default:
                return 10;
        }
    }

    /**
     * Generate random digits
     */
    private String generateRandomDigits(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    /**
     * Pad string with random digits to reach target length
     */
    private String padWithRandom(String base, int targetLength) {
        if (base.length() >= targetLength) {
            return base.substring(base.length() - targetLength);
        }
        
        int paddingLength = targetLength - base.length();
        String padding = generateRandomDigits(paddingLength);
        return padding + base;
    }

    /**
     * Validate VA number format for a bank
     */
    public boolean isValid(String bankCode, String vaNumber) {
        if (vaNumber == null || vaNumber.trim().isEmpty()) {
            return false;
        }
        
        String prefix = getVAPrefix(bankCode);
        int totalLength = getTotalLength(bankCode);
        
        // Check if VA number starts with correct prefix
        if (!vaNumber.startsWith(prefix)) {
            return false;
        }
        
        // Check total length
        if (vaNumber.length() != totalLength) {
            return false;
        }
        
        // Check if all characters are digits
        return vaNumber.matches("\\d+");
    }

    /**
     * Get total expected length for VA number
     */
    private int getTotalLength(String bankCode) {
        return getVAPrefix(bankCode).length() + getTargetLength(bankCode);
    }

    /**
     * Extract bank code from VA number
     */
    public String extractBankCode(String vaNumber) {
        if (vaNumber == null || vaNumber.length() < 5) {
            return null;
        }
        
        String prefix5 = vaNumber.substring(0, Math.min(5, vaNumber.length()));
        String prefix4 = vaNumber.length() >= 4 ? vaNumber.substring(0, 4) : "";
        
        if (prefix5.equals(bankTransferConfig.getBCAVAPrefix())) return "BCA";
        if (prefix5.equals(bankTransferConfig.getMandiriVAPrefix())) return "MANDIRI";
        if (prefix5.equals(bankTransferConfig.getBRIVAPrefix())) return "BRI";
        if (prefix5.equals(bankTransferConfig.getPermataVAPrefix())) return "PERMATA";
        if (prefix4.equals(bankTransferConfig.getBNIVAPrefix())) return "BNI";
        
        return null;
    }

    /**
     * Generate a unique transaction reference
     */
    public String generateTransactionRef() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) +
               String.format("%04d", RANDOM.nextInt(10000));
    }
}
