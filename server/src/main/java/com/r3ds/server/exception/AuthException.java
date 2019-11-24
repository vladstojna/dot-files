package com.r3ds.server.exception;

import io.grpc.Status;

public class AuthException extends Exception {
	
	Status status;
	
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
