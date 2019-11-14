package com.r3ds.client;

import java.util.Scanner;

import com.r3ds.Ping;
import com.r3ds.PingServiceGrpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

/**
 * Client app
 */
public class ClientApp
{
	public static void main(String[] args) throws Exception
	{
		System.out.println(ClientApp.class.getSimpleName());

		// check arguments
		if (args.length < 1) {
			System.err.println("Argument(s) missing!");
			System.err.printf("Usage: java %s port%n", ClientApp.class.getName());
			return;
		}

		final String host = args[0];
		final int port = Integer.parseInt(args[1]);
		final String target = host + ":" + port;

		System.out.printf("target: %s%n", target);

		// plaintext communication for now
		final ManagedChannel channel = ManagedChannelBuilder
			.forTarget(target)
			.usePlaintext()
			.build();

		// blocking stub
		PingServiceGrpc.PingServiceBlockingStub stub = PingServiceGrpc.newBlockingStub(channel);

		Ping.PingRequest request = Ping.PingRequest.newBuilder().setMessage("ping").build();

		Ping.PingResponse response = stub.ping(request);

		System.out.println(response);

		System.out.print("<enter> to exit");
		(new Scanner(System.in)).nextLine();

		channel.shutdownNow();

	}
}
