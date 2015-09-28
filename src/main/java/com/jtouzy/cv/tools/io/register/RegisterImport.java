package com.jtouzy.cv.tools.io.register;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;

import com.jtouzy.cv.model.classes.Season;
import com.jtouzy.cv.model.classes.SeasonTeam;
import com.jtouzy.cv.model.classes.Team;
import com.jtouzy.cv.model.classes.User;
import com.jtouzy.cv.model.dao.SeasonDAO;
import com.jtouzy.cv.model.dao.SeasonTeamDAO;
import com.jtouzy.cv.model.dao.TeamDAO;
import com.jtouzy.cv.model.dao.UserDAO;
import com.jtouzy.cv.tools.AbstractTool;
import com.jtouzy.cv.tools.Commands;
import com.jtouzy.cv.tools.errors.ToolsException;
import com.jtouzy.dao.DAO;
import com.jtouzy.dao.DAOManager;
import com.jtouzy.dao.errors.DAOCrudException;
import com.jtouzy.dao.errors.DAOInstantiationException;
import com.jtouzy.dao.errors.QueryException;
import com.jtouzy.dao.errors.model.ModelClassDefinitionException;
import com.jtouzy.utils.resources.ResourceUtils;

/**
 * Lecture du fichier d'imporation des inscriptions<br>
 * Format du fichier : <br>
 * <code>
 *   <root>
 *   	<team name="">
 *   		<player name="" firstName="" mail="" tel="">
 *   	</team>	
 *   </root>
 * </code>
 * @author JTO
 */
public class RegisterImport extends AbstractTool {
	private Connection connection;
	private Element rootElement;
	private TeamDAO teamDao;
	private SeasonTeamDAO stDao;
	private UserDAO userDao;
	private Season currentSeason;
	private static final Logger logger = LogManager.getLogger(RegisterImport.class);
	
	public RegisterImport(CommandLine commandLine) {
		super(commandLine);
	}

	@Override
	public void execute() 
	throws ToolsException {
		if (!getCommandLine().hasOption(Commands.FILE_PATH)) {
			throw new ToolsException("Chemin du fichier obligatoire avec le paramètre \"-file\"");
		}
		try {
			init();
			loadData();
		} catch (IOException | ModelClassDefinitionException | SQLException | QueryException | DAOCrudException | DAOInstantiationException ex) {
			if (this.connection != null) {
				try {
					connection.rollback();
					connection.close();
				} catch (SQLException ex2) {
					throw new ToolsException(ex2);
				}
			}
			throw new ToolsException(ex);
		} finally {
			if (this.connection != null) {
				try {
					connection.close();
				} catch (SQLException ex) {
					throw new ToolsException(ex);
				}
			}
		}
	}
	
	private <D extends DAO<T>,T> D getDAO(Class<D> daoClass)
	throws DAOInstantiationException {
		return DAOManager.getDAO(this.connection, daoClass);
	}
	
	private void init()
	throws IOException, ModelClassDefinitionException, SQLException, DAOInstantiationException, QueryException {
		logger.trace("Initialisation du contexte : Properties, classes modèles, connexion SGBD, racine du fichier XML...");
		Properties properties = ResourceUtils.readProperties("tools");
		DAOManager.init("com.jtouzy.cv.model.classes");
		initXmlDocument();
		connection = DriverManager.getConnection(properties.getProperty("db.jdbcUrl") + 
				                                 "/" + properties.getProperty("db.databaseName"),
				                                 properties.getProperty("db.admin.user"),
				                                 properties.getProperty("db.admin.password"));
		connection.setAutoCommit(false);
		this.currentSeason = getDAO(SeasonDAO.class).getCurrentSeason();
		this.stDao = getDAO(SeasonTeamDAO.class);
		this.teamDao = getDAO(TeamDAO.class);
		this.userDao = getDAO(UserDAO.class);
	}
	
	private void initXmlDocument()
	throws IOException {
		try {
			SAXBuilder builder = new SAXBuilder();
			File xmlFile = new File(getCommandLine().getOptionValue(Commands.FILE_PATH));
			Document document = (Document) builder.build(xmlFile);
			this.rootElement = document.getRootElement();
		} catch (JDOMException ex) {
			throw new IOException(ex);
		}
	}
	
	private void loadData()
	throws QueryException, ToolsException, DAOCrudException, SQLException, DAOInstantiationException {
		List<Element> teamElements = rootElement.getChildren("team");
		Iterator<Element> it = teamElements.iterator();
		Element element;
		String teamName;
		Team team;
		while (it.hasNext()) {
			element = it.next();
			teamName = element.getAttributeValue("name");
			if (teamName == null || teamName.isEmpty()) {
				throw new ToolsException("Un nom d'équipe est manquant");
			}
			team = loadTeam(teamName);
			loadSeasonTeam(team);
			loadPlayers(element);
		}
		connection.commit();
	}
	
	private Team loadTeam(String teamName)
	throws ToolsException, QueryException {
		logger.trace("Chargement de l'équipe " + teamName + "...");
		Team team = retrieveTeam(teamName);
		if (team == null) {
			logger.trace("L'équipe n'as pas été retrouvée : Création d'un nouvel enregistrement");
			team = new Team();
			team.setLabel(teamName);
			// FIXME this.teamDao.create(team);
		} else {
			logger.trace("Equipe existante : Pas de création");
		}
		return team;
	}
	
	private void loadSeasonTeam(Team team)
	throws DAOCrudException {
		logger.trace("Enregistrement de l'équipe pour la saison courante...");
		SeasonTeam seasonTeam = new SeasonTeam();
		seasonTeam.setSeason(currentSeason);
		// seasonTeam.setDate(date); --> CALCUL DATE PAR RAPPORT AU XML
		// seasonTeam.setGym(gym); --> RECHERCHE GYMNASE
		seasonTeam.setState(SeasonTeam.State.I);
		seasonTeam.setTeam(team);
		// FIXME stDao.create(seasonTeam);
	}
	
	private Team retrieveTeam(String teamName)
	throws QueryException, ToolsException {
		List<Team> teams = this.teamDao.getAllByName(teamName);
		if (teams.size() == 0)
			return null;
		if (teams.size() == 1)
			return teams.get(0);
		throw new ToolsException("Plusieurs équipes trouvées avec le nom [" + teamName + "]");
	}
	
	private void loadPlayers(Element teamElement)
	throws QueryException, ToolsException, DAOCrudException {
		List<Element> playerElements = teamElement.getChildren("player");
		Iterator<Element> it = playerElements.iterator();
		Element playerElement;
		String name, firstName;
		User user;
		while (it.hasNext()) {
			playerElement = it.next();
			name = playerElement.getAttributeValue("name");
			firstName = playerElement.getAttributeValue("firstName");
			if (name == null || firstName == null || name.isEmpty() || firstName.isEmpty()) {
				throw new ToolsException("Un nom ou prénom de joueur n'est pas défini dans l'équipe [" + teamElement.getAttributeValue("name") + "]");
			}
			user = loadUser(name, firstName);
			loadSeasonTeamPlayer(user);
		}
	}
	
	private User loadUser(String name, String firstName)
	throws QueryException, ToolsException {
		logger.trace("Chargement du joueur " + name + " " + firstName + "...");
		User user = retrieveUser(name, firstName);
		if (user == null) {
			logger.trace("Le joueur n'as pas été retrouvé : Création d'un nouvel enregistrement");
			user = new User();
			user.setName(name);
			user.setFirstName(firstName);
			// user.setBirthDate("");
			// user.setMail("");
			// user.setPassword("");
			// user.setPhone("");
			// FIXME this.userDao.create(user);
		} else {
			logger.trace("Joueur existant : Pas de création");
		}
		return user;
	}
	
	private void loadSeasonTeamPlayer(User user)
	throws DAOCrudException {
		
	}
	
	private User retrieveUser(String name, String firstName)
	throws QueryException, ToolsException {
		List<User> user = userDao.getAllByNames(name, firstName);
		if (user.size() == 0)
			return null;
		if (user.size() == 1)
			return user.get(0);
		throw new ToolsException("Plusieurs joueurs trouvés avec le nom [" + name + " " + firstName + "]");
	}
}
