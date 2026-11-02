package com.airport.baggage.service;

import com.airport.baggage.model.Passenger;
import com.airport.baggage.model.User;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Authentication and Session Management Service.
 * Implements salted PBKDF2 password hashing and role-based access control.
 */
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9]{10,15}$");

    public static class Session {
        private final String token;
        private final User user;
        private final long expiryMillis;

        public Session(String token, User user, long durationMillis) {
            this.token = token;
            this.user = user;
            this.expiryMillis = System.currentTimeMillis() + durationMillis;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiryMillis;
        }

        public String getToken() { return token; }
        public User getUser() { return user; }
    }

    // ===== OTP Storage =====
    private static class OtpRecord {
        final String otp;
        final String userId;
        final long expiryMillis;

        OtpRecord(String otp, String userId, long ttlMillis) {
            this.otp = otp;
            this.userId = userId;
            this.expiryMillis = System.currentTimeMillis() + ttlMillis;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiryMillis;
        }
    }

    private final Map<String, OtpRecord> pendingOtps = new ConcurrentHashMap<>();
    private final Map<String, Session> activeSessions = new ConcurrentHashMap<>();
    private final CsvFileService csvFileService;

    public AuthService(CsvFileService csvFileService) {
        this.csvFileService = csvFileService;
    }

    /**
     * Generate hex-encoded cryptographically secure random salt.
     */
    public static String generateSalt() {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return bytesToHex(salt);
    }

    /**
     * Hash password with PBKDF2-HMAC-SHA256 using the supplied random salt.
     */
    public static String hashPassword(String password, String saltHex) {
        return "PBKDF2$210000$" + derivePasswordHash(password, saltHex, 210000);
    }

    private static String derivePasswordHash(String password, String saltHex, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), hexToBytes(saltHex), iterations, 256);
            try {
                return bytesToHex(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                        .generateSecret(spec).getEncoded());
            } finally {
                spec.clearPassword();
            }
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("PBKDF2 password hashing is unavailable.", e);
        }
    }

    public static boolean verifyPassword(String password, String saltHex, String storedHashHex) {
        if (password == null || saltHex == null || storedHashHex == null) return false;
        try {
            if (storedHashHex.startsWith("PBKDF2$")) {
                String[] parts = storedHashHex.split("\\$", 3);
                if (parts.length != 3) return false;
                int iterations = Integer.parseInt(parts[1]);
                if (iterations < 10000 || iterations > 1000000) return false;
                return MessageDigest.isEqual(hexToBytes(derivePasswordHash(password, saltHex, iterations)),
                        hexToBytes(parts[2]));
            }
            String legacyHash = legacySha256Hash(password, saltHex);
            return MessageDigest.isEqual(hexToBytes(legacyHash), hexToBytes(storedHashHex));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String legacySha256Hash(String password, String saltHex) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(hexToBytes(saltHex));
            return bytesToHex(md.digest(password.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable.", e);
        }
    }

    /**
     * Authenticate passenger or admin user.
     */
    public Session login(String identifier, String password, String expectedRole) {
        if (identifier == null || password == null || identifier.trim().isEmpty() || password.isEmpty()) {
            return null;
        }

        String idClean = identifier.trim().toLowerCase();
        User foundUser = null;
        for (User u : csvFileService.getAllUsers()) {
            if (u.getUsername().equalsIgnoreCase(idClean) || u.getEmail().equalsIgnoreCase(idClean)) {
                foundUser = u;
                break;
            }
        }

        if (foundUser == null) {
            return null; // Account not found
        }

        // Verify role constraint if specified
        if (expectedRole != null && !expectedRole.equalsIgnoreCase(foundUser.getRole())) {
            return null;
        }

        // Verify password
        if (!verifyPassword(password, foundUser.getSalt(), foundUser.getPasswordHash())) {
            return null;
        }
        if (!foundUser.getPasswordHash().startsWith("PBKDF2$")) {
            foundUser.setPasswordHash(hashPassword(password, foundUser.getSalt()));
            csvFileService.updateUser(foundUser);
        }

        // Create session (24 hour lifetime)
        String token = UUID.randomUUID().toString();
        Session session = new Session(token, foundUser, 24 * 60 * 60 * 1000L);
        activeSessions.put(token, session);
        return session;
    }

    /**
     * Register a new passenger.
     */
    public synchronized String registerPassenger(String fullName, String mobileNumber, String email,
                                                String username, String password, String confirmPassword) {
        if (fullName == null || fullName.trim().isEmpty()) return "Full name is required.";
        if (email == null || !EMAIL_PATTERN.matcher(email.trim()).matches()) return "A valid email address is required.";
        if (mobileNumber == null || !PHONE_PATTERN.matcher(mobileNumber.trim().replaceAll("[\\s-]", "")).matches()) {
            return "A valid 10-15 digit mobile number is required.";
        }
        if (username == null || username.trim().length() < 3) return "Username must be at least 3 characters.";
        if (password == null || password.length() < 6) return "Password must be at least 6 characters.";
        if (!password.equals(confirmPassword)) return "Passwords do not match.";

        String cleanUsername = username.trim();
        String cleanEmail = email.trim();

        // Check duplicates
        for (User u : csvFileService.getAllUsers()) {
            if (u.getUsername().equalsIgnoreCase(cleanUsername)) {
                return "Username is already registered. Please choose another username.";
            }
            if (u.getEmail().equalsIgnoreCase(cleanEmail)) {
                return "Email is already registered. Please sign in or use another email.";
            }
        }

        String userId = "USR-" + System.currentTimeMillis() % 100000;
        String passengerId = "PAX-" + (1000 + csvFileService.getAllPassengers().size() + 1);
        String salt = generateSalt();
        String hash = hashPassword(password, salt);
        String createdAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

        User newUser = new User(userId, cleanUsername, cleanEmail, hash, salt, "PASSENGER", fullName.trim(), mobileNumber.trim(), createdAt);
        csvFileService.addUser(newUser);

        // Generate demo booking reference e.g. BK-xxxx
        String bookingRef = "BK-" + (1000 + (int)(Math.random() * 8999));
        Passenger newPax = new Passenger(passengerId, userId, fullName.trim(), cleanEmail, mobileNumber.trim(), bookingRef);
        csvFileService.addPassenger(newPax);

        return "SUCCESS";
    }

    public void logout(String token) {
        if (token != null) {
            activeSessions.remove(token);
        }
    }

    public Session getSession(String token) {
        if (token == null) return null;
        Session session = activeSessions.get(token);
        if (session != null) {
            if (session.isExpired()) {
                activeSessions.remove(token);
                return null;
            }
            return session;
        }
        return null;
    }

    public Passenger getPassengerForUser(String userId) {
        for (Passenger p : csvFileService.getAllPassengers()) {
            if (p.getUserId().equalsIgnoreCase(userId)) {
                return p;
            }
        }
        return null;
    }

    public Passenger getPassengerForUsername(String username) {
        if (username == null) return null;
        for (User user : csvFileService.getAllUsers()) {
            if (user.getUsername().equalsIgnoreCase(username.trim())) {
                return getPassengerForUser(user.getUserId());
            }
        }
        return null;
    }

    // ===== Forgot-Password: Step 1 – Request OTP =====
    /**
     * Verifies that the supplied identifier (username/email) matches the stored mobile number.
     * If valid, generates a 6-digit OTP (valid 5 min) and returns it in the result map.
     * Key "otp" is only included so the UI can display it for this academic simulation.
     */
    public Map<String, String> requestPasswordReset(String identifier, String phoneNumber) {
        Map<String, String> result = new LinkedHashMap<>();
        if (identifier == null || identifier.trim().isEmpty()) {
            result.put("error", "Username or email is required.");
            return result;
        }
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            result.put("error", "Phone number is required.");
            return result;
        }

        String idClean = identifier.trim().toLowerCase();
        String phoneCleaned = phoneNumber.trim().replaceAll("[\\s\\-()]", "");

        User found = null;
        for (User u : csvFileService.getAllUsers()) {
            if (u.getUsername().equalsIgnoreCase(idClean) || u.getEmail().equalsIgnoreCase(idClean)) {
                found = u;
                break;
            }
        }

        if (found == null) {
            result.put("error", "No account found with the given username or email.");
            return result;
        }

        // Normalize stored number the same way
        String storedPhone = found.getMobileNumber() == null ? "" :
                found.getMobileNumber().trim().replaceAll("[\\s\\-()]", "");

        if (!storedPhone.equals(phoneCleaned)) {
            result.put("error", "Phone number does not match the registered number for this account.");
            return result;
        }

        // Generate 6-digit OTP
        String otp = String.format("%06d", RANDOM.nextInt(1_000_000));
        // Store against the identifier key (normalize to lowercase username)
        pendingOtps.put(found.getUsername().toLowerCase(), new OtpRecord(otp, found.getUserId(), 5 * 60 * 1000L));

        System.out.println("[OTP] Password-reset OTP for '" + found.getUsername() + "': " + otp);

        result.put("success", "true");
        result.put("otp", otp);              // returned for simulation – in production this would be SMS-only
        result.put("username", found.getUsername().toLowerCase()); // canonical key for next step
        return result;
    }

    // ===== Forgot-Password: Step 2 – Verify OTP and Set New Password =====
    public Map<String, String> verifyOtpAndResetPassword(String identifier, String otp,
                                                         String newPassword, String confirmPassword) {
        Map<String, String> result = new LinkedHashMap<>();

        if (identifier == null || identifier.trim().isEmpty()) {
            result.put("error", "Username is required.");
            return result;
        }
        if (otp == null || otp.trim().isEmpty()) {
            result.put("error", "OTP is required.");
            return result;
        }
        if (newPassword == null || newPassword.length() < 6) {
            result.put("error", "New password must be at least 6 characters.");
            return result;
        }
        if (!newPassword.equals(confirmPassword)) {
            result.put("error", "Passwords do not match.");
            return result;
        }

        String key = identifier.trim().toLowerCase();
        OtpRecord record = pendingOtps.get(key);

        if (record == null) {
            result.put("error", "No OTP was requested for this account. Please start the process again.");
            return result;
        }
        if (record.isExpired()) {
            pendingOtps.remove(key);
            result.put("error", "OTP has expired. Please request a new one.");
            return result;
        }
        if (!record.otp.equals(otp.trim())) {
            result.put("error", "Incorrect OTP. Please check and try again.");
            return result;
        }

        // Find the user and update password
        User target = null;
        for (User u : csvFileService.getAllUsers()) {
            if (u.getUserId().equalsIgnoreCase(record.userId)) {
                target = u;
                break;
            }
        }
        if (target == null) {
            result.put("error", "Account no longer exists.");
            return result;
        }

        String newSalt = generateSalt();
        String newHash = hashPassword(newPassword, newSalt);

        User updated = new User(
            target.getUserId(), target.getUsername(), target.getEmail(),
            newHash, newSalt, target.getRole(),
            target.getFullName(), target.getMobileNumber(), target.getCreatedAt()
        );
        csvFileService.updateUser(updated);
        pendingOtps.remove(key);

        System.out.println("[OTP] Password successfully reset for '" + target.getUsername() + "'");

        result.put("success", "true");
        result.put("message", "Password updated successfully. You can now sign in with your new password.");
        return result;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        if (hex == null || hex.length() % 2 != 0) throw new IllegalArgumentException("Invalid hexadecimal value.");
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int high = Character.digit(hex.charAt(i), 16);
            int low = Character.digit(hex.charAt(i + 1), 16);
            if (high < 0 || low < 0) throw new IllegalArgumentException("Invalid hexadecimal value.");
            data[i / 2] = (byte) ((high << 4) + low);
        }
        return data;
    }
}
