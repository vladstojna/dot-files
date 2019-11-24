package com.r3ds.client;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.DigestException;
import java.security.DigestInputStream;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class CryptoTools {

	private static final Random random = new SecureRandom();

	/* Default fields */
	private static final String KDF_ALGO = "PBKDF2WithHmacSHA256";
	private static final int ITERATIONS = 16 * 1024;
	private static final int PBE_KEY_LEN = 256;
	private static final String ENCRYPTION_ALGO = "AES";
	private static final String ENCRYPTION_MODE = "CBC";
	private static final String DIGEST_ALGO = "SHA-256";
	private static final String MAC_ALGO = "HmacSHA256";
	private static final int BUFFER_SIZE = 16 * 1024;

	private MessageDigest messageDigest;
	private Mac mac;
	private Cipher cipher;
	private KeyGenerator keyGen;
	private String kdfAlgo;
	private String encryptionAlgo;
	private int kdfIterations;
	private int pbeKeyLen;
	private int bufferSize;

	/**
	 * Constructor
	 * @param kdfAlgo
	 * @param iterations
	 * @param keyLen
	 * @param encryptionAlgo
	 * @param mode
	 * @param digestAlgo
	 * @param bufferSize
	 */
	public CryptoTools(String kdfAlgo,
			int iterations,
			int keyLen,
			String encryptionAlgo,
			String mode,
			String digestAlgo,
			String macAlgo,
			int bufferSize)
	{
		try {
			this.messageDigest = MessageDigest.getInstance(digestAlgo);
			this.mac = Mac.getInstance(macAlgo);
			this.cipher = Cipher.getInstance(encryptionAlgo + "/" + mode + "/PKCS5Padding");
			this.kdfAlgo = kdfAlgo;
			this.encryptionAlgo = encryptionAlgo;
			this.kdfIterations = iterations;
			this.pbeKeyLen = keyLen;
			this.bufferSize = bufferSize;
			this.keyGen = null;
		} catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
			throw new AssertionError(
				String.format("%s: error in constructor %s",
				CryptoTools.class.getSimpleName(), e.getMessage()));
		}
	}

	/**
	 * Constructor with default values
	 */
	public CryptoTools() {
		this(KDF_ALGO,
			ITERATIONS,
			PBE_KEY_LEN,
			ENCRYPTION_ALGO,
			ENCRYPTION_MODE,
			DIGEST_ALGO,
			MAC_ALGO,
			BUFFER_SIZE);
	}

	/* Getters */

	public String getKdfAlgorithm() {
		return this.kdfAlgo;
	}

	public String getEncryptionAlgorithm() {
		return this.encryptionAlgo;
	}

	public int getKdfIterations() {
		return this.kdfIterations;
	}

	public int getPbeKeyLen() {
		return this.pbeKeyLen;
	}

	public int getBufferSize() {
		return this.bufferSize;
	}

	private MessageDigest getMessageDigest() {
		return this.messageDigest;
	}

	private Mac getMac() {
		return this.mac;
	}

	private Cipher getCipher() {
		return this.cipher;
	}

	private KeyGenerator getKeyGenerator() {
		return this.keyGen;
	}

	/* Setters */

	public CryptoTools setKdfAlgorithm(String algorithm) {
		this.kdfAlgo = algorithm;
		return this;
	}

	public CryptoTools setKdfIterations(int iterations) {
		if (iterations < 0)
			throw new IllegalArgumentException("Number of iterations must be positive");
		this.kdfIterations = iterations;
		return this;
	}

	public CryptoTools setBufferSize(int size) {
		if (size < 1)
			throw new IllegalArgumentException("Buffer size must be positive");
		this.bufferSize = size;
		return this;
	}

	public CryptoTools setEncryptionAlgorithm(String algorithm, String mode) {
		try {
			this.cipher = Cipher.getInstance(algorithm + "/" + mode + "/PKCS5Padding");
			this.encryptionAlgo = algorithm;
			return this;
		} catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
			throw new AssertionError(e.getMessage());
		}
	}

	public CryptoTools setDigestAlgorithm(String algorithm) {
		try {
			this.messageDigest = MessageDigest.getInstance(algorithm);
			return this;
		} catch (NoSuchAlgorithmException e) {
			throw new AssertionError(e.getMessage());
		}
	}

	public CryptoTools setMacAlgorithm(String algorithm) {
		try {
			this.mac = Mac.getInstance(algorithm);
			return this;
		} catch (NoSuchAlgorithmException e) {
			throw new AssertionError(e.getMessage());
		}
	}

	public CryptoTools initKeyGenerator(String algorithm, int keyLen) {
		try {
			this.keyGen = KeyGenerator.getInstance(algorithm);
			this.keyGen.init(keyLen);
			return this;
		} catch (NoSuchAlgorithmException e) {
			throw new AssertionError(e.getMessage());
		}
	}

	/**
	 * Securely generates a byte sequence
	 * @param len
	 * @return a random array
	 */
	public static byte[] genSalt(int len) {
		if (len <= 0)
			throw new IllegalArgumentException("Salt length must not be negative");
		byte[] salt = new byte[len];
		random.nextBytes(salt);
		return salt;
	}

	/**
	 * Securely generates a 16-byte salt
	 * @return a 16-byte array
	 */
	public static byte[] genSalt() {
		return genSalt(16);
	}

	/**
	 * Securely generates a 128-bit (16-byte) IV
	 * @return a random 16-byte array
	 */
	public IvParameterSpec genIv() {
		return new IvParameterSpec(genSalt(getCipher().getBlockSize()));
	}

	/**
	 * Read bytes from stream
	 * @param bytes
	 * @param is
	 * @return
	 * @throws IOException
	 */
	private static byte[] readBytes(int bytes, final InputStream is) throws IOException {
		byte[] result = new byte[bytes];
		int offset = 0;
		while (offset < bytes) {
			int read = is.read(result, offset, bytes-offset);
			if (read == -1)
				throw new IOException("Could not read bytes");
			offset += read;
		}
		return result;
	}

	/**
	 * Reads an IV from a stream
	 * @param is
	 * @return
	 * @throws IOException
	 */
	private IvParameterSpec readIv(final InputStream is) throws IOException {
		return new IvParameterSpec(readBytes(getCipher().getBlockSize(), is));
	}

	/**
	 * Reads a MAC from a stream
	 * @param is
	 * @return
	 * @throws IOException
	 */
	private byte[] readMac(final InputStream is) throws IOException {
		return readBytes(getMac().getMacLength(), is);
	}

	/**
	 * Derives a key from a password
	 * @param pw
	 * @param salt
	 * @return
	 */
	public SecretKey deriveKey(char[] pw, byte[] salt) {
		PBEKeySpec spec = new PBEKeySpec(pw, salt, getKdfIterations(), getPbeKeyLen());
		try {
			SecretKeyFactory kf = SecretKeyFactory.getInstance(getKdfAlgorithm());
			return new SecretKeySpec(kf.generateSecret(spec).getEncoded(), getEncryptionAlgorithm());
		} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
			throw new AssertionError("Error deriving key", e);
		} finally {
			spec.clearPassword();
		}
	}

	/**
	 * generates a secret key
	 * @return
	 */
	public SecretKey generateKey() {
		return getKeyGenerator().generateKey();
	}

	/**
	 * Computes a digest of a file
	 * @param pathToFile
	 * @return digest as byte array
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public byte[] digestFile(String pathToFile) throws FileNotFoundException, IOException {
		InputStream is = new DigestInputStream(new FileInputStream(pathToFile), getMessageDigest());
		byte[] buffer = new byte[getBufferSize()];
		while (is.read(buffer, 0, getBufferSize()) != -1) {
			// empty
		}
		is.close();
		byte[] digest = getMessageDigest().digest();
		getMessageDigest().reset();
		return digest;
	}

	/**
	 * Computes the MAC of a file
	 * @param pathToFile file to read
	 * @param key key to use during computation
	 * @return a MAC (byte array)
	 * @throws FileNotFoundException if file is not found
	 * @throws IOException if there as I/O error
	 * @throws InvalidKeyException if the key provided is invalid for said Mac
	 */
	public byte[] computeMac(String pathToFile, Key key)
			throws FileNotFoundException, IOException, InvalidKeyException {
		InputStream is = new BufferedInputStream(new FileInputStream(pathToFile));
		byte[] buffer = new byte[getBufferSize()];
		int read;
		getMac().init(key);
		while ((read = is.read(buffer, 0, getBufferSize())) != -1) {
			getMac().update(buffer, 0, read);
		}
		is.close();
		return getMac().doFinal();
	}

	/**
	 * Encrypts a file, prepends IV and digest
	 * @param inPath
	 * @param outPath
	 * @param key
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public void encrypt(String inPath, String outPath, Key key)
			throws FileNotFoundException, IOException, GeneralSecurityException {
		try {
			byte[] buffer = new byte[getBufferSize()];
			int read;
			byte[] mac = computeMac(inPath, key);
			getCipher().init(Cipher.ENCRYPT_MODE, key, genIv());

			BufferedInputStream bReader = new BufferedInputStream(new FileInputStream(inPath));
			BufferedOutputStream bWriter = new BufferedOutputStream(new FileOutputStream(outPath));

			bWriter.write(mac);
			bWriter.write(getCipher().getIV());
			while ((read = bReader.read(buffer, 0, getBufferSize())) != -1) {
				byte[] cipheredData = getCipher().update(buffer, 0, read);
				if (cipheredData != null) {
					bWriter.write(cipheredData);
				}
			}
			bWriter.write(getCipher().doFinal());
			bWriter.flush();
			bWriter.close();
			bReader.close();

		} catch (InvalidAlgorithmParameterException e) {
			throw new AssertionError("Error initializing cipher", e);
		} catch (InvalidKeyException e) {
			throw new AssertionError("Error encrypting file", e);
		}
	}

	/**
	 * Decrypts file
	 * @param inPath
	 * @param outPath
	 * @param key
	 * @throws FileNotFoundException
	 * @throws IOException
	 * @throws DigestException
	 */
	public void decrypt(String inPath, String outPath, Key key)
			throws FileNotFoundException, IOException, GeneralSecurityException {
		try {
			BufferedInputStream bReader = new BufferedInputStream(new FileInputStream(inPath));
			BufferedOutputStream bWriter = new BufferedOutputStream(new FileOutputStream(outPath));

			byte[] mac = readMac(bReader);
			IvParameterSpec iv = readIv(bReader);
			getCipher().init(Cipher.DECRYPT_MODE, key, iv);

			byte[] buffer = new byte[getBufferSize()];
			byte[] decipheredData;
			int read;

			while ((read = bReader.read(buffer, 0, getBufferSize())) != -1) {
				decipheredData = getCipher().update(buffer, 0, read);
				if (decipheredData != null) {
					bWriter.write(decipheredData);
					getMac().update(decipheredData);
				}
			}
			decipheredData = getCipher().doFinal();
			bWriter.write(decipheredData);
			bWriter.flush();
			bWriter.close();
			bReader.close();

			byte[] newMac = getMac().doFinal(decipheredData);
			if (!Arrays.equals(newMac, mac))
				throw new SignatureException("MAC is not equal, file is corrupted");

		} catch (InvalidAlgorithmParameterException e) {
			throw new AssertionError("Error initializing cipher", e);
		} catch (InvalidKeyException e) {
			throw new AssertionError("Error encrypting file", e);
		}
	}

	/**
	 * Converts a byte array into a string of hexadecimals
	 * @param bytes
	 * @return
	 */
	public static String bytesToHex(byte[] bytes) {
		StringBuilder sb = new StringBuilder();
		for (byte b : bytes)
			sb.append(Integer.toHexString((b & 0xFF) | 0x100).substring(1, 3));
		return sb.toString();
	}

	/**
	 * Converts a char array to a byte array
	 * @param array
	 * @return
	 */
	public static byte[] toBytes(char[] array) {
		ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(array));
		byte[] bytes = Arrays.copyOfRange(byteBuffer.array(),
			byteBuffer.position(), byteBuffer.limit());
		Arrays.fill(byteBuffer.array(), (byte) 0);
		return bytes;
	}

	/**
	 * Clears a char array
	 * @param array
	 */
	public static void clear(char[] array) {
		Arrays.fill(array, '\0');
	}

	/**
	 * Clears a byte array
	 * @param array
	 */
	public static void clear(byte[] array) {
		Arrays.fill(array, (byte) 0);;
	}

}
