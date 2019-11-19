package com.r3ds.client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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

			System.out.print("\n>>> ");
			System.out.flush();
			
			BufferedReader buffer = new BufferedReader(new InputStreamReader(System.in));
			while (!(input = buffer.readLine().toLowerCase()).equals("exit")) {
				inputArgs = new ArrayList<>(Arrays.asList(input.split("\\s+")));
				methodName = inputArgs.get(0);
				inputArgs.remove(0);
				
				try {
					// Reflection - in this case, discover what method has to call
					Method method = client.getClass().getDeclaredMethod(methodName, List.class);
					// call the found method in client object with the given args
					method.invoke(client, inputArgs);
				} catch (SecurityException | NoSuchMethodException e) {
					e.printStackTrace();
					System.out.println(e.getMessage());
				} catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
					// it should not be get here since all methods will receive a list of strings as argument
					e.printStackTrace();
					System.out.println(e.getMessage());
				}
				System.out.print("\n>>> ");
				System.out.flush();
			}
		} finally {
			client.shutdown();
		}
	}
}
