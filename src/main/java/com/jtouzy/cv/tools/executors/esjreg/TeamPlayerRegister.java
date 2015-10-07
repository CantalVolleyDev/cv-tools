package com.jtouzy.cv.tools.executors.esjreg;

import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.base.CaseFormat;
import com.google.common.base.Strings;
import com.jtouzy.cv.model.classes.SeasonTeam;
import com.jtouzy.cv.model.classes.SeasonTeamPlayer;
import com.jtouzy.cv.model.classes.User;
import com.jtouzy.cv.model.dao.SeasonDAO;
import com.jtouzy.cv.model.dao.SeasonTeamPlayerDAO;
import com.jtouzy.cv.model.dao.UserDAO;
import com.jtouzy.cv.tools.ToolLauncher;
import com.jtouzy.cv.tools.errors.ToolsException;
import com.jtouzy.cv.tools.model.ParameterNames;
import com.jtouzy.cv.tools.model.ToolExecutorImpl;
import com.jtouzy.cv.tools.model.ToolsList;
import com.jtouzy.dao.errors.DAOCrudException;
import com.jtouzy.dao.errors.DAOInstantiationException;
import com.jtouzy.dao.errors.QueryException;
import com.jtouzy.dao.errors.validation.DataValidationException;

public class TeamPlayerRegister extends ToolExecutorImpl {
	private User targetUser;
	private static final Logger logger = LogManager.getLogger(TeamPlayerRegister.class);
	
	@Override
	public void execute() {
		initializeContext();
		controlParameters();
		findUser();
		completeAndCommitUser();
	}
	
	private void controlParameters() {
		if (!hasParameter(ParameterNames.ID))
			throw new ToolsException("Numéro d'équipe absent");
		
		if (!hasParameter(ParameterNames.IDU)) {
			if (!hasParameter(ParameterNames.NAME))
				throw new ToolsException("Nom de l'utilisateur absent");
			if (!hasParameter(ParameterNames.FIRSTNAME))
				throw new ToolsException("Prénom de l'utilisateur absent");
		}
	}
	
	private String getUserNameSubmit() {
		return getParameterValue(ParameterNames.NAME).toUpperCase();
	}
	
	private String getUserFirstNameSubmit() {
		return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, getParameterValue(ParameterNames.FIRSTNAME).toUpperCase());
	}
	
	private void findUser() {
		try {
			if (hasParameter(ParameterNames.IDU)) {
				targetUser = getDAO(UserDAO.class).getOne(Integer.parseInt(getParameterValue(ParameterNames.IDU)));
				if (targetUser == null)
					throw new ToolsException("Utilisateur avec l'identifiant " + getParameterValue(ParameterNames.IDU) + " inexistant");
				else
					logger.trace("Utilisateur existant à modifier");
			} else {
				List<User> users = getDAO(UserDAO.class).getAllByNames(getUserNameSubmit(), getUserFirstNameSubmit());
				if (users.size() > 0) {
					if (users.size() == 1)
						throw new ToolsException("Un utilisateur existe déjà avec ce nom et ce prénom");
					else
						throw new ToolsException("Plusieurs utilisateurs existent déjà avec ce nom et ce prénom");
				} else {
					logger.trace("Nouvel utilisateur à créer");
					targetUser = new User();
				}
			}
		} catch (DAOInstantiationException | QueryException ex) {
			throw new ToolsException(ex);
		}
	}
	
	private void completeAndCommitUser() {
		try {
			if (hasParameter(ParameterNames.NAME)) {
				logger.trace("Mise à jour du nom");
				targetUser.setName(getParameterValue(ParameterNames.NAME));
			}
			if (hasParameter(ParameterNames.FIRSTNAME)) {
				logger.trace("Mise à jour du prénom");
				targetUser.setFirstName(getParameterValue(ParameterNames.FIRSTNAME));
			}
			if (hasParameter(ParameterNames.MAIL)) {
				logger.trace("Mise à jour du mail");
				targetUser.setMail(getParameterValue(ParameterNames.MAIL));
			}
			if (hasParameter(ParameterNames.TEL)) {
				logger.trace("Mise à jour du téléphone");
				targetUser.setPhone(getParameterValue(ParameterNames.TEL));
			}
			
			this.connection.setAutoCommit(false);
			targetUser = getDAO(UserDAO.class).createOrUpdate(targetUser);
			if (Strings.isNullOrEmpty(targetUser.getPassword()) && !Strings.isNullOrEmpty(targetUser.getMail())) {
				logger.trace("Appel de la génération d'un mot de passe");
				ToolLauncher.build()
							.target(ToolsList.PWD_GEN)
				            .useConnection(this.connection)
				            .addParameter(ParameterNames.ID, targetUser.getIdentifier())
				            .run();
			}
			
			Integer teamId = Integer.parseInt(getParameterValue(ParameterNames.ID));
			List<SeasonTeamPlayer> stpList = getDAO(SeasonTeamPlayerDAO.class).getAllBySeasonAndPlayer(
					getDAO(SeasonDAO.class).getCurrentSeason().getIdentifier(), targetUser.getIdentifier());
			if (stpList.size() > 0) {
				logger.trace("L'utilisateur est déjà présent dans une équipe cette année");
				Iterator<SeasonTeamPlayer> it = stpList.iterator();
				while (it.hasNext()) {
					if (it.next().getTeam().getIdentifier() == teamId) {
						throw new ToolsException("L'utilisateur existe déjà dans l'équipe! Aucune modification n'as été faite");
					}
				}
			} else {
				logger.trace("Création de l'utilisateur/équipe");
				SeasonTeam team = new SeasonTeam();
				team.setIdentifier(teamId);
				SeasonTeamPlayer stp = new SeasonTeamPlayer();
				stp.setManager(false);
				stp.setPlayer(targetUser);
				stp.setTeam(team);
				getDAO(SeasonTeamPlayerDAO.class).create(stp);
			}
			this.connection.commit();
			this.connection.setAutoCommit(true);
		} catch (DAOInstantiationException | DAOCrudException | DataValidationException | SQLException | ToolsException | QueryException ex) {
			try {
				if (!this.connection.getAutoCommit()) {
					this.connection.rollback();
					this.connection.setAutoCommit(true);
				}
			} catch (SQLException ex2) {
				throw new ToolsException(ex2);
			}
			throw new ToolsException(ex);
		}
	}
}
