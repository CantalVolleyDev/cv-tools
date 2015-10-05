package com.jtouzy.cv.tools.errors;

public class MailSendingException extends RuntimeException {
	private static final long serialVersionUID = 1L;
	
	public MailSendingException(String message) {
		super(message);
	}
	
	public MailSendingException(Throwable cause) {
		super(cause);
	}
}
