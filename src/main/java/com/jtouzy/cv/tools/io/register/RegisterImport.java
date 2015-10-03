package com.jtouzy.cv.tools.io.register;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
import com.google.common.collect.Multimap;
import com.jtouzy.cv.model.classes.Championship;
import com.jtouzy.cv.model.classes.ChampionshipTeam;
import com.jtouzy.cv.model.classes.Gym;
import com.jtouzy.cv.model.classes.Season;
import com.jtouzy.cv.model.classes.SeasonTeam;
import com.jtouzy.cv.model.classes.SeasonTeamPlayer;
import com.jtouzy.cv.model.classes.Team;
import com.jtouzy.cv.model.classes.User;
import com.jtouzy.cv.model.dao.ChampionshipDAO;
import com.jtouzy.cv.model.dao.ChampionshipTeamDAO;
import com.jtouzy.cv.model.dao.GymDAO;
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
	/**
	 * Connexion à la base de données
	 */
	private Connection connection;
	
	/**
	 * Element principal du fichier XML en entrée
	 */
	private Element rootElement;
	/**
	 * Est-ce qu'on lance le traitement en simulation ou non?
	 */
	private boolean simulation = false;
	
	/**
	 * Saison principale dans laquelle on va insérer les données
	 */
	private Season currentSeason;
	/**
	 * Liste des équipes déjà existantes indexées par leurs ID
	 */
	private HashMap<Integer,Team> teamsById;
	/**
	 * Liste des joueurs déjà existants indexés par leurs ID
	 */
	private HashMap<Integer,User> usersById;
	/**
	 * Liste des gymnases existants indexés par leurs ID
	 */
	private HashMap<Integer,Gym> gymsById;
	/**
	 * Liste des championnats existants indexés par leurs ID
	 */
	private HashMap<Integer,Championship> championshipsById;
	
	/**
	 * Liste des nouveaux utilisateurs à créer
	 */
	private List<User> usersToCreate;
	/**
	 * Liste des utilisateurs participants déjà existants
	 */
	private List<User> existingUsers;
	/**
	 * Liste des nouvelles équipes à créer
	 */
	private List<Team> teamsToCreate;
	/**
	 * Liste des équipes participantes déjà existantes
	 */
	private List<Team> existingTeams;
	/**
	 * Liste des équipes/saison à créer
	 */
	private List<SeasonTeam> seasonTeamsToCreate;
	/**
	 * Liste des équipes/saison/joueurs à créer
	 */
	private List<SeasonTeamPlayer> seasonTeamPlayersToCreate;
	/**
	 * Liste des équipes/championnats à créer
	 */
	private Multimap<Integer,ChampionshipTeam> championshipTeamsToCreate;
	/**
	 * Logger de l'outil
	 */
	private static final Logger logger = LogManager.getLogger(RegisterImport.class);
	
	/**
	 * Constructeur
	 * @param commandLine Informations sur la ligne de commande lancée
	 */
	public RegisterImport(CommandLine commandLine) {
		super(commandLine);
	}
	
	/**
	 * Récupération d'un DAO par sa classe
	 * @param daoClass Classe du DAO demandée
	 * @return Instance du DAO
	 * @throws DAOInstantiationException Si problème dans l'instanciation du DAO
	 */
	private <D extends DAO<T>,T> D getDAO(Class<D> daoClass)
	throws DAOInstantiationException {
		return DAOManager.getDAO(this.connection, daoClass);
	}
	
	/**
	 * Exécution générale de l'outil
	 * - Lecture du fichier XML
	 * - Intégration des nouvelles équipes et nouveaux joueurs
	 * - Intégration des équipes/saison et équipes/saison/championnat
	 * @throws ToolsException Si un problème survient pendant l'exécution
	 */
	@Override
	public void execute() 
	throws ToolsException {
		if (!getCommandLine().hasOption(Commands.FILE_PATH)) {
			throw new ToolsException("Chemin du fichier obligatoire avec le paramètre \"-file\"");
		}
		try {
			this.simulation = getCommandLine().hasOption(Commands.SIMULATION); 
			this.initializeContext();
			this.searchOldData();
			this.loadXMLData();
			this.printDataToCreate();
			this.connection.commit();
			this.connection.close();
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

	/**
	 * Initialisation du contexte de l'outil
	 * @throws IOException 
	 * @throws ModelClassDefinitionException
	 * @throws SQLException
	 * @throws QueryException
	 */
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
			this.existingTeams = new ArrayList<>();
			this.existingUsers = new ArrayList<>();
			this.usersToCreate = new ArrayList<>();
			this.teamsToCreate = new ArrayList<>();
			this.seasonTeamsToCreate = new ArrayList<>();
			this.seasonTeamPlayersToCreate = new ArrayList<>();
			this.championshipTeamsToCreate = HashMultimap.create();
		} catch (DAOInstantiationException ex) {
			throw new QueryException(ex);
		}
	}
	
	/**
	 * Recherche de l'élément racine du document XML
	 * @throws IOException
	 */
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
	
	/**
	 * Recherche des données existantes en base de données
	 * @throws QueryException Si problème dans la recherche des données
	 */
	private void searchOldData()
	throws QueryException {
		logger.trace("Recherche des équipes et utilisateurs existants...");
		this.searchUsers();
		this.searchTeams();
		this.searchGyms();
		this.searchChampionships();
	}
	
	/**
	 * Recherche des utilisateurs déjà existant dans la base
	 * @throws QueryException Si problème dans la recherche des données
	 */
	private void searchUsers()
	throws QueryException {
		try {
			this.usersById = new HashMap<>();
			List<User> users = getDAO(UserDAO.class).getAll();
			Iterator<User> itu = users.iterator();
			User user;
			while (itu.hasNext()) {
				user = itu.next();
				this.usersById.put(user.getIdentifier(), user);
			}
		} catch (DAOInstantiationException ex) {
			throw new QueryException(ex);
		}
	}
	
	/**
	 * Recherche des équipes déjà existantes dans la base
	 * @throws QueryException Si problème dans la recherche des données
	 */
	private void searchTeams()
	throws QueryException {
		try {
			this.teamsById = new HashMap<>();
			List<Team> teams = getDAO(TeamDAO.class).getAll();
			Iterator<Team> itt = teams.iterator();
			Team team;
			while (itt.hasNext()) {
				team = itt.next();
				this.teamsById.put(team.getIdentifier(), team);
			}
		} catch (DAOInstantiationException ex) {
			throw new QueryException(ex);
		}
	}
	
	/**
	 * Recherche des gymnases dans la base
	 * @throws QueryException Si problème dans la recherche des données
	 */
	private void searchGyms()
	throws QueryException {
		try {
			this.gymsById = new HashMap<>();
			List<Gym> gyms = getDAO(GymDAO.class).getAll();
			Iterator<Gym> itt = gyms.iterator();
			Gym gym;
			while (itt.hasNext()) {
				gym = itt.next();
				this.gymsById.put(gym.getIdentifier(), gym);
			}
		} catch (DAOInstantiationException ex) {
			throw new QueryException(ex);
		}
	}
	
	/**
	 * Recherche des championnats dans la base
	 * @throws QueryException Si problème dans la recherche des données
	 */
	private void searchChampionships()
	throws QueryException {
		try {
			this.championshipsById = new HashMap<>();
			List<Championship> chps = getDAO(ChampionshipDAO.class).getAll();
			Iterator<Championship> itt = chps.iterator();
			Championship chp;
			while (itt.hasNext()) {
				chp = itt.next();
				this.championshipsById.put(chp.getIdentifier(), chp);
			}
		} catch (DAOInstantiationException ex) {
			throw new QueryException(ex);
		}
	}
	
	/**
	 * Initialisation du chargement des données depuis le XML
	 * @throws ToolsException Si problème levé pendant le chargement des données
	 */
	private void loadXMLData()
	throws ToolsException {
		logger.trace("Chargement des données depuis le fichier XML...");
		
		List<Element> teamElements = rootElement.getChildren("team");
		Iterator<Element> itt = teamElements.iterator();
		Element teamElement;
		while (itt.hasNext()) {
			teamElement = itt.next();
			buildTeamWithXML(teamElement);
		}
	}
	
	/**
	 * Création/recherche d'une équipe + Création équipe/saison à partir d'un noeud XML
	 * @param teamElement Element XML représentant l'équipe
	 * @throws ToolsException Si problème levé pendant le chargement des données
	 */
	private void buildTeamWithXML(Element teamElement)
	throws ToolsException {
		String id = teamElement.getAttributeValue("id");
		Team team;
		boolean teamToCreate = false;
		if (id != null) {
			team = this.teamsById.get(Integer.parseInt(id));
			if (team == null) {
				throw new ToolsException("L'identifiant " + id + " ne correspond pas à une équipe");
			}
			this.existingTeams.add(team);
		} else {
			team = new Team();
			teamToCreate = true;
			this.teamsToCreate.add(team);

		}
		
		String name = teamElement.getAttributeValue("name");
		if (Strings.isNullOrEmpty(name))
			throw new ToolsException("Un nom d'équipe est manquant sur un élément");
		
		String gym = teamElement.getAttributeValue("gym"),
			   date = teamElement.getAttributeValue("date");
		Gym gymObj;
		if (Strings.isNullOrEmpty(gym))
			throw new ToolsException("Gymnase non précisé pour l'équipe [" + name + "]");
		else {
			gymObj = this.gymsById.get(Integer.parseInt(gym));
			if (gymObj == null)
				throw new ToolsException("Gymnase inexistant avec l'identifiant [" + gym + "]");
		}
		if (Strings.isNullOrEmpty(date))
			throw new ToolsException("Date de match non précisée pour l'équipe [" + name + "]");
		
		if (teamToCreate && !simulation) {
			try {
				Team newTeam = getDAO(TeamDAO.class).create(team);
				team.setIdentifier(newTeam.getIdentifier());
			} catch (DAOInstantiationException | DAOCrudException | DataValidationException ex) {
				throw new ToolsException(ex);
			}
		}
		
		SeasonTeam seasonTeam = new SeasonTeam();
		seasonTeam.setImage(null);
		seasonTeam.setLabel(name);
		seasonTeam.setGym(gymObj);
		seasonTeam.setDate(LocalDateTime.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
		seasonTeam.setSeason(this.currentSeason);
		seasonTeam.setState(SeasonTeam.State.I);
		seasonTeam.setTeam(team);
		this.seasonTeamsToCreate.add(seasonTeam);
		if (!simulation) {
			try {
				seasonTeam = getDAO(SeasonTeamDAO.class).create(seasonTeam);
			} catch (DAOInstantiationException | DAOCrudException | DataValidationException ex) {
				throw new ToolsException(ex);
			}
		}
		
		String chpNumber = teamElement.getAttributeValue("chp");
		if (chpNumber != null) {
			try {
				Championship chp = this.championshipsById.get(Integer.parseInt(chpNumber));
				if (chp == null)
					throw new ToolsException("Championnat " + chpNumber + " inexistant");
				
				ChampionshipTeam ech = new ChampionshipTeam();
				ech.setChampionship(chp);
				ech.setTeam(seasonTeam);
				ech.setBonus(0);
				ech.setForfeit(0);
				ech.setLoose(0);
				ech.setLoose3By2(0);
				ech.setPlay(0);
				ech.setPoints(0);
				ech.setPointsAgainst(0);
				ech.setPointsFor(0);
				ech.setSetsAgainst(0);
				ech.setSetsFor(0);
				ech.setWin(0);
				this.championshipTeamsToCreate.put(ech.getChampionship().getIdentifier(), ech);
				if (!simulation) {
					getDAO(ChampionshipTeamDAO.class).create(ech);
				}
			} catch (DAOInstantiationException | DAOCrudException | DataValidationException ex) {
				throw new ToolsException(ex);
			}
		}
		
		buildPlayersWithTeamXML(seasonTeam, teamElement);
	}
	
	/**
	 * Création/recherche des joueurs + Création équipe/saison/joueur à partir d'un noeud XML
	 * @param team Equipe concernée pour le joueur
	 * @param teamElement Element XML représentant l'équipe
	 * @throws ToolsException Si problème levé pendant le chargement des données
	 */
	private void buildPlayersWithTeamXML(SeasonTeam team, Element teamElement)
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
	
	/**
	 * Création/recherche d'un joueur + Création équipe/saison/joueur à partir d'un noeud XML
	 * @param team Equipe concernée pour le joueur
	 * @param playerElement Element XML représentant le joueur
	 * @throws ToolsException Si problème levé pendant le chargement des données
	 */
	private boolean buildPlayerWithXML(SeasonTeam team, Element playerElement)
	throws ToolsException {
		boolean managerFlag = false;
		String id = playerElement.getAttributeValue("id");
		User user;
		boolean userToCreate = false;
		if (id != null) {
			user = this.usersById.get(Integer.parseInt(id));
			if (user == null) {
				throw new ToolsException("L'identifiant " + id + " ne correspond pas à un joueur");
			}
			this.existingUsers.add(user);
		} else {
			user = new User();
			this.usersToCreate.add(user);
			userToCreate = true;
			String name = playerElement.getAttributeValue("name"), 
				   firstName = playerElement.getAttributeValue("firstName");
			if (Strings.isNullOrEmpty(name))
				throw new ToolsException("Le nom du joueur doit être renseigné sur l'élément, dans l'équipe [" + team.getLabel() + "]");
			if (Strings.isNullOrEmpty(firstName))
				throw new ToolsException("Le prénom du joueur doit être renseigné sur l'élément, dans l'équipe [" + team.getLabel() + "]");
			user.setName(name);
			user.setFirstName(firstName);
		}
		
		String manager = playerElement.getAttributeValue("manager"),
			   mail = playerElement.getAttributeValue("mail"),
			   tel = playerElement.getAttributeValue("tel"),
			   gender = playerElement.getAttributeValue("gender");

		if (manager != null && manager.equals("true")) {
			if (userToCreate) {
				if (mail == null) 
					throw new ToolsException("Le responsable d'équipe doit avoir un mail! Dans l'équipe [" + team.getLabel() + "]");
			} else {
				mail = mail != null ? mail : user.getMail();
				if (mail == null)
					throw new ToolsException("Le responsable d'équipe doit avoir un mail! Dans l'équipe [" + team.getLabel() + "]");
			}
			managerFlag = true;
		}		
		
		if (mail != null) {
			user.setMail(mail);
		}
		if (userToCreate) {
			if (gender == null) {
				throw new ToolsException("Le genre doit être renseigné pour : " + user.getName() + "/" + user.getFirstName());
			}
			user.setAdministrator(false);
			user.setImage(null);
			user.setPassword("");
		}
		if (tel != null) {
			user.setPhone(tel);
		}
		
		if (!simulation) {
			try {
				if (userToCreate) {
					User newUser = getDAO(UserDAO.class).create(user);
					user.setIdentifier(newUser.getIdentifier());
				} else {
					getDAO(UserDAO.class).update(user);
				}
			} catch (DAOInstantiationException | DAOCrudException | DataValidationException ex) {
				throw new ToolsException(ex);
			}
		}
		
		SeasonTeamPlayer stp = new SeasonTeamPlayer();
		stp.setManager(managerFlag);
		stp.setPlayer(user);
		stp.setTeam(team);
		this.seasonTeamPlayersToCreate.add(stp);
		if (!simulation) {
			try {
				getDAO(SeasonTeamPlayerDAO.class).create(stp);
			} catch (DAOInstantiationException | DAOCrudException | DataValidationException ex) {
				throw new ToolsException(ex);
			}
		}
		return managerFlag;
	}
	
	/**
	 * Affichage du résultat dans le logger
	 * Affiché à chaque fois, même en simulation pour visualiser le résultat éventuel
	 */
	private void printDataToCreate() {
		logger.trace("----------------------------------------");
		logger.trace("Résultat de la création/existence :     ");
		logger.trace("----------------------------------------");
		logger.trace("LISTE DES EQUIPES A CREER (" + this.teamsToCreate.size() + ")");
		logger.trace("LISTE DES EQUIPES EXISTANTES ENREGISTREES (" + this.existingTeams.size() + ")");
		logger.trace("LISTE DES UTILISATEURS A CREER (" + this.usersToCreate.size() + ")");
		this.usersToCreate.forEach(t -> {
			logger.trace("Utilisateur : " + t.getFirstName() + " " + t.getName());
		});
		logger.trace("LISTE DES UTILISATEURS EXISTANTS ENREGISTRES (" + this.existingUsers.size() + ")");
		this.existingUsers.forEach(t -> {
			logger.trace("Utilisateur : " + t.getFirstName() + " " + t.getName());
		});
		logger.trace("LISTE DES EQUIPES/SAISON A CREER (" + this.seasonTeamsToCreate.size() + ")");
		this.seasonTeamsToCreate.forEach(t -> {
			logger.trace("Equipe/Saison : " + t.getLabel() + " / " + t.getGym().getLabel() + " / " + t.getDate().format(DateTimeFormatter.ofPattern("EEEE HH:mm")));
		});
		logger.trace("LISTE DES EQUIPES/SAISON/JOUEUR A CREER (" + this.seasonTeamPlayersToCreate.size() + ")");
		this.seasonTeamPlayersToCreate.forEach(t -> {
			logger.trace("Equipe/Saison/Joueur : " + t.getTeam().getLabel() + " / " + t.getPlayer().getName() + " / " + t.getPlayer().getFirstName());
		});
		logger.trace("LISTE DES EQUIPES/CHAMPIONNATS A CREER (" + this.championshipTeamsToCreate.size() + ")");
		this.championshipTeamsToCreate.entries().forEach(e -> {
			logger.trace("Equipe/Championnat : " + e.getKey() + " | " + e.getValue().getTeam().getLabel());
		});
	}
}
