package com.r3ds.mainsv;

import com.r3ds.Ping;
import com.r3ds.PingServiceGrpc;
import com.r3ds.server.PingServiceImpl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

public class PingServiceExt extends PingServiceImpl {

	private Logger logger = LoggerFactory.getLogger(PingServiceExt.class);

	private PingServiceGrpc.PingServiceBlockingStub backupStub;

	public PingServiceExt(PingServiceGrpc.PingServiceBlockingStub backupStub) {
		super();
		this.backupStub = backupStub;
	}

	@Override
	public void ping(Ping.PingRequest request, StreamObserver<Ping.PingResponse> responseObserver) {
		try {
			super.ping(request, responseObserver);
			backupStub.ping(request);
			logger.info("Replication RPC finished");
		} catch (StatusRuntimeException e) {
			logger.warn("Replication RPC failed: {}", e.getStatus());
		}
	}
}