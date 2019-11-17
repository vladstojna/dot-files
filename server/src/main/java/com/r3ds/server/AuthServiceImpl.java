package com.r3ds.server;

import com.r3ds.Auth;
import com.r3ds.AuthServiceGrpc;
import io.grpc.Status;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.*;

public class AuthServiceImpl extends AuthServiceGrpc.AuthServiceImplBase {

    private boolean verbose = true;

    @Override
    public void signup(com.r3ds.Auth.Credentials request,
                       io.grpc.stub.StreamObserver<com.r3ds.Auth.SignupResponse> responseObserver) {

        if (verbose)
            System.out.printf("Signup - create account with username %s%n", request.getUsername());

        Connection conn = null;
        try {
            conn = Database.getConnection();
            PreparedStatement stmt = conn.prepareStatement("INSERT INTO user(username, password) VALUES (?, ?)");
            stmt.setString(1, request.getUsername());
            stmt.setString(2, BCrypt.hashpw(request.getPassword(), BCrypt.gensalt()));
            stmt.execute();
            conn.close();
        } catch (SQLException e) {
            if (e.getSQLState().equals("23000")) {
                System.out.println("Query failed because username already exists"); // TODO: resolver este try
                responseObserver.onError(Status
                        .ALREADY_EXISTS
                        .withDescription("Username already exists.")
                        .withCause(e)
                        .asRuntimeException()
                );
            } else {
                System.out.println("Query failed"); // TODO: resolver este try
                responseObserver.onError(Status
                        .INTERNAL
                        .withDescription("Query failed.")
                        .withCause(e)
                        .asRuntimeException()
                );
            }
        } catch (Exception e) {
            System.out.println("Something unexpected happened"); // TODO: resolver este try
            responseObserver.onError(Status
                    .INTERNAL
                    .withDescription("Something unexpected happened.")
                    .withCause(e)
                    .asRuntimeException()
            );
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    /* ignored */
                    System.out.println("Close failure");
                }
            }
        }

        Auth.SignupResponse response = Auth.SignupResponse.newBuilder()
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    public void login(com.r3ds.Auth.Credentials request,
                      io.grpc.stub.StreamObserver<com.r3ds.Auth.LoginResponse> responseObserver) {
        
    }
}
