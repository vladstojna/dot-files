package com.r3ds.client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
			String input;
			List<String> inputArgs = null;
			String methodName;

			BufferedReader buffer = new BufferedReader(new InputStreamReader(System.in));
			while (!(input = buffer.readLine().toLowerCase()).equals("exit")) {
				inputArgs = Arrays.asList(input.split(" "));
				methodName = inputArgs.get(0);
				inputArgs.remove(0);
				client.getClass().getDeclaredMethod(methodName).invoke(inputArgs);
			}
		} finally {
			client.shutdown();
		}
	}
}
