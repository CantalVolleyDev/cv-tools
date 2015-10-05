package com.jtouzy.cv.tools.executors.backup;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;

import com.google.common.base.CaseFormat;
import com.google.common.base.Splitter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import com.jtouzy.cv.model.classes.ChampionshipWeeks;
import com.jtouzy.cv.model.classes.Comment;
import com.jtouzy.cv.model.classes.Match;
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
import com.jtouzy.cv.tools.errors.ToolsException;
import com.jtouzy.cv.tools.executors.dbgen.DBGenerateTool;
import com.jtouzy.cv.tools.model.ParameterNames;
import com.jtouzy.cv.tools.model.ToolExecutorImpl;
import com.jtouzy.dao.DAO;
import com.jtouzy.dao.DAOManager;
import com.jtouzy.dao.db.DBType;
import com.jtouzy.dao.model.ColumnContext;
import com.jtouzy.dao.model.ModelContext;
import com.jtouzy.dao.model.TableContext;
import com.jtouzy.dao.reflect.ObjectUtils;

public class XmlBackUtils extends ToolExecutorImpl {
	private Multimap<TableContext, Object> values;
	private static final List<String> tableList = Lists.newArrayList(
		"usr", "sai", "cmp", "chp", "mat", "eqi", "eqs", "esj", "pma", "gym", "cmt", "ech", "wsem"
	);
	private static final List<String> excludeColumns = Lists.newArrayList(
		"nomeqi",
		"ufbcmp",
		"notcmt",
		"etaeqi",
		"gkeusr",
		"grpech", "libech",
		"cmpesj", "saiesj",
		"numsem"
	);
	private Map<String, Integer> objectsSummary = new LinkedHashMap<>();
	private Map<String, Integer> dataSummary = new LinkedHashMap<>();
	private Table<TableContext, Integer, Integer> equivalences = HashBasedTable.create();
	private Map<Integer, String> oldTeamsLabels = new LinkedHashMap<>();
	
	public XmlBackUtils() {
	}
	
	@Override
	public void execute() {
		try {
			if (!hasParameter(ParameterNames.FILEPATH))
				throw new ToolsException("Le chemin du fichier de backup n'est pas renseigné");
			initializeContext();
			new DBGenerateTool().execute();
			load();
			trtData(); 
			createData();
		} catch (Exception ex) {
			throw new ToolsException(ex);
		}
	}
	
	public void load()
	throws Exception {
		Document doc = getBackDocument();
		values = ArrayListMultimap.create();
		loadDatabase(doc.getRootElement().getChild("database"));
		System.out.println(objectsSummary);
	}
	
	private Document getBackDocument()
	throws Exception {
		SAXBuilder builder = new SAXBuilder();
		File xmlFile = new File(getParameterValue(ParameterNames.FILEPATH));
		Document document = (Document) builder.build(xmlFile);
		return document;
	}
	
	private void loadDatabase(Element databaseElement)
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
	
	private Element getTableElement(String tableName, Element databaseElement) {
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
	
	private void loadTable(Element tableElement)
	throws Exception {
		TableContext tableContext = ModelContext.getTableContext(tableElement.getAttributeValue("name"));
		System.out.println("Chargement des données pour la table " + tableContext.getName() + "...");
		List<Element> rows = tableElement.getChildren("row");
		Iterator<Element> it = rows.iterator();
		while (it.hasNext()) 
			loadRowForTable(tableContext, it.next());
		objectsSummary.put(tableContext.getName(), rows.size());
	}
	
	private void loadRowForTable(TableContext tableContext, Element rowElement)
	throws Exception {
		Object instance = ObjectUtils.newObject(tableContext.getTableClass());
		Iterator<Element> it = rowElement.getChildren("field").iterator();
		Element field;
		ColumnContext columnContext;
		Object value = null;
		String name = null;
		String valueStr = null;
		Integer numeqi = null;
		while (it.hasNext()) {
			field = it.next();
			name = field.getAttributeValue("name");
			value = field.getValue();
			if (name.equals("numeqi")) {
				numeqi = Integer.parseInt(String.valueOf(value));
			}
			if (excludeColumns.contains(name))
			{
				if (name.equals("nomeqi")) {
					oldTeamsLabels.put(numeqi, String.valueOf(value));
				}
				continue;
			}
			
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
			ObjectUtils.setValue(instance, columnContext, value);
		}
		values.put(tableContext, instance);
	}
	
	private void trtData()
	throws Exception {
		Iterator<TableContext> it = values.keySet().iterator();
		Iterator<Object> itObj = null;
		TableContext tableContext;
		
		List<String> male = Arrays.asList("Fabrice", "Pierre", "Pierré", "Abdelkrim", "Thierry",
				"Christophe", "Mickael", "Rémy", "Alexandre", "Frederic", "Stéphane", "Jean marie",
				"Cédric", "Baptiste", "Bruno", "Cyril", "Laurent", "Matthieu", "Christian", "William",
				"Sébastien", "Guillaume", "Philippe", "Gael", "François", "Iziadin", "Frédéric",
				"Benoit", "Vincent", "Justin", "Jérémy", "Jeremy", "Eric", "Nicolas", "Fabien", "Romain",
				"Joao", "Arnaud", "Antonio", "Anthony", "Izmir", "Jean christophe", "Jean françois",
				"Sylvain", "Yannick", "David", "Olivier", "Kujtim", "Patrick", "Erwan", "Firmin", "Emilien",
				"Benjamin", "Gilles", "Jean-philippe", "Michel", "Ioan", "Julien");
		List<String> female = Arrays.asList("Fannie", "Christine", "Claire", "Julie", "Camille", "Floriane",
				"Bérengère", "Géraldine", "Sarah", "Audrey", "Laura", "Irmina", "Cécile", "Sandrine", "Maeva",
				"Aurélie", "Stéphanie", "Magali", "Linda", "Marine", "Valérie", "Sabrina", "Catherine", "Anais",
				"Lise", "Nathalie", "Manon", "Emilie", "Amélie", "Caroline", "Nadège", "Karen", "Beatrice", "Céline",
				"Laurence", "Valerie", "Léa", "Melanie", "Coralie");
		
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
					st.setImage(null);
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
			// ----------------------------------------------
			// Traitement pour les users : Majuscules et minuscules
			// ----------------------------------------------
			else if (tableContext.getName().equals("usr")) {
				itObj = values.get(tableContext).iterator();
				User usr = null;
				while (itObj.hasNext()) {
					usr = (User)itObj.next();
					usr.setImage(null);
					usr.setName(usr.getName().toUpperCase());
					if (usr.getName().equals("TOUZY")) {
						usr.setImage("jpeg");
					}
					usr.setFirstName(
							CaseFormat.LOWER_UNDERSCORE.to(
									CaseFormat.UPPER_CAMEL, 
										usr.getFirstName().toLowerCase()));
					if (male.contains(usr.getFirstName())) {
						usr.setGender(User.Gender.M);
					} else if (female.contains(usr.getFirstName()))
						usr.setGender(User.Gender.F);
					else throw new IllegalArgumentException("Sexe non trouvé pour : " + usr.getFirstName());
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
	private <D extends DAO<T>,T> void createData()
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
	private <D extends DAO<T>,T> void createFor(TableContext tableContext, Class<D> clazz)
	throws Exception {
		D dao = DAOManager.getDAO(this.connection, clazz);
		List<T> objects = (List<T>)Lists.newArrayList(values.get(tableContext));
		List<Integer> oldValues = new ArrayList<>();
		List<Integer> oldTeamIds = new ArrayList<>();
		Iterator<T> it = objects.iterator();
		Iterator<ColumnContext> itc;
		Object object = null;
		ColumnContext columnContext;
		ColumnContext autoGeneratedColumnContext = tableContext.getAutoGeneratedField();
		boolean isAutoGenerated = autoGeneratedColumnContext != null;
		Integer oldValue = null, value = null;
		while (it.hasNext()) {
			object = it.next();
			if (isAutoGenerated) {
				Object oldValueObject = ObjectUtils.getValue(object, autoGeneratedColumnContext);
				if (oldValueObject != null) {
					oldValues.add(Integer.parseInt(String.valueOf(oldValueObject)));
				} else {
					oldValues.add(0);
				}
			}
			if (object instanceof SeasonTeam) {
				SeasonTeam st = ((SeasonTeam)object);
				st.setLabel(oldTeamsLabels.get(st.getTeam().getIdentifier()));
				st.setPlayersNumber(4);
				if (st.getLabel().contains("FOX")) {
					st.setImage("jpg");
				} else if (st.getLabel().contains("Raptor")) {
					st.setImage("png");
				}
				oldTeamIds.add(st.getTeam().getIdentifier());
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
				} else if (object instanceof Comment && columnContext.getName().equals("valcmt")) {
					Map<Integer,Integer> equivalencesId = equivalences.row(ModelContext.getTableContext(Match.class));
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
				if (object instanceof SeasonTeam) {
					equivalences.put(tableContext, oldTeamIds.get(newObjects.indexOf(object)), ((SeasonTeam)object).getIdentifier());
				} else {
					equivalences.put(tableContext, oldValue, Integer.parseInt(String.valueOf(ObjectUtils.getValue(object, autoGeneratedColumnContext))));
				}
			}
		}
		dataSummary.put(tableContext.getName(), newObjects.size());
	}
}