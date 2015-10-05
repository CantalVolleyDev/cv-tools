package com.jtouzy.cv.tools.executors.pwdgen;

import com.google.common.base.Strings;
import com.jtouzy.cv.model.classes.User;
import com.jtouzy.cv.model.dao.UserDAO;
import com.jtouzy.cv.security.UserPassword;
import com.jtouzy.cv.tools.errors.ToolsException;
import com.jtouzy.cv.tools.mail.MailBuilder;
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
		MailManager.init();
		if (!hasParameter(ParameterNames.ID))
			throw new ToolsException("Identifiant de l'utilisateur absent");
		
		User user = getUser();
		if (user == null)
			throw new ToolsException("L'utilisateur identifié par " + getParameterValue(ParameterNames.ID) + " n'existe pas");
		
		String mail = user.getMail();
		if (Strings.isNullOrEmpty(mail))
			throw new ToolsException("L'utilisateur ne possède pas d'adresse mail, aucune génération de mot de passe");

		String newPassword = UserPassword.buildString();
		user.setPassword(UserPassword.getFullHashedNewPassword(newPassword));
		
		try {
			userDao.update(user);
		} catch (DAOCrudException | DataValidationException ex) {
			throw new ToolsException(ex); 
		}
		
		MailBuilder.sendMailAutoPassword(user, newPassword);
	}
	
	public User getUser() {
		try {
			userDao = getDAO(UserDAO.class); 
			return userDao.getOne(Integer.parseInt(getParameterValue(ParameterNames.ID)));
		} catch (DAOInstantiationException | QueryException ex) {
			throw new ToolsException(ex);
		}
	}
}
