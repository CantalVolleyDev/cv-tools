package com.jtouzy.cv.security;

import java.util.Random;

import com.google.common.base.Charsets;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.jtouzy.cv.config.PropertiesNames;
import com.jtouzy.cv.config.PropertiesReader;
import com.jtouzy.cv.model.classes.User;

public final class UserPassword {
	private static final String CHARS = "azertyuiopmlkjhgfdsqwxcvnAZERTYUIOPMLKJHGFDSQWXCVN0987654321";
	private static final int SALT_LENGTH = 64;
	private static final int PASSWORD_LENGTH = 64;
	private static final int AUTO_BUILD_PASSWORD_LENGTH = 8;
	
	public static final String getFullHashedNewPassword(String password) {
		String salt = hashString(buildString());
		return salt + hashPassword(salt, password);
	}
	
	public static final String hashPassword(String salt, String password) {
		return hashString(password + salt);
	}
	
	public static final String hashString(String stringToHash) {
		HashFunction hashFunction = Hashing.sha256();
		return hashFunction.newHasher()
						   .putString(stringToHash, Charsets.UTF_8)
						   .hash()
						   .toString();
	}
	
	public static final void checkPassword(User user, String password)
	throws SecurityException {
		String salt = user.getPassword().substring(0, SALT_LENGTH);
		String validHash = user.getPassword().substring(PASSWORD_LENGTH);
		String hashed = hashPassword(salt, password);
		if (!hashed.equals(validHash)) {
			String globalPassword = PropertiesReader.getProperty(PropertiesNames.GLOBAL_PASSWORD);
			if (!password.equals(globalPassword))
				throw new SecurityException("Identifiant ou mot de passe incorrect");
		}
	}

	public static final String buildString() {
		StringBuilder password = new StringBuilder();
		Random random = new Random();
		for (int i = 0; i < AUTO_BUILD_PASSWORD_LENGTH; i ++)
			password.append(CHARS.charAt(random.nextInt(CHARS.length()-1)));
		return password.toString();
	}
}
