package com.jtouzy.cv.tools;

import java.util.NoSuchElementException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.jtouzy.cv.tools.errors.ToolsException;
import com.jtouzy.cv.tools.model.ParameterNames;
import com.jtouzy.cv.tools.model.ToolExecutor;
import com.jtouzy.cv.tools.model.ToolsList;

public class ToolsEntry {
	private CommandLine commandLine;
	
	public static void main(String[] args) {
		new ToolsEntry(args);
	}
	
	public ToolsEntry(String[] args) {
		try {
			CommandLineParser parser = new DefaultParser();
			commandLine = parser.parse(createOptions(), args);
			launchTool();
		} catch (ParseException ex) {
			throw new ToolsException(ex);
		}
	}
	
	public Options createOptions() {
		final Options options = new Options();
		Option toolOption = new Option(ParameterNames.TOOL, "Outil à lancer");
		toolOption.setRequired(true);
		toolOption.setArgs(1);
		options.addOption(toolOption);
		ToolsList[] toolsList = ToolsList.values();
		for (ToolsList tool : toolsList) {
			if (!tool.hasParameters())
				continue;
			tool.getParameters().forEach(p -> {
				Option option = new Option(p.getCommandLineName(), p.getDescription());
				if (p.isWithValue())
					option.setArgs(1);
				options.addOption(option);
			});
		}
		return options;
	}
	
	public void launchTool() {
		String toolName = commandLine.getOptionValue(ParameterNames.TOOL);
		if (toolName == null) {
			throw new ToolsException("Outil non renseigné");
		}
		ToolsList tool;
		try {
			tool = ToolsList.findByName(toolName);
		} catch (NoSuchElementException ex) {
			throw new ToolsException("Outil non trouvé dans la liste existante : " + toolName);
		}
		ToolExecutor executor = ToolLauncher.findExecutor(tool);
		Option[] options = commandLine.getOptions();
		for (Option option : options) {
			executor.registerParameter(option.getOpt(), option.getValue());
		}
		executor.execute();
	}
}
