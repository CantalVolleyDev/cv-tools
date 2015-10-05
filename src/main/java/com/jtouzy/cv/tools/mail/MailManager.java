package com.jtouzy.cv.tools.mail;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import com.jtouzy.cv.config.PropertiesNames;
import com.jtouzy.cv.config.PropertiesReader;
import com.jtouzy.cv.tools.errors.MailSendingException;

public class MailManager {
	private static boolean initialized = false;
	private static Session session;
	
	public static void init() {
		if (initialized)
			return;
		
		Properties mailProperties = new Properties();
		mailProperties.put(PropertiesNames.MAIL_SMTP_HOST, PropertiesReader.getProperty(PropertiesNames.MAIL_SMTP_HOST));
		mailProperties.put(PropertiesNames.MAIL_SMTP_AUTH, PropertiesReader.getProperty(PropertiesNames.MAIL_SMTP_AUTH));
		mailProperties.put(PropertiesNames.MAIL_SMTP_PORT, PropertiesReader.getProperty(PropertiesNames.MAIL_SMTP_PORT));
		mailProperties.put(PropertiesNames.MAIL_SMTP_START_TLS, PropertiesReader.getProperty(PropertiesNames.MAIL_SMTP_START_TLS));
		mailProperties.put("mail.smtp.connectiontimeout", "5000");
		mailProperties.put("mail.smtp.timeout", "6000");
		session = Session.getDefaultInstance(mailProperties, 
			new Authenticator() {
				protected PasswordAuthentication getPasswordAuthentication() {
					return new PasswordAuthentication(PropertiesReader.getProperty(PropertiesNames.MAIL_SMTP_USER),
													  PropertiesReader.getProperty(PropertiesNames.MAIL_SMTP_PASSWORD));
				}
			}
		);
		
		MailManager.initialized = true;
	}
	
	public static void sendMail(String destination, String subject, String text) {
		sendMail(Arrays.asList(destination), subject, text);
	}
	
	public static void sendMail(List<String> destinations, String subject, String text) {
		if (!initialized)
			throw new MailSendingException("Configuration de l'envoi de mail inexistante");
		try {
			Message message = new MimeMessage(session);
			message.setFrom(new InternetAddress("contact@cantalvolley.fr", "CantalVolley Webmailing"));
			message.setRecipients(Message.RecipientType.TO, parseDestinations(destinations));
			message.setSubject(subject);
			message.setText(text);
			Transport.send(message);
		} catch (MessagingException | UnsupportedEncodingException ex) {
			throw new MailSendingException(ex);
		}
	}
	
	private static InternetAddress[] parseDestinations(List<String> destinations)
	throws MailSendingException {
		try {
			InternetAddress[] addresses = new InternetAddress[destinations.size()];
			int index = 0;
			for (String address : destinations) {
				addresses[index] = InternetAddress.parse(address)[0];
				index++;
			}
			return addresses;
		} catch (AddressException ex) {
			throw new MailSendingException(ex);
		}
	}
}
