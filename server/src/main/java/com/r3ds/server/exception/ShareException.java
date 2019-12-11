package com.r3ds.server.exception;

public class ShareException extends Exception {
	public ShareException() {
		super();
	}
	
	public ShareException(String message) {
		super(message);
	}
	
	public ShareException(String message, Throwable cause) {
		super(message, cause);
	}
	
	public ShareException(Throwable cause) {
		super(cause);
	}
}
