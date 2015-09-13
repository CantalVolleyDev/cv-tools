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
import com.jtouzy.cv.tools.errors.ToolsException;
import com.jtouzy.utils.ftp.FTPCli;
import com.jtouzy.utils.ssh.SSHCli;

public class ProductionDeployment extends AbstractTool {
	private static Logger logger = LogManager.getLogger(ProductionDeployment.class);
	private static final String SSH_USER = "ssh.user";
	private static final String SSH_PASSWORD = "ssh.password";
	private static final String SSH_HOST = "ssh.host";
	private static final String FTP_USER = "ftp.user";
	private static final String FTP_PASSWORD = "ftp.password";
	private static final String DAO_PROJECT_PATH = "project.dao";
	private static final String MODEL_PROJECT_PATH = "project.model";
	private static final String API_PROJECT_PATH = "project.api";
	private static final String UTILS_PROJECT_PATH = "project.utils";
	private static final String MAVEN_HOME = "maven.home";
	
	public ProductionDeployment(CommandLine commandLine) {
		super(commandLine);
	}

	@Override
	public void execute() 
	throws ToolsException {
		
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
			Properties properties = ResourceUtils.readProperties("tools");
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
		Properties properties = ResourceUtils.readProperties("tools");
		FTPCli.connect("5.135.146.110", properties.getProperty(FTP_USER), properties.getProperty(FTP_PASSWORD))
		      .removeDirectory("/jto/temp/cvapi", false)
		      .uploadDirectory("/Users/JTO/MavenProjects/cv-api/target/cvapi", "/jto/temp/cvapi")
		      .execute();
		logger.trace("Téléchargement terminé.");
	}
	
	private void tomcatDeployAndSave()
	throws IOException {
		logger.trace("Exécution des commandes sur le serveur...");
		Properties properties = ResourceUtils.readProperties("tools");
		SSHCli.connect(properties.getProperty(SSH_HOST), 
				       properties.getProperty(SSH_USER), 
				       properties.getProperty(SSH_PASSWORD))
			  .openChannel("shell")
			  .useCommandsFromFile("tomcat-deploy.commands")
			  .useOutputStream(System.out)
			  .execute();
	}
}
