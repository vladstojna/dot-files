package com.r3ds.server.exception;

public class UnshareException extends Exception {
	public UnshareException() {
		super();
	}
	
	public UnshareException(String message) {
		super(message);
	}
	
	public UnshareException(String message, Throwable cause) {
		super(message, cause);
	}
	
	public UnshareException(Throwable cause) {
		super(cause);
	}
}
