package com.jtouzy.cv.tools;

import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

import com.jtouzy.cv.tools.errors.ToolsException;
import com.jtouzy.cv.tools.executors.backup.XmlBackUtils;
import com.jtouzy.cv.tools.executors.calgen.ChampionshipCalendarGenerator;
import com.jtouzy.cv.tools.executors.dayswitch.DaySwitcher;
import com.jtouzy.cv.tools.executors.dbgen.DBGenerateTool;
import com.jtouzy.cv.tools.executors.deploy.ProductionDeployment;
import com.jtouzy.cv.tools.executors.esjreg.TeamPlayerRegister;
import com.jtouzy.cv.tools.executors.impreg.ImportRegister;
import com.jtouzy.cv.tools.executors.pwdgen.PasswordGenerator;
import com.jtouzy.cv.tools.model.ToolExecutor;
import com.jtouzy.cv.tools.model.ToolsList;

public class ToolLauncher {
	private Map<String, String> parameters;
	private ToolsList targetTool;
	private Connection connection;
	private ToolExecutor executor;
	
	public static final ToolLauncher build() {
		ToolLauncher launcher = new ToolLauncher();
		launcher.parameters = new HashMap<>();
		return launcher;
	}
	
	public static final ToolExecutor findExecutor(ToolsList tool) {
		ToolExecutor executor = null;
		switch (tool) {
			case CALENDAR_GEN:
				executor = new ChampionshipCalendarGenerator();
				break;
			case DAY_SWITCH:
				executor = new DaySwitcher();
				break;
			case DEPLOY:
				executor = new ProductionDeployment();
				break;
			case IMPORT_REGISTER:
				executor = new ImportRegister();
				break;
			case DB_GEN:
				executor = new DBGenerateTool();
				break;
			case BACKUP:
				executor = new XmlBackUtils();
				break;
			case PWD_GEN:
				executor = new PasswordGenerator();
				break;
			case ESJ_GEN:
				executor = new TeamPlayerRegister();
				break;
		}
		if (executor == null) {
			throw new ToolsException("Aucun exécutable trouvé pour : " + tool);
		}
		return executor;
	}
	
	public ToolLauncher useConnection(Connection connection) {
		this.connection = connection;
		return this;
	}
	
	public ToolLauncher target(ToolsList tool) {
		this.targetTool = tool;
		return this;
	}
	
	public ToolLauncher addParameter(String key, Object value) {
		parameters.put(key, String.valueOf(value));
		return this;
	}
	
	public ToolExecutor instance() {
		return getExecutor();
	}
	
	public ToolLauncher run() {
		getExecutor();
		executor.execute();
		return this;
	}
	
	private ToolExecutor getExecutor() {
		if (executor != null)
			return executor;
		executor = findExecutor(this.targetTool);
		executor.registerParameters(parameters);
		executor.setConnection(connection);
		return executor;
	}
}
