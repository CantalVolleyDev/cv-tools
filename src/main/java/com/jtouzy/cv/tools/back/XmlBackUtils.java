package com.jtouzy.cv.tools.back;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;

import com.google.common.base.Splitter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import com.jtouzy.cv.model.classes.ChampionshipWeeks;
import com.jtouzy.cv.model.classes.Comment;
import com.jtouzy.cv.model.classes.News;
import com.jtouzy.cv.model.classes.SeasonTeam;
import com.jtouzy.cv.model.classes.User;
import com.jtouzy.cv.model.dao.ChampionshipDAO;
import com.jtouzy.cv.model.dao.ChampionshipTeamDAO;
import com.jtouzy.cv.model.dao.ChampionshipWeeksDAO;
import com.jtouzy.cv.model.dao.CommentDAO;
import com.jtouzy.cv.model.dao.CompetitionDAO;
import com.jtouzy.cv.model.dao.GymDAO;
import com.jtouzy.cv.model.dao.MatchDAO;
import com.jtouzy.cv.model.dao.MatchPlayerDAO;
import com.jtouzy.cv.model.dao.NewsDAO;
import com.jtouzy.cv.model.dao.SeasonDAO;
import com.jtouzy.cv.model.dao.SeasonTeamDAO;
import com.jtouzy.cv.model.dao.SeasonTeamPlayerDAO;
import com.jtouzy.cv.model.dao.TeamDAO;
import com.jtouzy.cv.model.dao.UserDAO;
import com.jtouzy.cv.tools.generate.DBGenerateTool;
import com.jtouzy.dao.DAO;
import com.jtouzy.dao.DAOManager;
import com.jtouzy.dao.db.DBType;
import com.jtouzy.dao.model.ColumnContext;
import com.jtouzy.dao.model.ModelContext;
import com.jtouzy.dao.model.TableContext;
import com.jtouzy.dao.reflect.ObjectUtils;
import com.jtouzy.utils.resources.ResourceUtils;

public class XmlBackUtils {

	// FONCTIONS A RAJOUTER DANS L'OUTIL DE BACKUP DE L'ANCIENNE BASE DE DONNEES :
	// - Création de nouveaux ID et correspondances avec les anciens pour recopie dans les tables
	// - Rajouter les contraintes externes (DBGenerateTool) pour les clés étrangères
	// - Faire un commun pour créer les tables dans l'ordre et avoir les DAO associés
	// - Pour les dates des commentaires, faire une méthode particulière pour date de match + 1 jour
	
	private static Connection connection;
	private static Multimap<TableContext, Object> values;
	private static final List<String> tableList = Lists.newArrayList(
		"usr", "sai", "cmp", "chp", "mat", "eqi", "eqs", "esj", "pma", "gym", "cmt", "ech", "wsem"
	);
	private static final List<String> excludeColumns = Lists.newArrayList(
		"ufbcmp",
		"eqicmt", "notcmt",
		"etaeqi",
		"gkeusr",
		"grpech", "libech",
		"libeqs",
		"cmpesj"
	);
	private static final Map<String, Integer> objectsSummary = new LinkedHashMap<>();
	private static final Map<String, Integer> dataSummary = new LinkedHashMap<>();
	private static final Table<TableContext, Integer, Integer> equivalences = HashBasedTable.create();
	
	public static void main(String[] args)
	throws Exception {
		// Initialisations du fichier properties
		Properties properties = ResourceUtils.readProperties("tools");
		// Initialisation des classes modèles
		DAOManager.init("com.jtouzy.cv.model.classes");
		// Initialisation de la connexion
		connection = DriverManager.getConnection(properties.getProperty("db.jdbcUrl") + 
				                                 "/" + properties.getProperty("db.databaseName"),
				                                 properties.getProperty("db.admin.user"),
				                                 properties.getProperty("db.admin.password"));
		// Génération des tables
		DBGenerateTool.main(null);
		// Chargement des données depuis le dump dans des objets modèle
		load();
		// Traitement éventuel des données pour modifications
		trtData();
		// Création en base des objets modèle 
		createData();
	}
	
	public static void load()
	throws Exception {
		//xmlValues = new HashMap<String, Map<String,Object>>();
		Document doc = getBackDocument();
		values = ArrayListMultimap.create();
		loadDatabase(doc.getRootElement().getChild("database"));
		System.out.println(objectsSummary);
	}
	
	private static Document getBackDocument()
	throws Exception {
		SAXBuilder builder = new SAXBuilder();
		File xmlFile = new File("dbdump.xml");
		Document document = (Document) builder.build(xmlFile);
		return document;
	}
	
	private static void loadDatabase(Element databaseElement)
	throws Exception {
		Iterator<String> it = tableList.iterator();
		String tableName;
		Element tableElement;
		while (it.hasNext()) {
			tableName = it.next();
			tableElement = getTableElement(tableName, databaseElement);
			if (tableElement == null) {
				System.out.println("Aucune donnée à charger pour la table " + tableName);
				continue;
			}
			loadTable(tableElement);
		}
	}
	
	private static Element getTableElement(String tableName, Element databaseElement) {
		Iterator<Element> it = databaseElement.getChildren("table_data").iterator();
		Element tableElement;
		while (it.hasNext()) {
			tableElement = it.next();
			if (tableElement.getAttributeValue("name").equals(tableName)) {
				return tableElement;
			}
		}
		return null;
	}
	
	private static void loadTable(Element tableElement)
	throws Exception {
		TableContext tableContext = ModelContext.getTableContext(tableElement.getAttributeValue("name"));
		System.out.println("Chargement des données pour la table " + tableContext.getName() + "...");
		List<Element> rows = tableElement.getChildren("row");
		Iterator<Element> it = rows.iterator();
		while (it.hasNext()) 
			loadRowForTable(tableContext, it.next());
		objectsSummary.put(tableContext.getName(), rows.size());
	}
	
	private static void loadRowForTable(TableContext tableContext, Element rowElement)
	throws Exception {
		Object instance = ObjectUtils.newObject(tableContext.getTableClass());
		Iterator<Element> it = rowElement.getChildren("field").iterator();
		Element field;
		ColumnContext columnContext;
		Object value = null;
		String name = null;
		String valueStr = null;
		while (it.hasNext()) {
			field = it.next();
			name = field.getAttributeValue("name");
			value = field.getValue();
			if (excludeColumns.contains(name))
				continue;
			
			if (name.equals("hreeqs") || name.equals("jrneqs")) {
				String inf = ((SeasonTeam)instance).getInformation(); 
				((SeasonTeam)instance).setInformation(inf == null ? (String)value : (inf + "_" + value));
				continue;
			}
			
			columnContext = tableContext.getColumnContext(name);
			if (String.valueOf(value).length() != 0) {
				switch (columnContext.getType()) {
					case BOOLEAN:
						if (value.equals("O") || value.equals("N")) {
							value = value.equals("O");
						}
						break;
					case VARCHAR:
						valueStr = String.valueOf(value);
						valueStr = valueStr.replace("&eacute;", "é");
						valueStr = valueStr.replace("Ã©", "é");
						valueStr = valueStr.replace("&egrave;", "è");
						valueStr = valueStr.replace("Ã¨", "è");
						valueStr = valueStr.replace("&ccedil;", "ç");
						valueStr = valueStr.replace("Ã§", "ç");
						valueStr = valueStr.replace("&acirc;", "â");
						valueStr = valueStr.replace("&ecirc;", "ê");
						valueStr = valueStr.replace("&nbsp;", " ");
						valueStr = valueStr.replace("&agrave;", "à");
						valueStr = valueStr.replace("&ocirc;", "ô");
						value = valueStr;
						break;
					case ENUM:
					case INTEGER:
					case DATE:
					case DATETIME:
					default:
						break;
				}
			} else {
				value = null;
				if (columnContext.getType() == DBType.BOOLEAN)
					value = false;
			}
			
			if (columnContext.isRelationColumn()) {
				Class<?> relationClass = columnContext.getFieldContext().getField().getType();
				TableContext tableCtx = ModelContext.getTableContext(relationClass);
				Map<Integer,Integer> equivalencesId = equivalences.row(tableCtx);
				if (equivalencesId != null && equivalencesId.size() > 0) {
					value = equivalencesId.get(value);
				}
			}
			
			ObjectUtils.setValue(instance, columnContext, value);
		}
		values.put(tableContext, instance);
	}
	
	private static void trtData()
	throws Exception {
		Iterator<TableContext> it = values.keySet().iterator();
		Iterator<Object> itObj = null;
		TableContext tableContext;
		while (it.hasNext()) {
			tableContext = it.next();
			// ----------------------------------------------
			// Traitement pour les commentaires : Modif des dates
			// ----------------------------------------------
			if (tableContext.getName().equals("cmt")) {
				itObj = values.get(tableContext).iterator();
				Comment comment = null;
				while (itObj.hasNext()) {
					comment = (Comment)itObj.next();
					// Date temporaire : Aller chercher la date du match
					comment.setDate(LocalDateTime.of(2014, 01, 01, 00, 00));
				}
			}
			// ----------------------------------------------
			// Traitement pour les équipes/saison : Modif des dates
			// ----------------------------------------------
			else if (tableContext.getName().equals("eqs")) {
				itObj = values.get(tableContext).iterator();
				SeasonTeam st = null;
				while (itObj.hasNext()) {
					st = (SeasonTeam)itObj.next();
					List<String> infos = Splitter.on("_")
							                     .splitToList(st.getInformation());
					String day = infos.get(0);
					String hour = infos.get(1).substring(0, 2);
					String min = infos.get(1).substring(2, 4);
					LocalDateTime realDate = LocalDateTime.of(2015, 2, 1, 
							                                  Integer.parseInt(hour), 
							                                  Integer.parseInt(min));
					int amount = 0;
					switch (day) {
						case "LU": amount = 1; break;
						case "MA": amount = 2; break;
						case "ME": amount = 3; break;
						case "JE": amount = 4; break;
						case "VE": amount = 5; break;
						case "SA": amount = 6; break;
						case "DI": amount = 0; break;
					}
					st.setDate(realDate.plusDays(amount));
				}
			}
			// ----------------------------------------------
			// Traitement pour les semaines/championnats : Modif des dates
			// ----------------------------------------------
			else if (tableContext.getName().equals("wsem")) {
				itObj = values.get(tableContext).iterator();
				ChampionshipWeeks chw = null;
				while (itObj.hasNext()) {
					chw = (ChampionshipWeeks)itObj.next();
					chw.setWeekDate(chw.getWeekDate().plusDays(1));
				}
			}
		}
		
		// ----------------------------------------------
		// Création de la news unique pour le site web
		// ----------------------------------------------
		News n1 = new News();
		User usr = new User();
		usr.setIdentifier(1);
		n1.setIdentifier(1);
		n1.setAuthor(usr);
		n1.setContent("Cantalvolley.fr évolue! Toutes les semaines de nouvelles évolutions seront ajoutées au site.");
		n1.setCreationDate(LocalDateTime.of(2015, 8, 27, 19, 00));
		n1.setState(News.State.V);
		n1.setTitle("Peau neuve");
		n1.setCategory("Développement");
		n1.setPublishDate(LocalDateTime.of(2015, 9, 4, 22, 00));
		values.put(ModelContext.getTableContext("nws"), n1);
		
		n1 = new News();
		usr = new User();
		usr.setIdentifier(1);
		n1.setIdentifier(1);
		n1.setAuthor(usr);
		n1.setContent("Aurillac Volley Ball : Les entraînements ont repris depuis le 2 septembre au gymnase des Camisières. Tous les lundis avant la reprise du championnat 4x4, des mini-tournois seront organisés pour préparer les équipes.");
		n1.setCreationDate(LocalDateTime.of(2015, 9, 4, 20, 00));
		n1.setState(News.State.V);
		n1.setTitle("C'est la reprise");
		n1.setCategory("Informations club");
		n1.setPublishDate(LocalDateTime.of(2015, 9, 4, 22, 10));
		values.put(ModelContext.getTableContext("nws"), n1);
		
		n1 = new News();
		usr = new User();
		usr.setIdentifier(1);
		n1.setIdentifier(1);
		n1.setAuthor(usr);
		n1.setContent("Aurillac Volley Ball : L'assemblée générale du club aura lieu le 22 septembre 2015, au Parc Hélitas à 20h00.");
		n1.setCreationDate(LocalDateTime.of(2015, 9, 4, 20, 15));
		n1.setState(News.State.V);
		n1.setTitle("Assemblée générale");
		n1.setCategory("Informations club");
		n1.setPublishDate(LocalDateTime.of(2015, 9, 4, 22, 15));
		values.put(ModelContext.getTableContext("nws"), n1);
	}
	
	@SuppressWarnings("unchecked")
	private static <D extends DAO<T>,T> void createData()
	throws Exception {
		Map<String,Class<D>> daoClasses = new LinkedHashMap<>();
		daoClasses.put("usr", (Class<D>)UserDAO.class);
		daoClasses.put("eqi", (Class<D>)TeamDAO.class);
		daoClasses.put("gym", (Class<D>)GymDAO.class);
		daoClasses.put("sai", (Class<D>)SeasonDAO.class);
		daoClasses.put("eqs", (Class<D>)SeasonTeamDAO.class);
		daoClasses.put("esj", (Class<D>)SeasonTeamPlayerDAO.class);
		daoClasses.put("cmp", (Class<D>)CompetitionDAO.class);
		daoClasses.put("chp", (Class<D>)ChampionshipDAO.class);
		daoClasses.put("mat", (Class<D>)MatchDAO.class);
		daoClasses.put("pma", (Class<D>)MatchPlayerDAO.class); // Temporaire
		daoClasses.put("cmt", (Class<D>)CommentDAO.class);
		daoClasses.put("nws", (Class<D>)NewsDAO.class);
		daoClasses.put("wsem", (Class<D>)ChampionshipWeeksDAO.class);
		daoClasses.put("ech", (Class<D>)ChampionshipTeamDAO.class);
		
		try {
			TableContext context = null;
			Map.Entry<String, Class<D>> entry;
			Iterator<Map.Entry<String, Class<D>>> it = daoClasses.entrySet().iterator();
			while (it.hasNext()) {
				entry = it.next();
				System.out.println("Création des données de la table " + entry.getKey() + "...");
				context = ModelContext.getTableContext(entry.getKey());
				createFor(context, entry.getValue());
			}
		} finally {
			System.out.println(dataSummary);
		}
	}
	
	@SuppressWarnings("unchecked")
	private static <D extends DAO<T>,T> void createFor(TableContext tableContext, Class<D> clazz)
	throws Exception {
		D dao = DAOManager.getDAO(connection, clazz);
		List<T> objects = (List<T>)Lists.newArrayList(values.get(tableContext));
		List<Integer> oldValues = new ArrayList<>();
		Iterator<T> it = objects.iterator();
		Iterator<ColumnContext> itc;
		Object object;
		ColumnContext columnContext;
		ColumnContext autoGeneratedColumnContext = tableContext.getAutoGeneratedField();
		boolean isAutoGenerated = autoGeneratedColumnContext != null;
		Integer oldValue = null, value = null;
		while (it.hasNext()) {
			object = it.next();
			if (isAutoGenerated) {
				oldValues.add(Integer.parseInt(String.valueOf(ObjectUtils.getValue(object, autoGeneratedColumnContext))));
			}
			itc = tableContext.getColumns().iterator();
			while (itc.hasNext()) {
				columnContext = itc.next();
				if (columnContext.isRelationColumn()) {
					Class<?> relationClass = columnContext.getFieldContext().getField().getType();
					TableContext tableCtx = ModelContext.getTableContext(relationClass);
					Map<Integer,Integer> equivalencesId = equivalences.row(tableCtx);
					if (equivalencesId != null && equivalencesId.size() > 0) {
						value = equivalencesId.get(ObjectUtils.getValue(object, columnContext));
						ObjectUtils.setValue(object, columnContext, value);
					}
				}
			}
		}
		
		List<T> newObjects = dao.create(objects);
		if (isAutoGenerated) {
			it = newObjects.iterator();
			while (it.hasNext()) {
				object = it.next();
				oldValue = oldValues.get(newObjects.indexOf(object));
				equivalences.put(tableContext, oldValue, Integer.parseInt(String.valueOf(ObjectUtils.getValue(object, autoGeneratedColumnContext))));
			}
		}
		dataSummary.put(tableContext.getName(), newObjects.size());
	}
}
