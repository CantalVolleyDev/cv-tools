package com.jtouzy.cv.tools.io.register;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
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

import com.google.common.base.Strings;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.jtouzy.cv.model.classes.Season;
import com.jtouzy.cv.model.classes.SeasonTeam;
import com.jtouzy.cv.model.classes.SeasonTeamPlayer;
import com.jtouzy.cv.model.classes.Team;
import com.jtouzy.cv.model.classes.User;
import com.jtouzy.cv.model.dao.SeasonDAO;
import com.jtouzy.cv.model.dao.SeasonTeamDAO;
import com.jtouzy.cv.model.dao.SeasonTeamPlayerDAO;
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
import com.jtouzy.dao.errors.validation.DataValidationException;
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
	
	private Season currentSeason;
	private HashMap<String,Team> teamsByName;
	private Multimap<String,User> usersByName;

	private HashMap<String,Team> teamsFromXML;
	private Multimap<String,User> usersFromXML;
	private Multimap<String,String> usersByTeamsFromXML;
	
	private List<User> usersToCreate;
	private List<User> existingUsers;
	private List<Team> teamsToCreate;
	private List<Team> existingTeams;
	
	private static final Logger logger = LogManager.getLogger(RegisterImport.class);
	
	public RegisterImport(CommandLine commandLine) {
		super(commandLine);
	}
	
	private <D extends DAO<T>,T> D getDAO(Class<D> daoClass)
	throws DAOInstantiationException {
		return DAOManager.getDAO(this.connection, daoClass);
	}
	
	@Override
	public void execute() 
	throws ToolsException {
		if (!getCommandLine().hasOption(Commands.FILE_PATH)) {
			throw new ToolsException("Chemin du fichier obligatoire avec le paramètre \"-file\"");
		}
		try {
			boolean isSimulation = getCommandLine().hasOption(Commands.SIMULATION); 
			this.initializeContext();
			this.searchOldData();
			this.loadXMLData();
			this.printXMLResult();
			this.markDataToCreate();
			this.printDataToCreate();
			if (!isSimulation) {
				this.createBaseData();
				this.createSeasonData();
				this.connection.commit();
				this.connection.close();
			}
		} catch (IOException | ModelClassDefinitionException | SQLException | QueryException ex) {
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

	private void initializeContext()
	throws IOException, ModelClassDefinitionException, SQLException, QueryException {
		try {
			logger.trace("Initialisation du contexte...");
			Properties properties = ResourceUtils.readProperties("tools");
			DAOManager.init("com.jtouzy.cv.model.classes");
			connection = DriverManager.getConnection(new StringBuilder().append(properties.getProperty("db.jdbcUrl"))
	                												    .append("/")
	        												    		.append(properties.getProperty("db.databaseName"))
	        												    		.toString(),
										    		 properties.getProperty("db.admin.user"),
										    		 properties.getProperty("db.admin.password"));
			connection.setAutoCommit(false);
			this.currentSeason = getDAO(SeasonDAO.class).getCurrentSeason();
			this.initXmlDocument();
		} catch (DAOInstantiationException ex) {
			throw new QueryException(ex);
		}
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
	
	private void searchOldData()
	throws QueryException {
		logger.trace("Recherche des équipes et utilisateurs existants...");
		this.searchUsers();
		this.searchTeams();
	}
	
	private void searchUsers()
	throws QueryException {
		try {
			this.usersByName = HashMultimap.create();
			List<User> users = getDAO(UserDAO.class).getAll();
			Iterator<User> itu = users.iterator();
			User user;
			while (itu.hasNext()) {
				user = itu.next();
				this.usersByName.put(getUserFullName(user), user);
			}
		} catch (DAOInstantiationException ex) {
			throw new QueryException(ex);
		}
	}
	
	private final String getUserFullName(User user) {
		return new StringBuilder().append(user.getName().toUpperCase())
                                  .append(" ")
                                  .append(user.getFirstName().toUpperCase())
                                  .toString();
	}
	
	private final String getUserFullName(Element userElement) {
		return new StringBuilder().append(userElement.getAttributeValue("name").toUpperCase())
				                  .append(" ")
				                  .append(userElement.getAttributeValue("firstName").toUpperCase())
				                  .toString();
	}
	
	private void searchTeams()
	throws QueryException {
		try {
			this.teamsByName = new HashMap<>();
			List<Team> teams = getDAO(TeamDAO.class).getAll();
			Iterator<Team> itt = teams.iterator();
			Team team;
			String fullName;
			while (itt.hasNext()) {
				team = itt.next();
				fullName = new StringBuilder().append(team.getLabel().toUpperCase())
						                      .toString();
				this.teamsByName.put(fullName, team);
			}
		} catch (DAOInstantiationException ex) {
			throw new QueryException(ex);
		}
	}
	
	private void loadXMLData()
	throws ToolsException {
		logger.trace("Chargement des données depuis le fichier XML...");
		this.teamsFromXML = new HashMap<>();
		this.usersFromXML = HashMultimap.create();
		this.usersByTeamsFromXML = HashMultimap.create();
		
		List<Element> teamElements = rootElement.getChildren("team");
		Iterator<Element> itt = teamElements.iterator();
		Element teamElement;
		while (itt.hasNext()) {
			teamElement = itt.next();
			buildTeamWithXML(teamElement);
		}
	}
	
	private void buildTeamWithXML(Element teamElement)
	throws ToolsException {
		String name = teamElement.getAttributeValue("name"),
				gym = teamElement.getAttributeValue("gym"),
			   date = teamElement.getAttributeValue("date");
		if (Strings.isNullOrEmpty(name))
			throw new ToolsException("Un nom d'équipe est manquant sur l'élément [" + teamElement + "]");
		if (Strings.isNullOrEmpty(gym))
			throw new ToolsException("Gymnase non précisé pour l'équipe [" + name + "]");
		if (Strings.isNullOrEmpty(date))
			throw new ToolsException("Date de match non précisée pour l'équipe [" + name + "]");
		//FIXME: Contrôle de cohérence du gymnase + Date
		Team team = new Team();
		team.setLabel(name);
		this.teamsFromXML.put(name.toUpperCase(), team);
		buildPlayersWithTeamXML(team, teamElement);
	}
	
	private void buildPlayersWithTeamXML(Team team, Element teamElement)
	throws ToolsException {
		List<Element> playerElements = teamElement.getChildren("player");
		if (playerElements == null || playerElements.size() == 0)
			throw new ToolsException("Aucun joueur enregistré pour l'équipe [" + teamElement.getAttributeValue("name") + "]");
		Iterator<Element> itp = playerElements.iterator();
		Element playerElement;
		boolean managerFlag = false,
				currentManagerFlag = false;
		while (itp.hasNext()) {
			playerElement = itp.next();
			currentManagerFlag = buildPlayerWithXML(team, playerElement);
			if (currentManagerFlag) {
				if (managerFlag)
					throw new ToolsException("L'équipe [" + teamElement.getAttributeValue("name") + "] possède plusieurs responsables");
				managerFlag = currentManagerFlag;
			}
		}
		if (!managerFlag)
			throw new ToolsException("L'équipe [" + teamElement.getAttributeValue("name") + "] ne possède aucun responsable");
	}
	
	private boolean buildPlayerWithXML(Team team, Element playerElement)
	throws ToolsException {
		boolean managerFlag = false;
		String name = playerElement.getAttributeValue("name"),
			   firstName = playerElement.getAttributeValue("firstName"),
			   manager = playerElement.getAttributeValue("manager"),
			   mail = playerElement.getAttributeValue("mail");
		if (Strings.isNullOrEmpty(name))
			throw new ToolsException("Le nom du joueur doit être renseigné sur l'élément, dans l'équipe [" + team.getLabel() + "]");
		if (Strings.isNullOrEmpty(firstName))
			throw new ToolsException("Le prénom du joueur doit être renseigné sur l'élément, dans l'équipe [" + team.getLabel() + "]");
		if (manager != null && manager.equals("true")) {
			managerFlag = true;
			if (mail == null) {
				throw new ToolsException("Le responsable d'équipe doit avoir un mail! Dans l'équipe [" + team.getLabel() + "]");
			}
		}		
		User user = new User();
		user.setFirstName(firstName);
		user.setName(name);
		user.setAdministrator(false);
		//user.setBirthDate(birthDate);
		user.setMail(playerElement.getAttributeValue("mail"));
		user.setPassword("");
		user.setPhone(playerElement.getAttributeValue("tel"));
		String fullName = getUserFullName(user);
		this.usersFromXML.put(fullName, user);
		this.usersByTeamsFromXML.put(team.getLabel().toUpperCase(), fullName);
		return managerFlag;
	}
	
	private void markDataToCreate()
	throws ToolsException {
		this.usersToCreate = new ArrayList<>();
		this.teamsToCreate = new ArrayList<>();
		this.existingTeams = new ArrayList<>();
		this.existingUsers = new ArrayList<>();
		
		Iterator<String> itu = this.usersFromXML.keySet().iterator();
		String fullName;
		while (itu.hasNext()) {
			fullName = itu.next();
			if (!this.usersByName.containsKey(fullName)) {
				this.usersToCreate.add(Lists.newArrayList(this.usersFromXML.get(fullName)).get(0));
			} else {
				this.existingUsers.add(Lists.newArrayList(this.usersFromXML.get(fullName)).get(0));
			}
		}
		itu = this.teamsFromXML.keySet().iterator();
		while (itu.hasNext()) {
			fullName = itu.next();
			if (!this.teamsByName.containsKey(fullName)) {
				this.teamsToCreate.add(this.teamsFromXML.get(fullName));
			} else {
				this.existingTeams.add(this.teamsFromXML.get(fullName));
			}
		}
	}
	
	private void createBaseData()
	throws ToolsException {
		try {
			TeamDAO teamDao = getDAO(TeamDAO.class);
			Iterator<Team> itt = this.teamsToCreate.iterator();
			Team team, newTeam;
			while (itt.hasNext()) {
				team = itt.next();
				newTeam = teamDao.create(team);
				team.setIdentifier(newTeam.getIdentifier());
			}
			UserDAO userDao = getDAO(UserDAO.class);
			Iterator<User> itu = this.usersToCreate.iterator();
			User user, newUser;
			while (itu.hasNext()) {
				user = itu.next();
				newUser = userDao.create(user);
				user.setIdentifier(newUser.getIdentifier());
			}
		} catch (DAOInstantiationException | DAOCrudException | DataValidationException ex) {
			throw new ToolsException(ex);
		}
	}
	
	private void createSeasonData()
	throws ToolsException {
		try {
			Team team;
			SeasonTeam seasonTeam;
			SeasonTeamDAO seasonTeamDao = getDAO(SeasonTeamDAO.class);
			SeasonTeamPlayer seasonTeamPlayer;
			SeasonTeamPlayerDAO seasonTeamPlayerDao = getDAO(SeasonTeamPlayerDAO.class);
			List<Element> teamElements = rootElement.getChildren("team");
			Iterator<Element> itx = teamElements.iterator();
			Element teamElement;
			while (itx.hasNext()) {
				teamElement = itx.next();
				team = this.teamsFromXML.get(teamElement.getAttributeValue("name").toUpperCase());
				seasonTeam = new SeasonTeam();
				seasonTeam.setSeason(this.currentSeason);
				seasonTeam.setTeam(team);
				//seasonTeam.setDate(date);
				//seasonTeam.setGym(gym);
				seasonTeam.setState(SeasonTeam.State.I);
				seasonTeamDao.create(seasonTeam);
				List<Element> playerElements = teamElement.getChildren("player");
				Iterator<Element> itp = playerElements.iterator();
				Element playerElement;
				while (itp.hasNext()) {
					playerElement = itp.next();
					seasonTeamPlayer = new SeasonTeamPlayer();
					seasonTeamPlayer.setPlayer(Lists.newArrayList(this.usersFromXML.get(getUserFullName(playerElement))).get(0));
					seasonTeamPlayer.setSeason(this.currentSeason);
					seasonTeamPlayer.setTeam(team);
					seasonTeamPlayerDao.create(seasonTeamPlayer);
				}
			}
		} catch (DAOInstantiationException | DAOCrudException | DataValidationException ex) {
			throw new ToolsException(ex);
		}
	}
	
	private void printXMLResult() {
		logger.trace("----------------------------------------");
		logger.trace("Résultat du traitement du fichier XML : ");
		logger.trace("----------------------------------------");
		logger.trace("LISTE DES EQUIPES (" + this.teamsFromXML.size() + ")");
		this.teamsFromXML.entrySet().forEach(t -> {
			logger.trace("Equipe : " + t.getValue().getLabel());
		});
		logger.trace("LISTE DES UTILISATEURS (" + this.usersFromXML.size() + ")");
		this.usersFromXML.entries().forEach(u -> {
			logger.trace("Utilisateur : " + u.getValue().getFirstName() + " " + u.getValue().getName());
		});
	}
	
	private void printDataToCreate() {
		logger.trace("----------------------------------------");
		logger.trace("Résultat de la création/existence :     ");
		logger.trace("----------------------------------------");
		logger.trace("LISTE DES EQUIPES A CREER (" + this.teamsToCreate.size() + "/" + this.teamsFromXML.size() + ")");
		this.teamsToCreate.forEach(t -> {
			logger.trace("Equipe : " + t.getLabel());
		});
		logger.trace("LISTE DES UTILISATEURS A CREER (" + this.usersToCreate.size() + "/" + this.usersFromXML.size() + ")");
		this.usersToCreate.forEach(t -> {
			logger.trace("Utilisateur : " + t.getFirstName() + " " + t.getName());
		});
		logger.trace("LISTE DES EQUIPES EXISTANTES ENREGISTREES (" + this.existingTeams.size() + "/" + this.teamsFromXML.size() + ")");
		this.existingTeams.forEach(t -> {
			logger.trace("Equipe : " + t.getLabel());
		});
		logger.trace("LISTE DES UTILISATEURS EXISTANTS ENREGISTRES (" + this.existingUsers.size() + "/" + this.usersFromXML.size() + ")");
		this.existingUsers.forEach(t -> {
			logger.trace("Utilisateur : " + t.getFirstName() + " " + t.getName());
		});
	}
}
