package com.r3ds.server.exception;

public class AuthException extends Exception {

	private static final long serialVersionUID = 1L;

	/**
	 * @param message
	 */
	public AuthException(String message) { super(message); }
	
	/**
	 * @param message
	 * @param cause
	 */
	public AuthException(String message, Throwable cause) { super(message, cause); }
}
