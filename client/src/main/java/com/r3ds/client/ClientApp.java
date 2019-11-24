package com.r3ds.client;

import java.io.Console;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.r3ds.client.exception.ClientException;

/**
 * Client app
 */
public class ClientApp
{
	private static boolean parseEmptyCommand(List<String> args) {
		if (!args.isEmpty()) {
			System.out.println("Unknown command, use 'help' to get a list of commands");
			return false;
		}
		return true;
	}

	private static boolean parseOneArgumentCommand(List<String> args) {
		if (args.size() != 1) {
			System.out.println("Unknown command, use 'help' to get a list of commands");
			return false;
		}
		return true;
	}

	private static void printHelp() {
		System.out.println();
		System.out.println("Available commands:");
		System.out.printf("%-10s %s%n", "help", "print this message");
		System.out.printf("%-10s %s%n", "exit", "exits the application");
		System.out.println();

		System.out.printf("%-10s %s%n", "signup", "begins interactive signup");
		System.out.printf("%-10s %s%n", "login", "begins interactive login");
		System.out.println();

		System.out.printf("%-8s %-15s %s%n", "download", "[filename]", "downloads file with name 'filename' from server");
		System.out.printf("%-8s %-15s %s%n", "upload", "[filename]", "uploads file with name 'filename' to server");
		System.out.printf("%-8s %-15s %s%n", "add", "[filename]", "starts tracking file in 'localpath'");
	}

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
		} catch (ClientException e) {
			e.printStackTrace();
			client.shutdown();
			System.exit(1);
		}

		boolean toExit = false;
		Console console = System.console();
		if (console == null) {
			System.out.println("No console found");
			System.exit(1);
		}
		try {
			while (toExit == false) {
				String input = console.readLine("%n>>> ").toLowerCase();
				List<String> arguments = new ArrayList<>(Arrays.asList(input.split("\\s+")));
				String command = arguments.get(0);
				arguments.remove(0);

				String username;
				char[] password;

				try {
					switch (command) {
						case "exit":
							if (parseEmptyCommand(arguments))
								toExit = true;
							break;
						case "help":
							if (parseEmptyCommand(arguments))
								printHelp();
							break;

						case "signup":
							if (!parseEmptyCommand(arguments))
								break;
							username = console.readLine("Username: ");
							password = console.readPassword("Password: ");
							char[] passwordAgain = console.readPassword("Repeat password: ");
							client.signup(username, password, passwordAgain);
							break;

						case "login":
							if (!parseEmptyCommand(arguments))
								break;
							username = console.readLine("Username: ");
							password = console.readPassword("Password: ");
							client.login(username, password);
							break;

						case "logout":
							if (parseEmptyCommand(arguments))
								client.logout();
							break;

						case "download":
							if (parseOneArgumentCommand(arguments))
								client.download(arguments.get(0));
							break;

						case "upload":
							if (parseOneArgumentCommand(arguments))
								client.upload(arguments.get(0));
							break;

						case "add":
							if (parseOneArgumentCommand(arguments))
								client.add(arguments.get(0));
							break;

						case "open":
							if (parseOneArgumentCommand(arguments))
								client.open(arguments.get(0));
							break;

						default:
							System.out.println("Unknown command, use 'help' to get a list of commands");
					}
				} catch (ClientException e) {
					System.out.println(e.getMessage());
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			client.shutdown();
		}
	}
}
