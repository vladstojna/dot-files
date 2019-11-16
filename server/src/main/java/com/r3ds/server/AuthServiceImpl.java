package com.r3ds.server;

import com.r3ds.Auth;
import com.r3ds.AuthServiceGrpc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class AuthServiceImpl extends AuthServiceGrpc.AuthServiceImplBase {

    private boolean verbose = true;

    @Override
    public void signup(com.r3ds.Auth.SignupRequest request,
                       io.grpc.stub.StreamObserver<com.r3ds.Auth.SignupResponse> responseObserver) {

        if (verbose)
            System.out.printf("Received ping %s%n", request);

        // connect to db and create an account
        String username = request.getUsername();
        String password = request.getPassword();

        Auth.SignupResponse response = Auth.SignupResponse.newBuilder()
                .setIsSignedUp(true)
                .build();

        Connection con = null;
        /*try {
            con = Database.getConnection();
            Statement stmt = con.createStatement();
            ResultSet rs = stmt.executeQuery("select * from emp");
            while(rs.next())
                System.out.println(rs.getInt(1) + "  " + rs.getString(2) + "  " + rs.getString(3));
        } catch (Exception e) {
            System.out.println("Query failed"); // TODO: resolver este try
        } finally {
            try {
                if (con != null) con.close();
            } catch (SQLException e) {
                System.out.println("Close failure"); // TODO: resolver este try
            }
        }*/

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    public void login(com.r3ds.Auth.LoginRequest request,
                      io.grpc.stub.StreamObserver<com.r3ds.Auth.LoginResponse> responseObserver) {

    }
}
