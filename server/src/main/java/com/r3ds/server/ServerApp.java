package com.r3ds.server;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;

/**
 * Server app
 */
public class ServerApp
{
	public static void main(String[] args) throws Exception
	{
		System.out.println(ServerApp.class.getSimpleName());

		// check arguments
		if (args.length < 1) {
			System.err.println("Argument(s) missing!");
			System.err.printf("Usage: java %s port%n", ServerApp.class.getName());
			return;
		}

		int port = Integer.parseInt(args[0]);

		System.out.printf("port: %d%n", port);

		// Create a new server to listen on port
		Server server = ServerBuilder
			.forPort(port)
			.addService((BindableService) new PingServiceImpl())
			.build();

		server.start();

		System.out.println("Server started");

		server.awaitTermination();
	}
}
