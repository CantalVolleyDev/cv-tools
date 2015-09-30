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
import java.util.Map.Entry;
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
	/*private TeamDAO teamDao;
	private SeasonTeamDAO stDao;
	private UserDAO userDao;
	private Season currentSeason;*/
	
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
			}
			if (!isSimulation) {
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
		String name = teamElement.getAttributeValue("name");
		if (Strings.isNullOrEmpty(name))
			throw new ToolsException("Un nom d'équipe est manquant sur l'élément [" + teamElement + "]");
		Team team = new Team();
		team.setLabel(name);
		this.teamsFromXML.put(name.toUpperCase(), team);
		/*SeasonTeam seasonTeam = new SeasonTeam();
		seasonTeam.setTeam(team);
		seasonTeam.setSeason(currentSeason);
		this.seasonTeamFromXML.add(seasonTeam);*/
		buildPlayersWithTeamXML(team, teamElement);
	}
	
	private void buildPlayersWithTeamXML(Team team, Element teamElement)
	throws ToolsException {
		List<Element> playerElements = teamElement.getChildren("player");
		if (playerElements == null || playerElements.size() == 0)
			throw new ToolsException("Aucun joueur enregistré pour l'équipe [" + teamElement.getAttributeValue("name") + "]");
		Iterator<Element> itp = playerElements.iterator();
		Element playerElement;
		while (itp.hasNext()) {
			playerElement = itp.next();
			buildPlayerWithXML(team, playerElement);
		}
	}
	
	private void buildPlayerWithXML(Team team, Element playerElement)
	throws ToolsException {
		String name = playerElement.getAttributeValue("name"),
			   firstName = playerElement.getAttributeValue("firstName");
		if (Strings.isNullOrEmpty(name))
			throw new ToolsException("Le nom du joueur doit être renseigné sur l'élément [" + playerElement + "]");
		if (Strings.isNullOrEmpty(firstName))
			throw new ToolsException("Le prénom du joueur doit être renseigné sur l'élément [" + playerElement + "]");
		User user = new User();
		user.setFirstName(firstName);
		user.setName(name);
		String fullName = getUserFullName(user);
		this.usersFromXML.put(fullName, user);
		this.usersByTeamsFromXML.put(team.getLabel().toUpperCase(), fullName);
		/*SeasonTeamPlayer player = new SeasonTeamPlayer();
		player.setPlayer(user);
		player.setSeason(currentSeason);
		player.setTeam(team);
		//FIXME : Vérifier les données existante si le joueur existe déjà dans une autre équipe
		this.seasonTeamPlayerFromXML.add(player);*/
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
			Iterator<Team> itt = this.teamsFromXML.values().iterator();
			Team team;
			SeasonTeam seasonTeam;
			SeasonTeamDAO seasonTeamDao = getDAO(SeasonTeamDAO.class);
			while (itt.hasNext()) {
				team = itt.next();
				seasonTeam = new SeasonTeam();
				seasonTeam.setSeason(this.currentSeason);
				seasonTeam.setTeam(team);
				//seasonTeam.setDate(date);
				//seasonTeam.setGym(gym);
				seasonTeam.setState(SeasonTeam.State.I);
				seasonTeamDao.create(seasonTeam);
			}
			
			SeasonTeamPlayer seasonTeamPlayer;
			SeasonTeamPlayerDAO seasonTeamPlayerDao = getDAO(SeasonTeamPlayerDAO.class);
			List<Element> teamElements = rootElement.getChildren("team");
			Iterator<Element> itx = teamElements.iterator();
			Element teamElement;
			while (itx.hasNext()) {
				teamElement = itx.next();
				team = this.teamsFromXML.get(teamElement.getAttributeValue("name").toUpperCase());
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
		/*logger.trace("LISTE DES EQUIPES/SAISONS (" + this.seasonTeamFromXML.size() + ")");
		this.seasonTeamFromXML.forEach(st -> {
			logger.trace("Equipe/Saison : " + st.getTeam().getLabel());
		});
		logger.trace("LISTE DES EQUIPES/SAISONS/JOUEUR (" + this.seasonTeamPlayerFromXML.size() + ")");
		this.seasonTeamPlayerFromXML.forEach(stp -> {
			logger.trace("Equipe/Saison/Joueur : " + stp.getTeam().getLabel() + " / " + stp.getPlayer().getFirstName() + " " + stp.getPlayer().getName());
		});*/
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
	
	/*private void init()
	throws IOException, ModelClassDefinitionException, SQLException, DAOInstantiationException, QueryException {
		logger.trace("Initialisation du contexte : Properties, classes modèles, connexion SGBD, racine du fichier XML...");
		Properties properties = ResourceUtils.readProperties("tools");
		DAOManager.init("com.jtouzy.cv.model.classes");
		initXmlDocument();

		this.currentSeason = getDAO(SeasonDAO.class).getCurrentSeason();
		this.stDao = getDAO(SeasonTeamDAO.class);
		this.teamDao = getDAO(TeamDAO.class);
		this.userDao = getDAO(UserDAO.class);
	}*/
	
	/*private void loadData()
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
	}*/
}
