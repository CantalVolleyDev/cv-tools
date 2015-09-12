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
		Option tool = new Option(Commands.TOOL_OPTION, "Outil à lancer");
		tool.setRequired(true);
		tool.setArgs(1);
		options.addOption(tool);
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
			default:
				throw new CLIException("Outil non géré <" + tool + ">");
		}
		executor.execute();
	}
}
