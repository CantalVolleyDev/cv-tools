package com.jtouzy.cv.tools;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.jtouzy.cv.tools.errors.ToolsException;
import com.jtouzy.dao.DAO;
import com.jtouzy.dao.DAOManager;
import com.jtouzy.dao.errors.DAOInstantiationException;
import com.jtouzy.dao.errors.model.ModelClassDefinitionException;
import com.jtouzy.utils.resources.ResourceUtils;

public abstract class AbstractTool implements ToolExecutor {
	private CommandLine commandLine;
	protected Connection connection;
	private static final Logger logger = LogManager.getLogger(AbstractTool.class);
	
	public AbstractTool(CommandLine commandLine) {
		this.commandLine = commandLine;
	}
	
	@Override
	public CommandLine getCommandLine() {
		return this.commandLine;
	}
	
	protected void initializeContext()
	throws SQLException, ModelClassDefinitionException, IOException, ToolsException {
		logger.trace("Initialisation du contexte...");
		Properties properties = ResourceUtils.readProperties("tools");
		DAOManager.init("com.jtouzy.cv.model.classes");
		connection = DriverManager.getConnection(new StringBuilder().append(properties.getProperty("db.jdbcUrl"))
                												    .append("/")
        												    		.append(properties.getProperty("db.databaseName"))
        												    		.toString(),
									    		 properties.getProperty("db.admin.user"),
									    		 properties.getProperty("db.admin.password"));
	}
	
	/**
	 * Récupération d'un DAO par sa classe
	 * @param daoClass Classe du DAO demandée
	 * @return Instance du DAO
	 * @throws DAOInstantiationException Si problème dans l'instanciation du DAO
	 */
	protected <D extends DAO<T>,T> D getDAO(Class<D> daoClass)
	throws DAOInstantiationException {
		return DAOManager.getDAO(this.connection, daoClass);
	}
}
