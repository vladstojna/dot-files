package com.r3ds.server;

import com.r3ds.Auth;
import com.r3ds.AuthServiceGrpc;

import java.sql.*;

public class AuthServiceImpl extends AuthServiceGrpc.AuthServiceImplBase {

    private boolean verbose = true;

    @Override
    public void signup(com.r3ds.Auth.SignupRequest request,
                       io.grpc.stub.StreamObserver<com.r3ds.Auth.SignupResponse> responseObserver) {

        if (verbose)
            System.out.printf("Signup - create account with username %s%n", request.getUsername());

        Connection conn = null;
        try {
            conn = Database.getConnection();
            PreparedStatement stmt = conn.prepareStatement("INSERT INTO user(username, password) VALUES (?, ?)");
            stmt.setString(1, request.getUsername());
            stmt.setString(2, request.getPassword());
            stmt.execute();
            conn.close();
        } catch (Exception e) {
            System.out.println("Query failed"); // TODO: resolver este try
        } finally {
            try {
                if (conn != null) conn.close();
            } catch (SQLException e) {
                System.out.println("Close failure"); // TODO: resolver este try
            }
        }

        Auth.SignupResponse response = Auth.SignupResponse.newBuilder()
                .setIsSignedUp(true)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    public void login(com.r3ds.Auth.LoginRequest request,
                      io.grpc.stub.StreamObserver<com.r3ds.Auth.LoginResponse> responseObserver) {

    }
}
