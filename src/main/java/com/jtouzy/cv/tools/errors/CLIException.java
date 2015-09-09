package com.jtouzy.cv.tools.errors;

public class CLIException extends ToolsException {
	private static final long serialVersionUID = 1L;

	public CLIException() {
		super();
	}

	public CLIException(String message, Throwable cause) {
		super(message, cause);
	}

	public CLIException(String message) {
		super(message);
	}

	public CLIException(Throwable cause) {
		super(cause);
	}
}
