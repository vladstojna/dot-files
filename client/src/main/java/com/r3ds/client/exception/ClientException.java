package com.r3ds.client.exception;

public class ClientException extends Exception {

	private static final long serialVersionUID = 1L;

	public ClientException() {
		super();
	}

	public ClientException(String message) {
		super(message);
	}

	public ClientException(Throwable cause) {
		super(cause);
	}

	public ClientException(String message, Throwable cause) {
		super(message, cause);
	}
}
