package com.r3ds.server.exception;

public class DatabaseException extends Exception {

    /**
     * @param message
     * @param cause
     */
    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
