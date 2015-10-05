package com.jtouzy.cv.tools.mail;

import com.jtouzy.cv.model.classes.User;

public class MailBuilder {

	public static void sendMailNewPassword(User user, String newPassword) {
		MailManager.sendMail(user.getMail(), 
				             "[CantalVolley.fr] Changement de mot de passe", 
				             getNewPasswordMail(user, newPassword));
	}
	
	public static void sendMailAutoPassword(User user, String newPassword) {
		MailManager.sendMail(user.getMail(), 
				             "[CantalVolley.fr] Nouveau mot de passe", 
				             getAutoPasswordMail(user, newPassword)); 
	}
	
	private static final String getAutoPasswordMail(User user, String newPassword) {
		StringBuilder mail = new StringBuilder();
		mail.append("Bonjour ")
		    .append(user.getFirstName())
		    .append(",\n\n")
		    .append("Suite à votre inscription au championnat 4x4 organisé par le comité départemental ")
		    .append("de volley-ball, un mot de passe vous a été attribué pour pouvoir vous connecter au site ")
		    .append("http://cantalvolley.fr\n")
		    .append("Ce mot de passe est le suivant : ")
		    .append(newPassword)
		    .append("\n")
		    .append("Conservez bien votre mot de passe, il vous servira ensuite à saisir les scores de vos matchs.\n\n")
		    .append("Cordialement,\n")
		    .append("Le Comité Départemental\n\n")
		    .append("NB : Cet e-mail est automatique. Pour d'éventuels problèmes ou questions, vous pouvez répondre à ")
		    .append("l'adresse d'expédition, un membre du comité prendra en charge votre demande.");
		return mail.toString();
	}
	
	private static final String getNewPasswordMail(User user, String newPassword) {
		StringBuilder mail = new StringBuilder();
		mail.append("Bonjour ")
		    .append(user.getFirstName())
		    .append(",\n\n")
		    .append("Votre mot de passe a été modifié sur http://cantalvolley.fr\n")
		    .append("Pour rappel, le nouveau mot de passe est le suivant : ")
		    .append(newPassword)
		    .append("\n")
		    .append("Conservez bien votre mot de passe, il vous servira ensuite à saisir les scores de vos matchs.\n")
		    .append("Si vous n'êtes pas à l'origine du changement de mot de passe, contactez un administrateur au plus vite.\n\n")
		    .append("Cordialement,\n")
		    .append("Le Comité Départemental\n\n")
		    .append("NB : Cet e-mail est automatique. Pour d'éventuels problèmes ou questions, vous pouvez répondre à ")
		    .append("l'adresse d'expédition, un membre du comité prendra en charge votre demande.");
		return mail.toString();
	}
	
}
