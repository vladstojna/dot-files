package com.r3ds.server.exception;

public class FileInfoException extends Exception {
	public FileInfoException() {
		super();
	}
	
	public FileInfoException(String message) {
		super(message);
	}
	
	public FileInfoException(String message, Throwable cause) {
		super(message, cause);
	}
	
	public FileInfoException(Throwable cause) {
		super(cause);
	}
	
}
