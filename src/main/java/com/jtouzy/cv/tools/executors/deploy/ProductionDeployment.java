package com.jtouzy.cv.tools.executors.deploy;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

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

public class ProductionDeployment extends ToolExecutorImpl {
	private static Logger logger = LogManager.getLogger(ProductionDeployment.class);
	private static final String SSH_USER = "ssh.user";
	private static final String SSH_PASSWORD = "ssh.password";
	private static final String SSH_HOST = "ssh.host";
	private static final String WEBAPI_FTP_USER = "webapi.ftp.user";
	private static final String WEBAPI_FTP_PASSWORD = "webapi.ftp.password";
	private static final String WEBAPP_FTP_USER = "webapp.ftp.user";
	private static final String WEBAPP_FTP_PASSWORD = "webapp.ftp.password";
	private static final String DAO_PROJECT_PATH = "project.dao";
	private static final String MODEL_PROJECT_PATH = "project.model";
	private static final String TOOL_PROJECT_PATH = "project.tools";
	private static final String API_PROJECT_PATH = "project.api";
	private static final String UTILS_PROJECT_PATH = "project.utils";
	private static final String WEBAPP_PROJECT_PATH = "project.app";
	private static final String WEBAPI_UPLOAD_PATH = "webapi.upload";
	private static final String WEBAPP_UPLOAD_PATH = "webapp.upload";
	private static final String MAVEN_HOME = "maven.home";
	
	public ProductionDeployment() {
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
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		SSHCli.connect(getProperty(SSH_HOST), 
				       getProperty(SSH_USER), 
				       getProperty(SSH_PASSWORD))
			  .openChannel("shell")
			  .useCommandsFromFile("tomcat-deploy.commands")
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
		new URL("http://5.135.146.110:8583/cvapi/news").openConnection().connect();
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
		FTPCli.connect("ftp.cantalvolley.fr", getProperty(WEBAPP_FTP_USER), getProperty(WEBAPP_FTP_PASSWORD))
		      .removeDirectory("/dvtweb1", false)
		      .uploadDirectory(getWebAppUploadPath(), "/dvtweb1")
		      .execute();
		logger.trace("Téléchargement terminé.");
	}
	
	private String getWebAppUploadPath() {
		return getProperty(WEBAPP_PROJECT_PATH) + getProperty(WEBAPP_UPLOAD_PATH);
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
			while (it.hasNext()) {
				projectPathProperty = it.next();
				request = new DefaultInvocationRequest();
				request.setPomFile(new File(getProperty(projectPathProperty) + "/pom.xml"));
				request.setGoals(goals);
				invoker.execute(request);
			}
		} catch (MavenInvocationException ex) {
			throw new IOException(ex);
		}
	}
}
