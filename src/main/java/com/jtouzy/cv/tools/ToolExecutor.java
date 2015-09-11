package com.jtouzy.cv.tools;

import org.apache.commons.cli.CommandLine;

import com.jtouzy.cv.tools.errors.ToolsException;

public interface ToolExecutor {
	public CommandLine getCommandLine();
	public void execute() throws ToolsException;
}
