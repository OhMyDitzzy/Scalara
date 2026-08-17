package id.ditzzy.scalara.presets;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypts and decrypts the JSON payload behind a {@code .scl} preset export.
 *
 * <p>Deliberately built on the JDK/Android's own {@code javax.crypto} rather
 * than {@code androidx.security:security-crypto}: that Jetpack library never
 * left 1.1.0-alpha and, per its own release notes, all of its APIs were
 * deprecated on arrival at stable with no further releases planned — not a
 * dependency worth taking on for a new feature. Everything used here
 * (AES/GCM/NoPadding, PBKDF2WithHmacSHA256) has shipped in the platform
 * since well before this app's {@code minSdk}, so no new entry in
 * {@code libs.versions.toml} is needed either.
 *
 * <p>On-disk layout of a {@code .scl} file, all fields fixed-length except
 * the last (see {@link #MAGIC}, {@link #SALT_LENGTH_BYTES},
 * {@link #IV_LENGTH_BYTES}):
 * <pre>
 * [4 bytes  magic "SCL1"]
 * [16 bytes random salt ] -&gt; PBKDF2 input
 * [12 bytes random IV    ] -&gt; GCM nonce, per NIST SP 800-38D §8.2
 * [N bytes  GCM ciphertext, 16-byte auth tag appended by Cipher itself]
 * </pre>
 * Salt and IV are stored unencrypted alongside the ciphertext — this is
 * standard practice for both: neither needs to be secret to do its job
 * (uniqueness, not secrecy, is what they contribute), and the receiving
 * device needs them back to derive the same key and re-run GCM.
 */
public final class PresetCrypto {

    private static final byte[] MAGIC = {'S', 'C', 'L', '1'};

    private static final int SALT_LENGTH_BYTES = 16;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int KEY_LENGTH_BITS = 256;

    // Matches OWASP's current Password Storage Cheat Sheet guidance for
    // PBKDF2-HMAC-SHA256 (600,000 iterations as of the 2026 revision): high
    // enough that brute-forcing the password offline is impractical, while
    // still completing in well under a second on any device this app
    // targets (minSdk 24).
    private static final int PBKDF2_ITERATIONS = 600_000;

    private static final String KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";

    /** Thrown when a {@code .scl} file is corrupt, truncated, or not a Scalara export at all. */
    public static final class InvalidFileException extends Exception {
        public InvalidFileException(String message) {
            super(message);
        }
    }

    /**
     * Thrown when decryption itself fails after the file's shape checks out
     * — in practice, this means the password was wrong: GCM's authentication
     * tag won't verify against ciphertext decrypted with the wrong key, so
     * this is how a bad password surfaces rather than as silently-garbled
     * output.
     */
    public static final class WrongPasswordException extends Exception {
        public WrongPasswordException(Throwable cause) {
            super(cause);
        }
    }

    private PresetCrypto() {
    }

    /**
     * Encrypts {@code plaintextJson} with a key derived from {@code password},
     * returning a complete {@code .scl} file's bytes (magic + salt + IV +
     * ciphertext) ready to write as-is.
     */
    public static byte[] encrypt(String plaintextJson, char[] password) throws GeneralSecurityException {
        SecureRandom random = new SecureRandom();

        byte[] salt = new byte[SALT_LENGTH_BYTES];
        random.nextBytes(salt);

        byte[] iv = new byte[IV_LENGTH_BYTES];
        random.nextBytes(iv);

        SecretKeySpec key = deriveKey(password, salt);

        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
        byte[] ciphertext = cipher.doFinal(plaintextJson.getBytes(StandardCharsets.UTF_8));

        ByteArrayOutputStream out = new ByteArrayOutputStream(
                MAGIC.length + salt.length + iv.length + ciphertext.length
        );
        out.write(MAGIC, 0, MAGIC.length);
        out.write(salt, 0, salt.length);
        out.write(iv, 0, iv.length);
        out.write(ciphertext, 0, ciphertext.length);
        return out.toByteArray();
    }

    /**
     * Reverses {@link #encrypt}: given a {@code .scl} file's raw bytes and
     * the password the caller claims decrypts it, returns the original JSON
     * plaintext.
     *
     * @throws InvalidFileException   if {@code fileBytes} isn't shaped like a
     *                                 {@code .scl} file (wrong magic, too
     *                                 short to hold a salt/IV/tag) — this is
     *                                 checked before any decryption is
     *                                 attempted, so a corrupt file is
     *                                 reported distinctly from a wrong
     *                                 password.
     * @throws WrongPasswordException if the file is well-formed but GCM's
     *                                 authentication tag doesn't verify,
     *                                 almost always because the password is
     *                                 wrong (or the file's bytes were
     *                                 altered after export).
     */
    public static String decrypt(byte[] fileBytes, char[] password)
            throws InvalidFileException, WrongPasswordException, GeneralSecurityException {
        int minimumLength = MAGIC.length + SALT_LENGTH_BYTES + IV_LENGTH_BYTES + (GCM_TAG_LENGTH_BITS / 8);
        if (fileBytes.length < minimumLength) {
            throw new InvalidFileException("File too short to be a valid .scl export");
        }

        byte[] magic = Arrays.copyOfRange(fileBytes, 0, MAGIC.length);
        if (!Arrays.equals(magic, MAGIC)) {
            throw new InvalidFileException("Missing or unrecognized .scl file header");
        }

        int offset = MAGIC.length;
        byte[] salt = Arrays.copyOfRange(fileBytes, offset, offset + SALT_LENGTH_BYTES);
        offset += SALT_LENGTH_BYTES;
        byte[] iv = Arrays.copyOfRange(fileBytes, offset, offset + IV_LENGTH_BYTES);
        offset += IV_LENGTH_BYTES;
        byte[] ciphertext = Arrays.copyOfRange(fileBytes, offset, fileBytes.length);

        SecretKeySpec key = deriveKey(password, salt);

        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

        byte[] plaintext;
        try {
            plaintext = cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException e) {
            // AEADBadTagException (a GeneralSecurityException subtype) is
            // GCM's way of reporting "the key/data doesn't match" — thrown
            // by doFinal itself with no separate verify step, since GCM is
            // authenticate-then-decrypt internally. A wrong password is by
            // far the most likely real-world cause once the file's shape
            // has already passed the checks above.
            throw new WrongPasswordException(e);
        }

        return new String(plaintext, StandardCharsets.UTF_8);
    }

    private static SecretKeySpec deriveKey(char[] password, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM);
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            try {
                return new SecretKeySpec(keyBytes, "AES");
            } finally {
                // SecretKeySpec copies the bytes it's given, so it's safe to
                // wipe our copy immediately rather than waiting on GC.
                Arrays.fill(keyBytes, (byte) 0);
            }
        } finally {
            // PBEKeySpec keeps its own internal copy of the password chars;
            // clearPassword() wipes that copy specifically (distinct from
            // wiping the caller's own char[], which callers handle themselves).
            spec.clearPassword();
        }
    }
}