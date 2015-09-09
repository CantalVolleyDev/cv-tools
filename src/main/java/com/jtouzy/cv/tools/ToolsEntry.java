package com.jtouzy.cv.tools;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.jtouzy.cv.tools.errors.CLIException;

public class ToolsEntry {
	private static final String TOOL_OPTION = "t";
	
	public static void main(String[] args)
	throws CLIException {
		try {
			CommandLineParser parser = new DefaultParser();
			CommandLine cl = parser.parse(createOptions(), args);
			launchTool(findTool(cl.getOptionValue(TOOL_OPTION)));
		} catch (ParseException ex) {
			throw new CLIException(ex);
		}
	}
	
	public static Options createOptions() {
		Options options = new Options();
		Option tool = new Option(TOOL_OPTION, "Outil à lancer");
		tool.setRequired(true);
		tool.setArgs(1);
		options.addOption(tool);
		return options;
	}
	
	public static Tools findTool(String name)
	throws CLIException {
		if (name == null)
			throw new CLIException("Outil inconnu <" + name + ">");
		for (Tools tool : Tools.values()) {
			if (tool.toString().equals(name.toUpperCase()))
				return tool;
		}
		throw new CLIException("Outil inconnu <" + name + ">");
	}
	
	public static void launchTool(Tools tool) {
		
	}
}
