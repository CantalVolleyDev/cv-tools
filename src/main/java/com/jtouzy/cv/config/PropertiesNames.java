package com.jtouzy.cv.config;

public class PropertiesNames {
	// -----------------------------------------------------------
	// Propriétés pour l'accès à la base de données
	// -----------------------------------------------------------
	public static final String DB_ADMIN_USER_PROPERTY = "db.admin.user";
	public static final String DB_ADMIN_PASSWORD_PROPERTY = "db.admin.password";
	public static final String DB_PUBLIC_USER_PROPERTY = "db.public.user";
	public static final String DB_PUBLIC_PASSWORD_PROPERTY = "db.public.password";
	public static final String DB_DATABASE_PROPERTY = "db.databaseName";
	public static final String DB_JDBCURL_PROPERTY = "db.jdbcUrl";
	public static final String DB_DRIVER_CLASSNAME = "db.driverClassName";
	// -----------------------------------------------------------
	// Domaines autorisés pour l'accès à l'API
	// -----------------------------------------------------------
	public static final String ORIGIN_ALLOWED = "origin.allowed";
	// -----------------------------------------------------------
	// Mot de passe générique pour la connexion utilisateur
	// -----------------------------------------------------------
	public static final String GLOBAL_PASSWORD = "global.password";
	// -----------------------------------------------------------
	// Chemin d'upload des fichiers
	// -----------------------------------------------------------
	public static final String FILE_UPLOAD_PATH = "file.upload";
	public static final String FILE_REMOTE_UPLOAD_PATH = "file.remote.upload";
	// -----------------------------------------------------------
	// Accès FTP au serveur du client
	// -----------------------------------------------------------
	public static final String WEBAPP_FTP_HOST = "webapp.ftp.host";
	public static final String WEBAPP_FTP_USER = "webapp.ftp.user";
	public static final String WEBAPP_FTP_PASSWORD = "webapp.ftp.password";
	// -----------------------------------------------------------
	// Propriétés pour l'envoi de mails
	// -----------------------------------------------------------
	public static final String MAIL_SMTP_HOST = "mail.smtp.host";
	public static final String MAIL_SMTP_START_TLS = "mail.smtp.starttls.enable";
	public static final String MAIL_SMTP_AUTH = "mail.smtp.auth";
	public static final String MAIL_SMTP_PORT = "mail.smtp.port";
	public static final String MAIL_SMTP_USER = "mail.smtp.user";
	public static final String MAIL_SMTP_PASSWORD = "mail.smtp.password"; 
}
