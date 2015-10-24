package com.jtouzy.cv.tools.model;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

public enum ToolsList {
	DEPLOY("Déploiement de l'application DVT/PROD", "deploy", Arrays.asList(
		new ToolParameter(ParameterNames.WEBAPP, "Déploiement de la webapp", ParameterNames.WEBAPP, false),
		new ToolParameter(ParameterNames.WEBAPI, "Déploiement de l'API (serveur)", ParameterNames.WEBAPI, false)
	)),
	INSTALL("Déploiement de l'application DVT/PROD", "install", Arrays.asList(
		new ToolParameter(ParameterNames.WEBAPP, "Installation de la webapp", ParameterNames.WEBAPP, false),
		new ToolParameter(ParameterNames.WEBAPI, "Installation de l'API (serveur)", ParameterNames.WEBAPI, false)
	)),
	IMPORT_REGISTER("Enregistrement des inscriptions depuis un fichier XML", "impreg", Arrays.asList(
		new ToolParameter(ParameterNames.FILEPATH, "Chemin vers le fichier XML", ParameterNames.FILEPATH, true),
		new ToolParameter(ParameterNames.SIMULATION, "Exécution en simulation?", ParameterNames.SIMULATION, false)
	)),
	DAY_SWITCH("Inversion de 2 journées d'un championnat", "dayswitch", Arrays.asList(
		new ToolParameter(ParameterNames.ID, "ID du championnat", ParameterNames.ID, true),
		new ToolParameter(ParameterNames.FIRSTDAY, "Première journée à échanger", ParameterNames.FIRSTDAY, true),
		new ToolParameter(ParameterNames.SWITCHDAY, "Seconde journée à échanger", ParameterNames.SWITCHDAY, true)
	)),
	CALENDAR_GEN("Génération du calendrier d'un championnat", "calgen", Arrays.asList(
		new ToolParameter(ParameterNames.ID, "ID du championnat", ParameterNames.ID, true),
		new ToolParameter(ParameterNames.RETURN, "Génération des matchs retour?", ParameterNames.RETURN, false),
		new ToolParameter(ParameterNames.SIMULATION, "Exécution en simulation?", ParameterNames.SIMULATION, false)
	)),
	DB_GEN("Génération des tables de la base de données", "dbgen"),
	BACKUP("Récupération des données de l'ancienne base MySQL", "backup", Arrays.asList(
		new ToolParameter(ParameterNames.FILEPATH, "Chemin vers le fichier XML", ParameterNames.FILEPATH, true)
	)),
	PWD_GEN("Génération d'un mot de passe pour un utilisateur", "pwdgen", Arrays.asList(
		new ToolParameter(ParameterNames.ID, "ID de l'utilisateur", ParameterNames.ID, true)
	)),
	ESJ_GEN("Enregistrement d'un utilisateur pour une équipe/saison", "esjgen", Arrays.asList(
		new ToolParameter(ParameterNames.ID, "ID de l'équipe", ParameterNames.ID, true),
		new ToolParameter(ParameterNames.IDU, "ID de l'utilisateur", ParameterNames.IDU, true),
		new ToolParameter(ParameterNames.NAME, "Nom de l'utilisateur", ParameterNames.NAME, true),
		new ToolParameter(ParameterNames.FIRSTNAME, "Prénom de l'utilisateur", ParameterNames.FIRSTNAME, true),
		new ToolParameter(ParameterNames.TEL, "Téléphone de l'utilisateur", ParameterNames.TEL, true),
		new ToolParameter(ParameterNames.MAIL, "E-mail de l'utilisateur", ParameterNames.MAIL, true),
		new ToolParameter(ParameterNames.GENDER, "Genre de l'utilisateur", ParameterNames.GENDER, true)
	)),
	UPD_TEAM_INFOS("Mise à jour informations d'équipe", "updinf", Arrays.asList(
		new ToolParameter(ParameterNames.ID, "ID de l'équipe", ParameterNames.ID, true),
		new ToolParameter(ParameterNames.DATE, "Nouvelle date de match", ParameterNames.DATE, true),
		new ToolParameter(ParameterNames.GYM, "Nouveau gymnase de match", ParameterNames.GYM, true)
	));
	
	private List<ToolParameter> parameters;
	private String description;
	private String commandLineToolName;
	
	private ToolsList(String description, String commandLineToolName) {
		this(description, commandLineToolName, null);
	}
	
	private ToolsList(String description, String commandLineToolName, List<ToolParameter> params) {
		this.parameters = params;
		this.description = description;
		this.commandLineToolName = commandLineToolName;
	}
	
	public boolean hasParameters() {
		return this.parameters != null && !this.parameters.isEmpty();
	}

	public List<ToolParameter> getParameters() {
		return parameters;
	}
	
	public String getDescription() {
		return description;
	}

	public String getCommandLineToolName() {
		return commandLineToolName;
	}

	public static ToolsList findByName(String toolName) {
		return Iterables.find(Arrays.asList(values()), new Predicate<ToolsList>() {
			@Override public boolean apply(ToolsList input) {
				return input.getCommandLineToolName().equals(toolName);
			}
		});
	}
}
