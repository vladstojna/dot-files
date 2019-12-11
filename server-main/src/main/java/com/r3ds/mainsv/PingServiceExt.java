package com.r3ds.mainsv;

import com.r3ds.Ping;
import com.r3ds.PingServiceGrpc;
import com.r3ds.server.PingServiceImpl;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

public class PingServiceExt extends PingServiceImpl {

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
		} catch (StatusRuntimeException e) {
			responseObserver.onError(Status.INTERNAL
				.withDescription("Ping failed")
				.withCause(e)
				.asRuntimeException()
			);
		}
	}
}