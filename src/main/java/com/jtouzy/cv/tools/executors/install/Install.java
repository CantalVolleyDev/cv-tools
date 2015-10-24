package com.jtouzy.cv.tools.executors.install;

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

import com.jtouzy.cv.config.PropertiesReader;
import com.jtouzy.cv.tools.errors.ToolsException;
import com.jtouzy.cv.tools.model.ParameterNames;
import com.jtouzy.cv.tools.model.ToolExecutorImpl;
import com.jtouzy.utils.ftp.FTPCli;
import com.jtouzy.utils.ssh.SSHCli;

public class Install extends ToolExecutorImpl {
	private static Logger logger = LogManager.getLogger(Install.class);
	
	/* #################################################### */
	/* # INFORMATIONS DE CONNEXION SSH AU SERVEUR           */
	/* #################################################### */
	private static final String SERVER_SSH_HOST = "server.ssh.host";
	private static final String SERVER_SSH_USER = "server.ssh.user";
	private static final String SERVER_SSH_PASSWORD = "server.ssh.password";
	
	/* #################################################### */
	/* # INFORMATIONS DE CONNEXION FTP AU SERVEUR           */
	/* #################################################### */
	private static final String SERVER_FTP_HOST = "server.ftp.host";
	private static final String SERVER_FTP_USER = "server.ftp.user";
	private static final String SERVER_FTP_PASSWORD = "server.ftp.password";
	
	/* #################################################### */
	/* # CHEMINS DES PROJETS LOCAUX                         */
	/* #################################################### */
	private static final String UTILS_PROJECT_PATH = "project.utils";
	private static final String DAO_PROJECT_PATH = "project.dao";
	private static final String MODEL_PROJECT_PATH = "project.model";
	private static final String TOOL_PROJECT_PATH = "project.tools";
	private static final String API_PROJECT_PATH = "project.api";
	private static final String WEBAPP_PROJECT_PATH = "project.app";
	
	/* #################################################### */
	/* # CHEMINS DES SOURCES FINALES DANS LES PROJETS       */
	/* # Les chemins locaux sont relatifs par rapports      */ 
	/* # aux chemins des projets                            */
	/* #################################################### */
	private static final String API_LOCAL_UPLOAD_PATH = "api.local.path";
	private static final String APP_LOCAL_UPLOAD_PATH = "app.local.path";
	private static final String APP_LOCAL_DVT_UPLOAD_PATH  = "app.local.dvt.path";
	private static final String API_SERVER_UPLOAD_PATH = "api.server.path";
	private static final String APP_SERVER_UPLOAD_PATH = "app.server.path";
	
	/* #################################################### */
	/* # CHEMIN DE MAVEN_HOME
	/* #################################################### */
	private static final String MAVEN_HOME = "maven.home";
	
	public Install() {
	}

	@Override
	public void execute() {
		try {
			initializeProperties();			
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
		uploadWeb(getWebAPIUploadPath(), getProperty(API_SERVER_UPLOAD_PATH));
	}
	
	private String getWebAPIUploadPath() {
		return getProperty(API_PROJECT_PATH) + getProperty(API_LOCAL_UPLOAD_PATH);
	}
	
	private void tomcatDeployAndSave()
	throws IOException {
		logger.trace("Exécution des commandes sur le serveur...");
		String commandFile = "tomcat-deploy-prod.commands";
		if (hasParameter(ParameterNames.DVT))
			commandFile = "tomcat-deploy-dvt.commands";
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		SSHCli.connect(getProperty(SERVER_SSH_HOST), 
				       getProperty(SERVER_SSH_USER), 
				       getProperty(SERVER_SSH_PASSWORD))
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
		String fireUrl = "http://cantalvolley.fr:8583/cvapi/news";
		if (hasParameter(ParameterNames.DVT))
			fireUrl = "http://cantalvolley.fr:8584/cvapi/news";
		new URL(fireUrl).openConnection().connect();
	    logger.trace("Servlet démarrée.");
	}
	
	private void deployWebApp()
	throws IOException {
		buildLocalWebappProjects();
		uploadWebAppProject();
		apacheDeploy();
	}
	
	private void buildLocalWebappProjects()
	throws IOException {
		launchMaven(WEBAPP_PROJECT_PATH);
	}
	
	private void uploadWebAppProject()
	throws IOException {
		uploadWeb(getWebAppUploadPath(), getProperty(APP_SERVER_UPLOAD_PATH));
	}
	
	private void uploadWeb(String localDirectory, String remoteDirectory)
	throws IOException {
		logger.trace("Téléchargement des fichiers sur le serveur...");
		FTPCli.connect(getProperty(SERVER_FTP_HOST), getProperty(SERVER_FTP_USER), getProperty(SERVER_FTP_PASSWORD))
		      .removeDirectory(remoteDirectory, false)
		      .uploadDirectory(localDirectory, remoteDirectory)
		      .execute();
		logger.trace("Téléchargement terminé.");
	}
	
	private String getWebAppUploadPath() {
		String path = getProperty(WEBAPP_PROJECT_PATH);
		if (hasParameter(ParameterNames.PROD))
			path += getProperty(APP_LOCAL_UPLOAD_PATH);
		else
			path += getProperty(APP_LOCAL_DVT_UPLOAD_PATH); 			
		return path;
	}
	
	private void apacheDeploy()
	throws IOException {
		logger.trace("Exécution des commandes sur le serveur...");
		String commandFile = "apache-deploy-prod.commands";
		if (hasParameter(ParameterNames.DVT))
			commandFile = "apache-deploy-dvt.commands";
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		SSHCli.connect(getProperty(SERVER_SSH_HOST), 
				       getProperty(SERVER_SSH_USER), 
				       getProperty(SERVER_SSH_PASSWORD))
			  .openChannel("shell")
			  .useCommandsFromFile(commandFile)
			  .useOutputStream(baos)
			  .execute();
		logger.trace(new String(baos.toByteArray()));
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
