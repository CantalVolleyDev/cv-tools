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
	
	public ToolExecutorImpl(Connection connection) {
		this.connection = connection;
		this.parameters = new HashMap<>();
	}
	
	public ToolExecutorImpl() {
		this(null);
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
				connection = 
						DriverManager.getConnection(PropertiesReader.getJDBCUrl(),
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
