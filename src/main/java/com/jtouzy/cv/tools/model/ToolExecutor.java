package com.jtouzy.cv.tools.model;

import java.sql.Connection;
import java.util.Map;

public interface ToolExecutor {
	public void setConnection(Connection connection);
	public boolean hasParameter(String parameterName);
	public void registerParameter(String parameterName, String parameterValue);
	public void registerParameters(Map<String,String> parameters);
	public String getParameterValue(String parameterName);
	public void preControl();
	public void execute();
}
