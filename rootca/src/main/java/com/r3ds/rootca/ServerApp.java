package com.r3ds.rootca;

import java.io.IOException;

/**
 * Server app
 */
public class ServerApp
{
	public static void main(String[] args) throws IOException, InterruptedException
	{
		System.out.println(ServerApp.class.getSimpleName());

		if (args.length != 4) {
			System.out.printf("USAGE: java %s port certFilePath privateKeyFilePath signedCertsPath%n",
				ServerApp.class.getSimpleName());
			System.exit(0);
		}

		ServerTls server = new ServerTls(
			Integer.parseInt(args[0]),
			args[1],
			args[2],
			args[3]);
		server.start();
		server.blockUntilShutdown();
	}
}