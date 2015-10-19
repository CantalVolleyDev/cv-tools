package com.jtouzy.cv.tools.model;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.jtouzy.cv.config.PropertiesNames;
import com.jtouzy.cv.config.PropertiesReader;
import com.jtouzy.cv.tools.errors.ToolsException;
import com.jtouzy.dao.DAO;
import com.jtouzy.dao.DAOManager;
import com.jtouzy.dao.errors.DAOInstantiationException;
import com.jtouzy.dao.errors.model.ModelClassDefinitionException;

public abstract class ToolExecutorImpl implements ToolExecutor {
	protected Connection connection;
	private Map<String, String> parameters;
	private static final Logger logger = LogManager.getLogger(ToolExecutorImpl.class);
	private static final String DB_DVT_DATABASE = "db.database.dvt";
	private static final String DB_PROD_DATABASE = "db.database.prod";
	
	public ToolExecutorImpl(Connection connection) {
		this.connection = connection;
		this.parameters = new HashMap<>();
	}
	
	public ToolExecutorImpl() {
		this(null);
	}
	
	@Override
	public void preControl() {
		if (this.connection == null) {
			if (!this.hasParameter(ParameterNames.DVT) &&
				!this.hasParameter(ParameterNames.PROD)) {
				throw new ToolsException("Le paramètre DVT ou PROD doit être obligatoirement présent");
			}
			if (this.hasParameter(ParameterNames.DVT) &&
				this.hasParameter(ParameterNames.PROD)) {
				throw new ToolsException("Les paramètres DVT et PROD ne doivent pas être présents ensemble");
			}
		}
	}
	
	@Override
	public void setConnection(Connection connection) {
		this.connection = connection;
	}
	
	@Override
	public void registerParameters(Map<String,String> parameters) {
		this.parameters.putAll(parameters);
	}
	
	@Override
	public void registerParameter(String parameterName, String parameterValue) {
		this.parameters.put(parameterName, parameterValue);
	}
	
	@Override
	public boolean hasParameter(String parameterName) {
		return this.parameters.containsKey(parameterName);
	}

	@Override
	public String getParameterValue(String parameterName) {
		return this.parameters.get(parameterName);
	}
	
	protected void initializeContext() {
		logger.trace("Initialisation du contexte...");
		try {
			initializeProperties();
			DAOManager.init("com.jtouzy.cv.model.classes");
			if (connection == null) {
				String databaseName = null;
				if (hasParameter(ParameterNames.DVT))
					databaseName = PropertiesReader.getProperty(DB_DVT_DATABASE);
				else if (hasParameter(ParameterNames.PROD))
					databaseName = PropertiesReader.getProperty(DB_PROD_DATABASE);
				if (databaseName == null)
					throw new ToolsException("Impossible d'établir une connexion : La base de données n'est pas précisée");
				connection = 
						DriverManager.getConnection(PropertiesReader.getJDBCUrlWithDatabase(databaseName),
										    		PropertiesReader.getProperty(PropertiesNames.DB_ADMIN_USER_PROPERTY),
										    		PropertiesReader.getProperty(PropertiesNames.DB_ADMIN_PASSWORD_PROPERTY));
			}
		} catch (SQLException | ModelClassDefinitionException ex) {
			throw new ToolsException(ex);
		}
	}
	
	protected void initializeProperties() {
		try {
			PropertiesReader.init("tools");
		} catch (IOException ex) {
			throw new ToolsException(ex);
		}
	}

	protected <D extends DAO<T>,T> D getDAO(Class<D> daoClass)
	throws DAOInstantiationException {
		return DAOManager.getDAO(this.connection, daoClass);
	}
}
