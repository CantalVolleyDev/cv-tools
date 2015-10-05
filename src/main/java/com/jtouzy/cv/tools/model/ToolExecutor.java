package com.jtouzy.cv.tools.model;

public interface ToolExecutor {
	public boolean hasParameter(String parameterName);
	public void registerParameter(String parameterName, String parameterValue);
	public String getParameterValue(String parameterName);
	public void execute();
}
