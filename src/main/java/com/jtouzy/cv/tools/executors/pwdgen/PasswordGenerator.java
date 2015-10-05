package com.jtouzy.cv.tools.executors.pwdgen;

import com.google.common.base.Strings;
import com.jtouzy.cv.model.classes.User;
import com.jtouzy.cv.model.dao.UserDAO;
import com.jtouzy.cv.security.UserPassword;
import com.jtouzy.cv.tools.errors.ToolsException;
import com.jtouzy.cv.tools.mail.MailManager;
import com.jtouzy.cv.tools.model.ParameterNames;
import com.jtouzy.cv.tools.model.ToolExecutorImpl;
import com.jtouzy.dao.errors.DAOCrudException;
import com.jtouzy.dao.errors.DAOInstantiationException;
import com.jtouzy.dao.errors.QueryException;
import com.jtouzy.dao.errors.validation.DataValidationException;

public class PasswordGenerator extends ToolExecutorImpl {
	private UserDAO userDao;
	
	@Override
	public void execute() {
		initializeContext();
		if (!hasParameter(ParameterNames.ID))
			throw new ToolsException("Identifiant de l'utilisateur absent");
		
		User user = getUser();
		if (user == null)
			throw new ToolsException("L'utilisateur identifié par " + getParameterValue(ParameterNames.ID) + " n'existe pas");
		
		String mail = user.getMail();
		if (Strings.isNullOrEmpty(mail))
			throw new ToolsException("L'utilisateur ne possède pas d'adresse mail, aucune génération de mot de passe");

		user.setPassword(UserPassword.buildString());
		String mailContent = getNewPasswordMail(user);
		user.setPassword(UserPassword.getFullHashedNewPassword(user.getPassword()));
		
		try {
			userDao.update(user);
		} catch (DAOCrudException | DataValidationException ex) {
			throw new ToolsException(ex); 
		}
		
		MailManager.sendMail(mail, "[CantalVolley.fr] Nouveau mot de passe", mailContent);
	}
	
	public User getUser() {
		try {
			userDao = getDAO(UserDAO.class); 
			return userDao.getOne(Integer.parseInt(getParameterValue(ParameterNames.ID)));
		} catch (DAOInstantiationException | QueryException ex) {
			throw new ToolsException(ex);
		}
	}
	
	private final String getNewPasswordMail(User user) {
		StringBuilder mail = new StringBuilder();
		mail.append("Bonjour ")
		    .append(user.getFirstName())
		    .append(",\n\n")
		    .append("Suite à votre inscription au championnat 4x4 organisé par le comité départemental ")
		    .append("de volley-ball, un mot de passe vous a été attribué pour pouvoir vous connecter au site ")
		    .append("http://cantalvolley.fr\n")
		    .append("Ce mot de passe est le suivant : ")
		    .append(user.getPassword())
		    .append("\n")
		    .append("Conservez bien votre mot de passe, il vous servira ensuite à saisir les scores de vos matchs.\n\n")
		    .append("Cordialement,\n")
		    .append("Le Comité Départemental\n\n")
		    .append("NB : Cet e-mail est automatique. Pour d'éventuels problèmes ou questions, vous pouvez répondre à ")
		    .append("l'adresse d'expédition, un membre du comité prendra en charge votre demande.");
		return mail.toString();
	}
}
