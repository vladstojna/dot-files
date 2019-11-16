package com.r3ds.client;

/**
 * Client app
 */
public class ClientApp
{
	public static void main(String[] args) throws Exception
	{
		System.out.println(ClientApp.class.getSimpleName());

		if (args.length != 3) {
			System.out.printf("USAGE: java %s host port trustCertCollectionFilePath%n",
				ClientApp.class.getSimpleName());
			System.exit(0);
		}

		ClientTls client = new ClientTls(
			args[0],
			Integer.parseInt(args[1]),
			args[2]);

		try {
			client.ping("hello server");
		} finally {
			client.shutdown();
		}
	}
}
