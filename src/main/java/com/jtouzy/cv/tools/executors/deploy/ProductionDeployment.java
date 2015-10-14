package com.jtouzy.cv.tools.executors.deploy;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;

import com.jtouzy.cv.config.PropertiesNames;
import com.jtouzy.cv.config.PropertiesReader;
import com.jtouzy.cv.tools.errors.ToolsException;
import com.jtouzy.cv.tools.model.ParameterNames;
import com.jtouzy.cv.tools.model.ToolExecutorImpl;
import com.jtouzy.utils.ftp.FTPCli;
import com.jtouzy.utils.ssh.SSHCli;

public class ProductionDeployment extends ToolExecutorImpl {
	private static Logger logger = LogManager.getLogger(ProductionDeployment.class);
	private static final String SSH_USER = "ssh.user";
	private static final String SSH_PASSWORD = "ssh.password";
	private static final String SSH_HOST = "ssh.host";
	private static final String WEBAPI_FTP_USER = "webapi.ftp.user";
	private static final String WEBAPI_FTP_PASSWORD = "webapi.ftp.password";
	private static final String DAO_PROJECT_PATH = "project.dao";
	private static final String MODEL_PROJECT_PATH = "project.model";
	private static final String TOOL_PROJECT_PATH = "project.tools";
	private static final String API_PROJECT_PATH = "project.api";
	private static final String UTILS_PROJECT_PATH = "project.utils";
	private static final String WEBAPP_PROJECT_PATH = "project.app";
	private static final String WEBAPI_UPLOAD_PATH = "webapi.upload";
	private static final String WEBAPP_UPLOAD_PATH = "webapp.upload";
	private static final String WEBAPP_UPLOAD_DVT_PATH = "webapp.upload.dvt";
	private static final String MAVEN_HOME = "maven.home";
	
	public ProductionDeployment() {
	}

	@Override
	public void execute() {
		try {
			initializeProperties();
			if (!hasParameter(ParameterNames.DVT) && !hasParameter(ParameterNames.PROD))
				throw new ToolsException("Précisez le niveau de déploiement (DVT/PROD)");
			if (hasParameter(ParameterNames.DVT) && hasParameter(ParameterNames.PROD))
				throw new ToolsException("Un seul niveau doit être précisé (DVT/PROD)");
			
			if (hasParameter(ParameterNames.WEBAPI)) {
				deployWebAPI();
			}
			if (hasParameter(ParameterNames.WEBAPP)) {
				deployWebApp();
			}
		} catch (IOException ex) {
			throw new ToolsException(ex);
		}
	}
	
	private String getProperty(String propertyName) {
		return PropertiesReader.getProperty(propertyName);
	}
	
	private void deployWebAPI()
	throws IOException {
		buildLocalProjects();
		uploadWebAPIProject();
		tomcatDeployAndSave();
		fireFirstUrl();
	}
	
	private void buildLocalProjects()
	throws IOException {
		launchMaven(UTILS_PROJECT_PATH, DAO_PROJECT_PATH, MODEL_PROJECT_PATH, TOOL_PROJECT_PATH, API_PROJECT_PATH);
	}
	
	private void uploadWebAPIProject()
	throws IOException {
		logger.trace("Téléchargement des fichiers sur le serveur...");
		FTPCli.connect("5.135.146.110", getProperty(WEBAPI_FTP_USER), getProperty(WEBAPI_FTP_PASSWORD))
		      .removeDirectory("/jto/temp/cvapi", false)
		      .uploadDirectory(getWebAPIUploadPath(), "/jto/temp/cvapi")
		      .execute();
		logger.trace("Téléchargement terminé.");
	}
	
	private String getWebAPIUploadPath() {
		return getProperty(API_PROJECT_PATH) + getProperty(WEBAPI_UPLOAD_PATH);
	}
	
	private void tomcatDeployAndSave()
	throws IOException {
		logger.trace("Exécution des commandes sur le serveur...");
		String commandFile = "tomcat-deploy-prod.commands";
		if (hasParameter(ParameterNames.DVT))
			commandFile = "tomcat-deploy-dvt.commands";
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		SSHCli.connect(getProperty(SSH_HOST), 
				       getProperty(SSH_USER), 
				       getProperty(SSH_PASSWORD))
			  .openChannel("shell")
			  .useCommandsFromFile(commandFile)
			  .useOutputStream(baos)
			  .execute();
		logger.trace(new String(baos.toByteArray()));
	}
	
	private void fireFirstUrl()
	throws IOException {
		try {
			logger.trace("Attente...");
			Thread.sleep(2000);
		} catch (InterruptedException ex) {
			throw new IOException(ex);
		}
		logger.trace("Première connexion à tomcat pour démarrer la servlet...");
		String fireUrl = "http://5.135.146.110:8583/cvapi/news";
		if (hasParameter(ParameterNames.DVT))
			fireUrl = "http://5.135.146.110:8584/cvapi/news";
		new URL(fireUrl).openConnection().connect();
	    logger.trace("Servlet démarrée.");
	}
	
	private void deployWebApp()
	throws IOException {
		buildLocalWebappProjects();
		uploadWebAppProject();
	}
	
	private void buildLocalWebappProjects()
	throws IOException {
		launchMaven(WEBAPP_PROJECT_PATH);
	}
	
	private void uploadWebAppProject()
	throws IOException {
		logger.trace("Téléchargement des fichiers sur le serveur...");
		String directory = "/dvtweb1";
		if (hasParameter(ParameterNames.PROD))
			directory = "/www";
		FTPCli.connect(PropertiesReader.getProperty(PropertiesNames.WEBAPP_FTP_HOST), 
			       	   PropertiesReader.getProperty(PropertiesNames.WEBAPP_FTP_USER),
			       	   PropertiesReader.getProperty(PropertiesNames.WEBAPP_FTP_PASSWORD))
		      .removeDirectory(directory, false)
		      .uploadDirectory(getWebAppUploadPath(), directory)
		      .execute();
		logger.trace("Téléchargement terminé.");
	}
	
	private String getWebAppUploadPath() {
		String path = getProperty(WEBAPP_PROJECT_PATH);
		if (hasParameter(ParameterNames.PROD))
			path += getProperty(WEBAPP_UPLOAD_PATH);
		else
			path += getProperty(WEBAPP_UPLOAD_DVT_PATH); 			
		return path;
	}
	
	private void launchMaven(String... paths)
	throws IOException {
		try {
			List<String> goals = Arrays.asList("clean","install");
			List<String> projects = Arrays.asList(paths);
			InvocationRequest request;
			Invoker invoker = new DefaultInvoker();
			invoker.setMavenHome(new File(getProperty(MAVEN_HOME)));
			Iterator<String> it = projects.iterator();
			String projectPathProperty;
			String deployTarget = "dvt";
			if (hasParameter(ParameterNames.PROD)) {
				deployTarget = "prod";
			}
			Properties props = new Properties();
			props.setProperty("project.deployTarget", deployTarget);
			while (it.hasNext()) {
				projectPathProperty = it.next();
				request = new DefaultInvocationRequest();
				request.setPomFile(new File(getProperty(projectPathProperty) + "/pom.xml"));
				request.setGoals(goals);
				if (projectPathProperty.equals(WEBAPP_PROJECT_PATH)) {
					request.setProperties(props);
				}
				invoker.execute(request);
			}
		} catch (MavenInvocationException ex) {
			throw new IOException(ex);
		}
	}
}
