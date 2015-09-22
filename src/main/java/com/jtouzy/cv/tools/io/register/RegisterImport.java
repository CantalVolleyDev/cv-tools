package com.jtouzy.cv.tools.io.register;

import org.apache.commons.cli.CommandLine;

import com.jtouzy.cv.tools.AbstractTool;
import com.jtouzy.cv.tools.Commands;
import com.jtouzy.cv.tools.errors.ToolsException;

/**
 * Lecture du fichier d'imporation des inscriptions<br>
 * Format du fichier : <br>
 * <code>
 *   <root>
 *   	<team name="">
 *   		<player name="" firstName="" mail="" tel="">
 *   	</team>	
 *   </root>
 * </code>
 * @author JTO
 */
public class RegisterImport extends AbstractTool {
	public RegisterImport(CommandLine commandLine) {
		super(commandLine);
	}

	@Override
	public void execute() 
	throws ToolsException {
		if (!getCommandLine().hasOption(Commands.FILE_PATH)) {
			throw new ToolsException("Chemin du fichier obligatoire avec le paramètre \"-file\"");
		}
		//String filePath = getCommandLine().getOptionValue(Commands.FILE_PATH);
	}
}
