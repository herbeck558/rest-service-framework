/*
 * (C) Copyright IBM Corp. 2001, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.watson.health.services;

import java.security.spec.KeySpec;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESedeKeySpec;

import java.util.Base64;

public class Encryption {

	// NOTE: CHANGING THIS KEY INVALIDATES ANY VALUES STORED IN ENCRYPTED FORM
	// THAT USED THE PREVIOUS VALUE !!!
	// must be minimum of 24 characters long given the key spec that is being used
	private static final String DEFAULT_KEY_MATERIAL = System.getenv(Encryption.class.getPackage().getName()+"_ENCRYPT_DEFAULT_KEY_MATERIAL");
	private static final String APPROVED_CIPHER_TRANSFORMATION = "DESede";

	public String encrypt(String unencryptedString) throws Exception {
		return encrypt(unencryptedString, DEFAULT_KEY_MATERIAL);
	}

	public String encrypt(String unencryptedString, String keyMaterial) throws Exception {
		if(keyMaterial == null || keyMaterial.isEmpty() || keyMaterial.length() < 24) {
			throw new Exception("Environmental Variable REST_SERVICE_FRAMEWORK_DEFAULT_KEY_MATERIAL must be provided and also must be at least 24 characters.");
		}
		if (keyMaterial.length() < 24) {
			StringBuffer sb = new StringBuffer(keyMaterial);
			sb.setLength(24);
			keyMaterial = sb.toString();
			System.out.println("keyMaterial:" + keyMaterial);
		}

		// Setup key
		KeySpec keySpec = new DESedeKeySpec(keyMaterial.getBytes());
		SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(APPROVED_CIPHER_TRANSFORMATION);
		SecretKey secretKey = keyFactory.generateSecret(keySpec);

		// Setup cipher
		Cipher cipher = Cipher.getInstance(APPROVED_CIPHER_TRANSFORMATION);
		cipher.init(Cipher.ENCRYPT_MODE, secretKey);

		// Encrypt string
		byte[] clearTextString = unencryptedString.getBytes("UTF-8");
		byte[] encryptedString = cipher.doFinal(clearTextString);

		// Encode string for easier storage
		Base64.Encoder encoder = Base64.getEncoder();
		return encoder.encodeToString(encryptedString);
	}

	public String decrypt(String encodedEncryptedString) throws Exception {
		return decrypt(encodedEncryptedString, DEFAULT_KEY_MATERIAL);
	}

	public String decrypt(String encodedEncryptedString, String keyMaterial) throws Exception {
		if(keyMaterial == null || keyMaterial.isEmpty() || keyMaterial.length() < 24) {
			throw new Exception("Environmental Variable REST_SERVICE_FRAMEWORK_DEFAULT_KEY_MATERIAL must be provided and also must be at least 24 characters.");
		}
		// Decode string
		Base64.Decoder decoder = Base64.getDecoder();
		byte[] encryptedString = decoder.decode(encodedEncryptedString);

		// Setup key
		KeySpec keySpec = new DESedeKeySpec(keyMaterial.getBytes());
		SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(APPROVED_CIPHER_TRANSFORMATION);

		// Setup cipher
		Cipher cipher = Cipher.getInstance(APPROVED_CIPHER_TRANSFORMATION);
		SecretKey secretKey = keyFactory.generateSecret(keySpec);

		// Decrypt string
		cipher.init(Cipher.DECRYPT_MODE, secretKey);
		byte[] clearTextString = cipher.doFinal(encryptedString);

		// Convert bytes to string
		StringBuffer stringBuffer = new StringBuffer();
		for (int i = 0; i < clearTextString.length; i++) {
			stringBuffer.append((char) clearTextString[i]);
		}
		return stringBuffer.toString();
	}

	// little driver program to use this from cmdline
	public static void main(String[] args) {
		if (args.length < 2) {
			System.out.println("Usage: -e|-d text_to_process <Secret key material>");
			System.exit(1);
		}
		String option = args[0];
		String item = args[1];
		String keyMaterial;
		if (args.length > 2) {
			keyMaterial = args[2];
		} else {
			keyMaterial = DEFAULT_KEY_MATERIAL;
		}
		int rc = 0;
		try {
			Encryption enc = new Encryption();
			if (option.equalsIgnoreCase("-e")) {
				System.out.println(enc.encrypt(item, keyMaterial));
			} else if (option.equalsIgnoreCase("-d")) {
				System.out.println(enc.decrypt(item, keyMaterial));
			} else {
				System.out.println("Option " + option + " is not supported");
				rc = 99;
			}
		} catch (Throwable t) {
			System.out.println("Unexpected exception.");
			t.printStackTrace();
			rc = 1;
		}
		System.exit(rc);
	}
}
