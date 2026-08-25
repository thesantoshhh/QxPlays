package com.qxplays.player;

import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Real cryptography for the private space:
 *  - Password -> key via PBKDF2WithHmacSHA256 (150k iterations, random 16-byte salt).
 *  - Files encrypted with AES-256-GCM (random 12-byte nonce per file, authenticated).
 */
public final class Crypto {
    public static final int ITERATIONS = 150_000;
    private static final int SALT_LEN = 16;
    private static final int NONCE_LEN = 12;
    public static final byte[] MAGIC = { 'Q', 'X', 'V', '1' }; // vault file header

    private Crypto() {}

    public static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        new SecureRandom().nextBytes(b);
        return b;
    }

    public static byte[] deriveKey(char[] password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, 256);
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return f.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new RuntimeException("PBKDF2 failed", e);
        }
    }

    /** Generate salt + verifier hash for a newly created password. */
    public static Credentials create(char[] password) {
        byte[] salt = randomBytes(SALT_LEN);
        byte[] key = deriveKey(password, salt, ITERATIONS);
        return new Credentials(salt, sha256Bytes(key), ITERATIONS);
    }

    public static boolean verify(char[] password, byte[] salt, byte[] expectedHash, int iterations) {
        if (salt == null || expectedHash == null) return false;
        byte[] key = deriveKey(password, salt, iterations);
        byte[] got = sha256Bytes(key);
        if (got.length != expectedHash.length) return false;
        int diff = 0;
        for (int i = 0; i < got.length; i++) diff |= got[i] ^ expectedHash[i];
        return diff == 0;
    }

    private static byte[] sha256Bytes(byte[] data) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            return md.digest(data);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public static byte[] encrypt(byte[] key, byte[] plain) {
        try {
            byte[] nonce = randomBytes(NONCE_LEN);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            byte[] ct = c.doFinal(plain);
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            bos.write(MAGIC);
            bos.write(nonce);
            bos.write(ct);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("encrypt failed", e);
        }
    }

    public static byte[] decrypt(byte[] key, byte[] blob) {
        try {
            if (blob.length < MAGIC.length + NONCE_LEN + 16) throw new IllegalArgumentException("bad vault file");
            for (int i = 0; i < MAGIC.length; i++) if (blob[i] != MAGIC[i]) throw new IllegalArgumentException("bad vault magic");
            byte[] nonce = new byte[NONCE_LEN];
            System.arraycopy(blob, MAGIC.length, nonce, 0, NONCE_LEN);
            byte[] ct = new byte[blob.length - MAGIC.length - NONCE_LEN];
            System.arraycopy(blob, MAGIC.length + NONCE_LEN, ct, 0, ct.length);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            return c.doFinal(ct);
        } catch (Exception e) {
            throw new RuntimeException("decrypt failed (wrong password or corrupted file)", e);
        }
    }

    public static class Credentials {
        public final byte[] salt;
        public final byte[] hash;
        public final int iterations;

        Credentials(byte[] salt, byte[] hash, int iterations) {
            this.salt = salt;
            this.hash = hash;
            this.iterations = iterations;
        }
    }
}
