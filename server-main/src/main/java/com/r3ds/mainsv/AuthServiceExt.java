package com.r3ds.mainsv;

import com.r3ds.AuthServiceGrpc;
import com.r3ds.Auth.SignupResponse;
import com.r3ds.Common.Credentials;
import com.r3ds.server.AuthServiceImpl;
import com.r3ds.server.AuthTools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

public class AuthServiceExt extends AuthServiceImpl {

	private static Logger logger = LoggerFactory.getLogger(AuthServiceExt.class);

	private final AuthServiceGrpc.AuthServiceBlockingStub backupStub;

	public AuthServiceExt(AuthTools authTools, AuthServiceGrpc.AuthServiceBlockingStub backupStub) {
		super(authTools);
		this.backupStub = backupStub;
	}

	@Override
	public void signup(Credentials request, StreamObserver<SignupResponse> responseObserver) {
		try {
			super.signup(request, responseObserver);
			backupStub.signup(request);
			logger.info("Replication RPC finished");
		} catch (StatusRuntimeException e) {
			logger.warn("Replication RPC failed: {}", e.getStatus());
		}
	}
}