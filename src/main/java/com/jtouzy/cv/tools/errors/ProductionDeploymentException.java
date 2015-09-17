package com.jtouzy.cv.tools.errors;

public class ProductionDeploymentException extends ToolsException {
	private static final long serialVersionUID = 1L;
	
	public ProductionDeploymentException(String message) {
		super(message);
	}
	public ProductionDeploymentException(Throwable cause) {
		super(cause);
	}
}
