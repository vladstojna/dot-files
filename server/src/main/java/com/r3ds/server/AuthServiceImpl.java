package com.r3ds.server;

import com.r3ds.Auth;
import com.r3ds.AuthServiceGrpc;
import com.r3ds.Common.Credentials;
import com.r3ds.server.exception.AuthException;
import com.r3ds.server.exception.DatabaseException;
import io.grpc.Status;

import java.sql.*;

public class AuthServiceImpl extends AuthServiceGrpc.AuthServiceImplBase {

	private boolean verbose = true;

	@Override
	public void signup(Credentials request,
					   io.grpc.stub.StreamObserver<com.r3ds.Auth.SignupResponse> responseObserver) {

		if (verbose)
			System.out.printf("Signup - create account with username %s%n", request.getUsername());

		try {
			AuthTools authTools = new AuthTools();
			authTools.signup(request.getUsername(), request.getPassword());
		} catch (AuthException e) {
			System.out.println(e.getMessage());
			responseObserver.onError(Status
					.ALREADY_EXISTS
					.withDescription("Username already exists.")
					.withCause(new AuthException("Username already exists."))
					.asRuntimeException()
			);
			return;
		} catch (DatabaseException e) {
			e.printStackTrace();
			responseObserver.onError(Status
					.INTERNAL
					.withDescription("Impossible to authenticate. Please try again.")
					.withCause(e)
					.asRuntimeException()
			);
			return;
		}

		Auth.SignupResponse response = Auth.SignupResponse.newBuilder()
				.build();

		responseObserver.onNext(response);
		responseObserver.onCompleted();
	}

	public void login(Credentials request,
					  io.grpc.stub.StreamObserver<com.r3ds.Auth.LoginResponse> responseObserver) {
	
		if (verbose)
			System.out.printf("Login - verify if exists account with username %s%n", request.getUsername());
	
		try {
			AuthTools authTools = new AuthTools();
			authTools.login(request.getUsername(), request.getPassword());
		} catch (DatabaseException e) {
			e.printStackTrace();
			responseObserver.onError(Status.INTERNAL
					.withDescription("Impossible to authenticate. Please try again.")
					.withCause(e)
					.asRuntimeException());
			return;
		} catch (AuthException e) {
			System.out.println("Username and password provided are not a match.");
			responseObserver.onError(Status.INTERNAL
					.withDescription("You are not logged in.")
					.withCause(e)
					.asRuntimeException());
			return;
		}
	
		Auth.LoginResponse response = Auth.LoginResponse.newBuilder()
				.build();
	
		responseObserver.onNext(response);
		responseObserver.onCompleted();
	}
}
