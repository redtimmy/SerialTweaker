package com.stefanbroeder.serially.serialtweaker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Base64;
import java.util.Scanner;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;

public class Util {
	
	static void printInfo(String msg) {
		System.out.println("[*] " + msg);
	}
	
	static void printHeader(String msg) {
		System.out.println("\n[** " + msg + " **]");
	}
	
	static void printError(String msg) {
		System.out.println("[!] " + msg);
	}
	
	static void DBG(String string) {
		// Debug output
		System.out.println(string);
	}
	
	static void terminate(String message) {
		System.err.println(message);
		System.exit(1);		
	}
	
	static int readIntFromStdin(String message) {
		Scanner in = new Scanner(System.in);
		int i = -1;
		while(true) {
			System.out.print(message);
			try {
				i = in.nextInt();
				break;
			} catch (Exception e) {
				Util.printError("Please input an integer");
			}
		}
		return i;
	}

	static String readStringFromStdin(String message) {
		Scanner in = new Scanner(System.in);
		String s = null;
		while(true) {
			System.out.print(message);
			try {
				in.useDelimiter("\n");
				s = in.nextLine();
				break;
			} catch (Exception e) {
				Util.printError("Error with reading from stdin");
			}
		}
		return s;
	}
	
	static byte[] decryptDES(String encodedKey, byte[] value) {
	    Cipher desCipher;
	    byte[] decodedKey = Base64.getDecoder().decode(encodedKey);
	    SecretKey originalKey = new SecretKeySpec(decodedKey, 0, decodedKey.length, "DES");
	    byte[] serObjDec = null;
	    try {
			desCipher = Cipher.getInstance("DES/ECB/PKCS5Padding");
			desCipher.init(Cipher.DECRYPT_MODE, originalKey);
			serObjDec = desCipher.doFinal(value);
		} catch (Exception e) {
			e.printStackTrace();
		} 

	    return serObjDec;
	}
	
	static byte[] encryptDES(String encodedKey, byte[] value) {
	    Cipher desCipher;
	    byte[] decodedKey = Base64.getDecoder().decode(encodedKey);
	    SecretKey originalKey = new SecretKeySpec(decodedKey, 0, decodedKey.length, "DES");
	    byte[] serObjEnc = null;
	    try {
			desCipher = Cipher.getInstance("DES/ECB/PKCS5Padding");
			desCipher.init(Cipher.ENCRYPT_MODE, originalKey);
			serObjEnc = desCipher.doFinal(value);
		} catch (Exception e) {
			e.printStackTrace();
		} 

	    return serObjEnc;
	}
	
	static String executeGet(String targetURL) {
		StringBuilder sb = new StringBuilder();
		try {
			URL url = new URL(targetURL);
			BufferedReader br = null;
			
			if(url.getProtocol().equals("https")) {
				HttpsURLConnection con = (HttpsURLConnection)url.openConnection();
				br = new BufferedReader(new InputStreamReader(con.getInputStream()));
			} else {
				HttpURLConnection con = (HttpURLConnection)url.openConnection();
				br = new BufferedReader(new InputStreamReader(con.getInputStream()));
			}
			
			String input;
			while ((input = br.readLine()) != null){
				sb.append(input);
			}
			br.close();
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return sb.toString();
	}
}
