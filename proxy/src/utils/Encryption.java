package utils;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.ServletException;
import javax.xml.bind.DatatypeConverter;

import org.apache.commons.codec.binary.Base64;

/**
 * Handles one-time generation of AES key and encryption of tokens
 */
public class Encryption
{
	/**
	 * Number of bits in key
	 */
	private final static int BITS = 128;

	/**
	 * The key used throughout
	 */
	private final static SecretKey key;

	static
	{
		byte[] encodedKey = DatatypeConverter.parseHexBinary(Secrets.AES_KEY);
		key = new SecretKeySpec(encodedKey, 0, encodedKey.length, "AES");
	}

	/**
	 * Generates and prints out an AES key
	 */
	public static void main(String[] args) throws Exception
	{
		KeyGenerator generator = KeyGenerator.getInstance("AES");
		generator.init(BITS);
		SecretKey key = generator.generateKey();
		System.out.println(DatatypeConverter.printHexBinary(key.getEncoded()));
	}

	/**
	 * Encrypts s. Returns null on failure.
	 */
	public static String encrypt(String s) throws ServletException
	{
		try
		{
			Cipher encrypter = Cipher.getInstance("AES");
			encrypter.init(Cipher.ENCRYPT_MODE, key);
			return new String(Base64.encodeBase64(encrypter.doFinal(s.getBytes())));
		}
		catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException
				| BadPaddingException e)
		{
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Decrypts s. Returns null on failure.
	 */
	public static String decrypt(String s) throws ServletException
	{
		Cipher decrypter;
		try
		{
			decrypter = Cipher.getInstance("AES");
			decrypter.init(Cipher.DECRYPT_MODE, key);
			return new String(decrypter.doFinal(Base64.decodeBase64(s)));
		}
		catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException
				| BadPaddingException e)
		{
			if (s != null)
			{
				e.printStackTrace();
			}
			return null;
		}
	}
}
