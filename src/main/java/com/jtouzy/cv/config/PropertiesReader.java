package com.jtouzy.cv.config;

import java.io.IOException;
import java.util.Properties;

import com.jtouzy.utils.resources.ResourceUtils;

public class PropertiesReader {
	private static boolean initialized = false;
	private static Properties properties;
	
	public static void init(String fileName)
	throws IOException {
		if (PropertiesReader.initialized)
			return;
		readProperties(fileName);
		PropertiesReader.initialized = true;
	}
	
	private static void readProperties(String fileName)
	throws IOException {
		properties = ResourceUtils.readProperties(fileName);
	}
	
	public static String getProperty(String name) {
		String prop = properties.getProperty(name);
		if (prop == null)
			throw new IllegalArgumentException("Paramètre " + name + " absent de la configuratoin");
		return prop;
	}
	
	public static String getJDBCUrl() {
		return getJDBCUrlWithDatabase(properties.getProperty(PropertiesNames.DB_DATABASE_PROPERTY));
	}
	
	public static String getJDBCUrlWithDatabase(String databaseName) {
		return new StringBuilder()
				.append(properties.getProperty(PropertiesNames.DB_JDBCURL_PROPERTY))
				.append("/")
				.append(databaseName)
				.toString();
	}
}
