package com.jtouzy.cv.tools;

import org.apache.commons.cli.CommandLine;

public abstract class AbstractTool implements ToolExecutor {
	private CommandLine commandLine;
	
	public AbstractTool(CommandLine commandLine) {
		this.commandLine = commandLine;
	}
	
	@Override
	public CommandLine getCommandLine() {
		return this.commandLine;
	}
}
