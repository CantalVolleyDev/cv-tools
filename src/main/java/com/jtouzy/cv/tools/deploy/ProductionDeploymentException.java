package com.jtouzy.cv.tools.deploy;

public class ProductionDeploymentException extends Exception {
	private static final long serialVersionUID = 1L;
	
	public ProductionDeploymentException(String message) {
		super(message);
	}
	public ProductionDeploymentException(Throwable cause) {
		super(cause);
	}
}
