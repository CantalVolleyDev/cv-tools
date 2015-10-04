package com.jtouzy.cv.tools;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.jtouzy.cv.tools.deploy.ProductionDeployment;
import com.jtouzy.cv.tools.errors.CLIException;
import com.jtouzy.cv.tools.errors.ToolsException;
import com.jtouzy.cv.tools.generate.ChampionshipGenerate;
import com.jtouzy.cv.tools.io.register.RegisterImport;
import com.jtouzy.cv.tools.switcher.DaySwitcher;

public class ToolsEntry {
	private CommandLine commandLine;
	
	public static void main(String[] args)
	throws ToolsException {
		new ToolsEntry(args);
	}
	
	public ToolsEntry(String[] args)
	throws ToolsException {
		try {
			CommandLineParser parser = new DefaultParser();
			commandLine = parser.parse(createOptions(), args);
			launchTool(findTool(commandLine.getOptionValue(Commands.TOOL_OPTION)));
		} catch (ParseException ex) {
			throw new CLIException(ex);
		}
	}
	
	public Options createOptions() {
		Options options = new Options();
		// -- OPTION : Identifiant de l'outil
		Option tool = new Option(Commands.TOOL_OPTION, "Outil à lancer");
		tool.setRequired(true);
		tool.setArgs(1);
		options.addOption(tool);
		// -- OPTION : -webapp
		options.addOption(new Option(Commands.DEPLOY_WEBAPP_OPTION, "Déploiement webapp"));
		// -- OPTION : -webapi
		options.addOption(new Option(Commands.DEPLOY_API_OPTION, "Déploiement api"));
		// -- OPTION : -file
		Option filePath = new Option(Commands.FILE_PATH, "Chemin de fichier");
		filePath.setArgs(1);
		options.addOption(filePath);
		// -- OPTION : -simulation
		options.addOption(new Option(Commands.SIMULATION, "Simulation"));
		// -- OPTION : -id
		Option id = new Option(Commands.ID, "Identifiant");
		id.setArgs(1);
		options.addOption(id);
		// -- OPTION : -return
		options.addOption(new Option(Commands.RETURN, "Génération du retour"));
		// -- OPTION : -fd
		Option fd = new Option(Commands.FIRST_DAY, "Journée pour échange");
		fd.setArgs(1);
		options.addOption(fd);
		// -- OPTION : -sd
		Option sd = new Option(Commands.SWITCH_DAY, "Journée pour échange");
		sd.setArgs(1);
		options.addOption(sd);
		return options;
	}
	
	public Tools findTool(String name)
	throws CLIException {
		if (name == null)
			throw new CLIException("Outil inconnu <" + name + ">");
		for (Tools tool : Tools.values()) {
			if (tool.toString().equals(name.toUpperCase()))
				return tool;
		}
		throw new CLIException("Outil inconnu <" + name + ">");
	}
	
	public void launchTool(Tools tool)
	throws ToolsException {
		ToolExecutor executor = null;
		switch (tool) {
			case DEPLOY:
				executor = new ProductionDeployment(commandLine);
				break;
			case IMPORT_REGISTER:
				executor = new RegisterImport(commandLine);
				break;
			case CHP_GEN:
				executor = new ChampionshipGenerate(commandLine);
				break;
			case DAY_SWITCH:
				executor = new DaySwitcher(commandLine);
				break;
			default:
				throw new CLIException("Outil non géré <" + tool + ">");
		}
		executor.execute();
	}
}
