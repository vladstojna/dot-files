package com.r3ds.client;

import java.io.Console;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.r3ds.client.exception.ClientAggregateException;
import com.r3ds.client.exception.ClientException;

/**
 * Client app
 */
public class ClientApp
{
	private interface ComparisonFunc {
		public boolean comp(int a, int b);
	}

	private static boolean parseCommand(List<String> args, int numArgs, ComparisonFunc cmp) {
		if (cmp.comp(args.size(), numArgs)) {
			System.out.println("Unknown command, use 'help' to get a list of commands");
			return false;
		}
		return true;
	}

	private static boolean parseEmptyCommand(List<String> args) {
		return parseCommand(args, 0, (a, b) -> a != b);
	}

	private static boolean parseOneArgumentCommand(List<String> args) {
		return parseCommand(args, 1, (a, b) -> a != b);
	}

	private static boolean parseTwoArgumentCommand(List<String> args) {
		return parseCommand(args, 2, (a, b) -> a != b);
	}

	private static boolean parseOneTwoArgumentsCommand(List<String> args) {
		return parseCommand(args, 2, (a, b) -> a > 2 || a < 1);
	}

	private static void printHelp() {
		System.out.println();
		System.out.println("Available commands:");
		System.out.printf("%-10s%s%n", "help", "print this message");
		System.out.printf("%-10s%s%n", "exit", "exits the application");
		System.out.println();

		System.out.printf("%-10s%s%n", "signup", "begins interactive signup");
		System.out.printf("%-10s%s%n", "login", "begins interactive login");
		System.out.println();

		System.out.printf("%-10s%-15s%s%n", "add", "[localpath]", "starts tracking file in 'localpath'");
		System.out.println();

		System.out.printf("%-10s%-11s%-10s%s%n", "download", "[filename]", "{owner}", "downloads 'filename' (opt. shared by 'owner') from server");
		System.out.printf("%-10s%-11s%-10s%s%n", "upload",   "[filename]", "{owner}", "uploads 'filename' (opt. shared by 'owner') to server");
		System.out.printf("%-10s%-11s%-10s%s%n", "open",     "[filename]", "{owner}", "decrypts 'filename' (opt. shared by 'owner')");
		System.out.printf("%-10s%-11s%-10s%s%n", "close",    "[filename]", "{owner}", "encrypts 'filename' (opt. shared by 'owner')");
		System.out.printf("%-31s%s%n", "closeall", "encrypts all opened files");
		System.out.printf("%-31s%s%n", "list", "lists current user's files");
		System.out.println();
		
		System.out.printf("%-10s%-11s%-10s%s%n", "share", "[filename]", "[user]", "shares 'filename' with 'user'");
		System.out.printf("%-10s%-11s%-10s%s%n", "unshare", "[filename]", "[user]", "un-shares 'filename' with 'user'");
	}

	public static void main(String[] args) throws Exception
	{
		System.out.println(ClientApp.class.getSimpleName());

		if (args.length != 5) {
			System.out.printf("USAGE: java %s serverHost serverPort caHost caPort trustCertCollectionFilePath%n",
				ClientApp.class.getSimpleName());
			System.exit(0);
		}

		ClientTls client = new ClientTls(
			args[0],
			Integer.parseInt(args[1]),
			args[2],
			Integer.parseInt(args[3]),
			args[4]);

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
							if (parseEmptyCommand(arguments)) {
								try {
									System.out.println("Exiting...");
									client.exit();
								} catch (ClientAggregateException e) {
									System.out.println(e.getAggregatedMessage());
								} finally {
									toExit = true;
									System.out.println("Exit success");
								}
							}
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
							System.out.println("Signing up...");
							client.signup(username, password, passwordAgain);
							System.out.println("Signup success");
							break;

						case "login":
							if (!parseEmptyCommand(arguments))
								break;
							username = console.readLine("Username: ");
							password = console.readPassword("Password: ");
							System.out.println("Logging in...");
							client.login(username, password);
							System.out.println("Login success");
							break;

						case "logout":
							if (parseEmptyCommand(arguments)) {
								System.out.println("Logging out...");
								client.logout();
								System.out.println("Logout success");
							}
							break;

						case "download":
							if (parseOneTwoArgumentsCommand(arguments)) {
								System.out.printf("Downloading %s...%n", arguments.get(0));
								client.download(arguments.get(0), arguments.size() == 2 ? arguments.get(1) : null);
								System.out.println("Download success");
							}
							break;

						case "upload":
							if (parseOneTwoArgumentsCommand(arguments)) {
								System.out.printf("Uploading %s...%n", arguments.get(0));
								client.upload(arguments.get(0), arguments.size() == 2 ? arguments.get(1) : null);
								System.out.println("Upload success");
							}
							break;

						case "add":
							if (parseOneArgumentCommand(arguments)) {
								System.out.printf("Adding %s...%n", arguments.get(0));
								client.add(arguments.get(0));
								System.out.println("Add success");
							}
							break;

						case "open":
							if (parseOneTwoArgumentsCommand(arguments)) {
								System.out.printf("Opening %s...%n", arguments.get(0));
								client.open(arguments.get(0), arguments.size() == 2 ? arguments.get(1) : null);
								System.out.println("Open success");
							}
							break;

						case "close":
							if (parseOneTwoArgumentsCommand(arguments)) {
								System.out.printf("Closing %s...%n", arguments.get(0));
								client.close(arguments.get(0), arguments.size() == 2 ? arguments.get(1) : null);
								System.out.println("Close success");
							}
							break;

						case "closeall":
							if (parseEmptyCommand(arguments)) {
								System.out.println("Closing all...");
								client.closeAll();
								System.out.println("Closeall success");
							}
							break;

						case "list":
							if (parseEmptyCommand(arguments)) {
								System.out.println("Listing...");
								System.out.println(client.list());
								System.out.println("List success");
							}
							break;

						case "share":
							if (parseTwoArgumentCommand(arguments)) {
								System.out.printf("Sharing %s with %s...%n", arguments.get(0), arguments.get(1));
								client.share(arguments.get(0), arguments.get(1));
								System.out.println("Share success");
							}
							break;

						case "unshare":
							if (parseTwoArgumentCommand(arguments)) {
								System.out.printf("Sharing %s with %s...%n", arguments.get(0), arguments.get(1));
								client.unshare(arguments.get(0), arguments.get(1));
								System.out.println("Share success");
							}
							break;

						default:
							System.out.println("Unknown command, use 'help' to get a list of commands");
					}
				} catch (ClientException e) {
					System.out.println(e.getMessage());
				} catch (ClientAggregateException e) {
					System.out.println(e.getAggregatedMessage());
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			client.shutdown();
		}
	}
}
