package com.jtouzy.cv.tools.deploy;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;

import com.jtouzy.utils.resources.ResourceUtils;
import com.jtouzy.cv.tools.AbstractTool;
import com.jtouzy.cv.tools.Commands;
import com.jtouzy.cv.tools.errors.ProductionDeploymentException;
import com.jtouzy.cv.tools.errors.ToolsException;
import com.jtouzy.utils.ftp.FTPCli;
import com.jtouzy.utils.ssh.SSHCli;

public class ProductionDeployment extends AbstractTool {
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
	private static final String API_PROJECT_PATH = "project.api";
	private static final String UTILS_PROJECT_PATH = "project.utils";
	private static final String WEBAPP_PROJECT_PATH = "project.app";
	private static final String WEBAPI_UPLOAD_PATH = "webapi.upload";
	private static final String WEBAPP_UPLOAD_PATH = "webapp.upload";
	private static final String MAVEN_HOME = "maven.home";
	private Properties properties;
	
	public ProductionDeployment(CommandLine commandLine) {
		super(commandLine);
	}

	@Override
	public void execute() 
	throws ToolsException {
		try {
			this.properties = ResourceUtils.readProperties("tools");
			if (getCommandLine().hasOption(Commands.DEPLOY_API_OPTION)) {
				deployWebAPI();
			} else if (getCommandLine().hasOption(Commands.DEPLOY_WEBAPP_OPTION)) {
				deployWebApp();
			}
		} catch (IOException ex) {
			throw new ToolsException(ex);
		}
	}
	
	private void deployWebAPI()
	throws ProductionDeploymentException, IOException {
		buildLocalProjects();
		uploadWebAPIProject();
		tomcatDeployAndSave();
	}
	
	private void buildLocalProjects()
	throws IOException {
		try {
			List<String> goals = Arrays.asList("clean","install");
			List<String> projects = Arrays.asList(
				UTILS_PROJECT_PATH, DAO_PROJECT_PATH, MODEL_PROJECT_PATH, API_PROJECT_PATH
			);
			InvocationRequest request;
			Invoker invoker = new DefaultInvoker();
			invoker.setMavenHome(new File(properties.getProperty(MAVEN_HOME)));
			Iterator<String> it = projects.iterator();
			String projectPathProperty;
			while (it.hasNext()) {
				projectPathProperty = it.next();
				request = new DefaultInvocationRequest();
				request.setPomFile(new File(properties.getProperty(projectPathProperty) + "/pom.xml"));
				request.setGoals(goals);
				invoker.execute(request);
			}
		} catch (MavenInvocationException ex) {
			throw new IOException(ex);
		}
	}
	
	private void uploadWebAPIProject()
	throws IOException {
		logger.trace("Téléchargement des fichiers sur le serveur...");
		FTPCli.connect("5.135.146.110", properties.getProperty(WEBAPI_FTP_USER), properties.getProperty(WEBAPI_FTP_PASSWORD))
		      .removeDirectory("/jto/temp/cvapi", false)
		      .uploadDirectory(getWebAPIUploadPath(), "/jto/temp/cvapi")
		      .execute();
		logger.trace("Téléchargement terminé.");
	}
	
	private String getWebAPIUploadPath() {
		return properties.getProperty(API_PROJECT_PATH) + properties.getProperty(WEBAPI_UPLOAD_PATH);
	}
	
	private void tomcatDeployAndSave()
	throws IOException {
		logger.trace("Exécution des commandes sur le serveur...");
		SSHCli.connect(properties.getProperty(SSH_HOST), 
				       properties.getProperty(SSH_USER), 
				       properties.getProperty(SSH_PASSWORD))
			  .openChannel("shell")
			  .useCommandsFromFile("tomcat-deploy.commands")
			  .useOutputStream(System.out)
			  .execute();
	}
	
	private void deployWebApp()
	throws IOException {
		logger.trace("Téléchargement des fichiers sur le serveur...");
		FTPCli.connect("ftp.cantalvolley.fr", properties.getProperty(WEBAPP_FTP_USER), properties.getProperty(WEBAPP_FTP_PASSWORD))
		      .removeDirectory("/dvtweb1", false)
		      .uploadDirectory(getWebAppUploadPath(), "/dvtweb1")
		      .execute();
		logger.trace("Téléchargement terminé.");
	}
	
	private String getWebAppUploadPath() {
		return properties.getProperty(WEBAPP_PROJECT_PATH) + properties.getProperty(WEBAPP_UPLOAD_PATH);
	}
}
