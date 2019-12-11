package com.r3ds.mainsv;

/**
 * Server app
 */
public class ServerApp
{
	public static void main(String[] args) throws Exception
	{
		System.out.println(ServerApp.class.getSimpleName());

		if (args.length != 6) {
			System.out.printf("USAGE: java %s port certFilePath privateKeyFilePath trustCertCollectionPath backupHost backupPort%n",
				ServerApp.class.getSimpleName());
			System.exit(0);
		}

		ServerTls server = new ServerTls(
			Integer.parseInt(args[0]),
			args[1],
			args[2],
			args[3],
			args[4],
			Integer.parseInt(args[5]));
		server.start();
		server.blockUntilShutdown();
	}
}
