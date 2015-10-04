package com.jtouzy.cv.tools.switcher;

import org.apache.commons.cli.CommandLine;

import com.jtouzy.cv.model.utils.ChampionshipDaySwitcher;
import com.jtouzy.cv.tools.AbstractTool;
import com.jtouzy.cv.tools.Commands;
import com.jtouzy.cv.tools.errors.ToolsException;

public class DaySwitcher extends AbstractTool {
	public DaySwitcher(CommandLine commandLine) {
		super(commandLine);
	}

	@Override
	public void execute() 
	throws ToolsException {
		try {
			if (!getCommandLine().hasOption(Commands.ID))
				throw new ToolsException("Le numéro de championnat est absent");
			if (!getCommandLine().hasOption(Commands.FIRST_DAY))
				throw new ToolsException("La première journée doit être indiquée");			
			if (!getCommandLine().hasOption(Commands.SWITCH_DAY))
				throw new ToolsException("La journée d'échange doit être indiquée");
			
			Integer championshipId = Integer.parseInt(getCommandLine().getOptionValue(Commands.ID)),
					firstDay = Integer.parseInt(getCommandLine().getOptionValue(Commands.FIRST_DAY)),
					dayToSwitch = Integer.parseInt(getCommandLine().getOptionValue(Commands.SWITCH_DAY));
			initializeContext();
			ChampionshipDaySwitcher.switchDay(this.connection, championshipId, firstDay, dayToSwitch);
		} catch (Exception ex) {
			throw new ToolsException(ex);
		}
	}
}
