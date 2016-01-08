package com.jtouzy.cv.tools.mail;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.jtouzy.cv.model.classes.Gym;
import com.jtouzy.cv.model.classes.Match;
import com.jtouzy.cv.model.classes.User;

public class MailBuilder {

	public static void sendMailMatchInfosChanged(Match match, LocalDateTime oldDate, Gym oldGym, String dest) {
		boolean gymChanged  = !match.getGym().getIdentifier().equals(oldGym.getIdentifier());
		boolean dateChanged = !match.getDate().isEqual(oldDate);
		if (gymChanged || dateChanged) {
			MailManager.sendMail(dest, 
					             "[CantalVolley.fr] Modification d'un match",
					 			 getMatchInfosChangedMail(match, oldDate, oldGym));
		}
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
	
	private static final String getMatchInfosChangedMail(Match match, LocalDateTime oldDate, Gym oldGym) {
		StringBuilder mail = new StringBuilder();
		mail.append("Bonjour,")
		    .append("\n\n")
		    .append("Les informations d'un match ont été modifiées.")
		    .append("\n\n");
		
		boolean gymChanged  = !match.getGym().getIdentifier().equals(oldGym.getIdentifier());
		boolean dateChanged = !match.getDate().isEqual(oldDate);
		
		if (dateChanged) {
			mail.append("La date du match ")
		    	.append(match.getFirstTeam().getLabel()).append("/").append(match.getSecondTeam().getLabel()).append(" ")
		    	.append("a été modifiée par votre adversaire.")
		    	.append("\n")
		    	.append("La nouvelle date est ")
		    	.append(match.getDate().format(DateTimeFormatter.ofPattern("EEEE dd-MM-yyyy à HH:mm"))).append(" ")
		    	.append("(Date précédente : ")
		    	.append(oldDate.format(DateTimeFormatter.ofPattern("EEEE dd-MM-yyyy à HH:mm")))
		    	.append(")")
		    	.append("\n\n");
		}
		
		if (gymChanged) {
			mail.append("Le gymnase du match ")
				.append(match.getFirstTeam().getLabel()).append("/").append(match.getSecondTeam().getLabel()).append(" ")
				.append("a été modifié par votre adversaire.")
				.append("\n")
				.append("Le nouveau gymnase est [")
				.append(match.getGym().getLabel())
				.append("] (Gymnase précédent : ")
				.append(oldGym.getLabel())
				.append(")")
				.append("\n\n");
		}
		
		mail.append("Vous n'avez pas besoin de confirmer ces nouvelles informations, elles sont automatiquement ")
		    .append("validées sur le site. Si elles ne vous conviennent pas, merci de vous adresser directement ")
		    .append("au responsable de l'équipe adverse")
		    .append("\n\n")		
			.append("Cordialement,\n")
		    .append("Le Comité Départemental\n\n")
		    .append("NB : Cet e-mail est automatique. Pour d'éventuels problèmes ou questions, vous pouvez répondre à ")
		    .append("l'adresse d'expédition, un membre du comité prendra en charge votre demande.");
		return mail.toString();
	}
}
