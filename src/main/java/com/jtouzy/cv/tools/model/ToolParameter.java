package com.jtouzy.cv.tools.model;

public class ToolParameter {
	private String name;
	private String description;
	private String commandLineName;
	private boolean withValue;
	
	public ToolParameter(String name, String description, String commandLineName, boolean withValue) {
		super();
		this.name = name;
		this.description = description;
		this.commandLineName = commandLineName;
		this.withValue = withValue;
	}
	
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	public String getCommandLineName() {
		return commandLineName;
	}
	public void setCommandLineName(String commandLineName) {
		this.commandLineName = commandLineName;
	}
	public boolean isWithValue() {
		return withValue;
	}
	public void setWithValue(boolean withValue) {
		this.withValue = withValue;
	}
}
