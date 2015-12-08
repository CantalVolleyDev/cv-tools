package com.jtouzy.cv.tools.mail;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.jtouzy.cv.model.classes.Match;
import com.jtouzy.cv.model.classes.User;

public class MailBuilder {

	public static void sendMailMatchDateChanged(Match match, LocalDateTime oldDate, String dest) {
		MailManager.sendMail(dest, 
				             "[CantalVolley.fr] Modification d'une date de match",
				 			 getMatchDateChangedMail(match, oldDate));
	}
	
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
	
	private static final String getMatchDateChangedMail(Match match, LocalDateTime oldDate) {
		StringBuilder mail = new StringBuilder();
		mail.append("Bonjour,")
		    .append("\n\n")
		    .append("La date du match ")
		    .append(match.getFirstTeam().getLabel()).append("/").append(match.getSecondTeam().getLabel()).append(" ")
		    .append("a été modifiée par votre adversaire. Vous n'avez pas besoin de confirmer cette nouvelle date, ")
		    .append("elle est automatiquement validée. Si elle ne vous convient pas, merci de vous adresser directement ")
		    .append("à l'équipe adverse.")
		    .append("\n")
		    .append("La nouvelle date est ")
		    .append(match.getDate().format(DateTimeFormatter.ofPattern("EEEE dd-MM-yyyy à HH:mm"))).append(" ")
		    .append("(Date précédente : ")
		    .append(oldDate.format(DateTimeFormatter.ofPattern("EEEE dd-MM-yyyy à HH:mm")))
		    .append(")")
			.append("\n\n")
			.append("Cordialement,\n")
		    .append("Le Comité Départemental\n\n")
		    .append("NB : Cet e-mail est automatique. Pour d'éventuels problèmes ou questions, vous pouvez répondre à ")
		    .append("l'adresse d'expédition, un membre du comité prendra en charge votre demande.");
		return mail.toString();
	}
}
