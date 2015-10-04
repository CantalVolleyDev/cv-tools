package com.jtouzy.cv.tools.generate;

import org.apache.commons.cli.CommandLine;

import com.jtouzy.cv.model.utils.ChampionshipCalendarGenerator;
import com.jtouzy.cv.tools.AbstractTool;
import com.jtouzy.cv.tools.Commands;
import com.jtouzy.cv.tools.errors.ToolsException;

public class ChampionshipGenerate extends AbstractTool {	
	public ChampionshipGenerate(CommandLine commandLine) {
		super(commandLine);
	}

	@Override
	public void execute() 
	throws ToolsException {
		try {
			if (!getCommandLine().hasOption(Commands.ID)) {
				throw new ToolsException("L'identifiant du championnat doit être renseigné");
			}
			boolean returnMatchs = false;
			if (getCommandLine().hasOption(Commands.RETURN)) {
				returnMatchs = true;
			}
			String championshipID = getCommandLine().getOptionValue(Commands.ID);
			initializeContext();
			ChampionshipCalendarGenerator.generate(this.connection, Integer.parseInt(championshipID), returnMatchs);
		} catch (Exception ex) {
			throw new ToolsException(ex);
		}
	}
}
