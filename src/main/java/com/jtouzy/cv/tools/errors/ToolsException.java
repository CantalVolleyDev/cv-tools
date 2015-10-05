package com.jtouzy.cv.tools.errors;

public class ToolsException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public ToolsException() {
		super();
	}

	public ToolsException(String message, Throwable cause) {
		super(message, cause);
	}

	public ToolsException(String message) {
		super(message);
	}

	public ToolsException(Throwable cause) {
		super(cause);
	}
}
