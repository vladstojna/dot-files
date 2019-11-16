package com.r3ds.server;

import com.r3ds.Ping;
import com.r3ds.PingServiceGrpc;

import io.grpc.stub.StreamObserver;

public class PingServiceImpl extends PingServiceGrpc.PingServiceImplBase {

	private boolean verbose = true;

	@Override
	public void ping(Ping.PingRequest request, StreamObserver<Ping.PingResponse> responseObserver) {

		if (verbose)
			System.out.printf("Received ping %s%n", request);

		Ping.PingResponse response = Ping.PingResponse.newBuilder()
			.setMessage(request.getMessage())
			.build();

		responseObserver.onNext(response);
		responseObserver.onCompleted();
	}
}