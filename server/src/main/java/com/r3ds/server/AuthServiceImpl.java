package com.r3ds.server;

import com.r3ds.Auth;
import com.r3ds.AuthServiceGrpc;
import com.r3ds.Common.Credentials;
import com.r3ds.server.exception.AuthException;
import io.grpc.Status;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.*;

public class AuthServiceImpl extends AuthServiceGrpc.AuthServiceImplBase {

    private boolean verbose = true;

    @Override
    public void signup(Credentials request,
                       io.grpc.stub.StreamObserver<com.r3ds.Auth.SignupResponse> responseObserver) {

        if (verbose)
            System.out.printf("Signup - create account with username %s%n", request.getUsername());

        try {
            if (!AuthTools.signup(Database.getConnection(), request.getUsername(), request.getPassword())) {
                responseObserver.onError(Status
                        .ALREADY_EXISTS
                        .withDescription("Username already exists.")
                        .withCause(new AuthException("Username already exists."))
                        .asRuntimeException()
                );
            }
        } catch (SQLException e) {
            responseObserver.onError(Status
                    .INTERNAL
                    .withDescription("Impossible to authenticate. Please try again.")
                    .withCause(e)
                    .asRuntimeException()
            );
        } catch (AuthException e) {
            responseObserver.onError(Status
                    .INTERNAL
                    .withDescription(e.getMessage())
                    .withCause(e)
                    .asRuntimeException()
            );
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
            AuthTools.login(Database.getConnection(), request.getUsername(), request.getPassword());
        } catch (SQLException e) {
            e.printStackTrace();
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Impossible to authenticate. Please try again.")
                    .withCause(e)
                    .asRuntimeException());
        } catch (AuthException e) {
            System.out.println("Username and password provided are not a match.");
            responseObserver.onError(Status.INTERNAL
                    .withDescription("You are not logged in.")
                    .withCause(e)
                    .asRuntimeException());
        }
    
        Auth.LoginResponse response = Auth.LoginResponse.newBuilder()
                .build();
    
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
