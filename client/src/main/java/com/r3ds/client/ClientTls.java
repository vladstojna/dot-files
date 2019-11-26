package com.r3ds.client;

import com.google.common.hash.Hashing;
import com.google.protobuf.ByteString;
import com.r3ds.AuthServiceGrpc;
import com.r3ds.FileTransferServiceGrpc;
import com.r3ds.Common.Credentials;
import com.r3ds.FileTransfer.Chunk;
import com.r3ds.FileTransfer.DownloadRequest;
import com.r3ds.FileTransfer.UploadData;
import com.r3ds.FileTransfer.UploadResponse;
import com.r3ds.PingServiceGrpc;
import com.r3ds.Ping.PingRequest;
import com.r3ds.Ping.PingResponse;
import com.r3ds.client.exception.ClientAggregateException;
import com.r3ds.client.exception.ClientException;

import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.crypto.SecretKey;
import javax.net.ssl.SSLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** TODO:
 * - list remote files
 * - ability to add directories (recursively if possible)
 * - when closing file, upload it
 * - delete-local & delete commands
 * - password length, special characters and numbers verification
 */

/**
 * A simple client that requests a greeting from the {@link HelloWorldServerTls}
 * with TLS.
 */
public class ClientTls {
	private static final Logger logger = LoggerFactory.getLogger(ClientTls.class);

	private final ManagedChannel channel;
	private final PingServiceGrpc.PingServiceBlockingStub blockingStub;
	private final AuthServiceGrpc.AuthServiceBlockingStub authBlockingStub;
	private final FileTransferServiceGrpc.FileTransferServiceBlockingStub downloadBlockingStub;
	private final FileTransferServiceGrpc.FileTransferServiceStub uploadStub;

	private int BUFFER_SIZE;
	private Path SYSTEM_PATH;

	// this users unique symmetric key used to cipher their documents
	private SecretKey symmetricKey;

	// username and password hash are stored for further authentication
	private String username;
	private String passwordHash;

	private boolean isLoggedIn;

	private CryptoTools cryptoHelper;

	// maintains a set of opened files
	private Set<String> openFiles;

	/**
	 * Builds SSL context
	 * @param trustCertCollectionFilePath
	 * @return SSL context
	 * @throws SSLException if unable to build SSL context
	 */
	private static SslContext getSslContext(String trustCertCollectionFilePath) throws SSLException {
		return SslContextBuilder
			.forClient()
			.applicationProtocolConfig(
				new ApplicationProtocolConfig(
					ApplicationProtocolConfig.Protocol.ALPN,
					ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
					ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
					ApplicationProtocolNames.HTTP_2))
			.trustManager(new File(trustCertCollectionFilePath)).build();
	}

	/**
	 * Constructor for client
	 * @param host
	 * @param port
	 * @param trustCertCollectionFilePath
	 * @throws SSLException if unable to build SSL context
	 * @throws ClientException
	 */
	public ClientTls(String host, int port, String trustCertCollectionFilePath) throws SSLException, ClientException {
		this(NettyChannelBuilder
			.forAddress(host, port)
			.sslContext(getSslContext(trustCertCollectionFilePath))
			.build());
	}

	/**
	 * Constructor for client
	 * @param channel
	 * @throws ClientException
	 */
	private ClientTls(ManagedChannel channel) throws ClientException {
		this.channel = channel;
		this.blockingStub = PingServiceGrpc.newBlockingStub(channel);
		this.authBlockingStub = AuthServiceGrpc.newBlockingStub(channel);
		this.downloadBlockingStub = FileTransferServiceGrpc.newBlockingStub(channel);
		this.uploadStub = FileTransferServiceGrpc.newStub(channel);
		this.cryptoHelper = new CryptoTools();
		this.openFiles = new HashSet<>();
		setLoggedOut();
		loadConfig();
		addShutdownHook();
	}

	/**
	 * Loads config.properties
	 * @throws ClientException when resource does not exist or is unable to load a resource
	 */
	private void loadConfig() throws ClientException {
		try {
			String rsrcName = "config.properties";
			InputStream input = ClientTls.class.getClassLoader().getResourceAsStream(rsrcName);
			if (input == null)
				throw new ClientException(String.format("Could not find resource '%s'", rsrcName));
			Properties prop = new Properties();
			prop.load(input);
			BUFFER_SIZE = Integer.parseInt(prop.getProperty("buffer.size"));
			SYSTEM_PATH = Paths.get(System.getProperty("user.home"), prop.getProperty("r3ds.path"));
		} catch (IOException e) {
			throw new ClientException(String.format("Could not load properties: %s", e.getMessage()));
		}
	}

	/**
	 * Closes all files, logs out and exits if the JVM shuts down
	 */
	private void addShutdownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				try {
					System.err.println("\n*** shutting down client");
					System.err.println("Closing all files and logging out");
					ClientTls.this.exit();
				} catch (ClientAggregateException e) {
					System.err.println(e.getAggregatedMessage());
				} finally {
					System.err.println("*** done");
				}
				
			}
		});
	}

	private void checkPassword(char[] password) throws ClientException {
		if (password.length < 1) // placeholder, for testing
			throw new ClientException("Password length must be at least 1 character");
	}

	/**
	 * Shuts down client
	 * @throws InterruptedException
	 */
	public void shutdown() throws InterruptedException {
		channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
	}

	/**
	 * Exits the application
	 * @throws ClientException
	 */
	public void exit() throws ClientAggregateException {
		try {
			justCloseAll();
		} finally {
			setLoggedOut();
		}
	}

	/**
	 * Ping server
	 * @param message
	 * @throws ClientException
	 */
	public void ping(String message) throws ClientException {
		logger.info("Request: {}", message);
		PingRequest request = PingRequest.newBuilder().setMessage(message).build();
		PingResponse response;
		try {
			response = blockingStub.ping(request);
		} catch (StatusRuntimeException e) {
			logger.warn("RPC failed: {}", e.getStatus());
			throw new ClientException(e.getMessage());
		}
		logger.info("Response: {}", response.getMessage());
	}
	
	/**
	 * Signs up the client
	 * @param username
	 * @param password
	 * @throws ClientException when signup is unsucessful or if a user is logged in
	 */
	public void signup(String username, char[] password, char[] pwAgain) throws ClientException {
		try {
			if (isLoggedIn)
				throw new ClientException(String.format("Logged in as '%s', logout first", this.username));
			if (!Arrays.equals(password, pwAgain))
				throw new ClientException("Passwords do not match");
			checkPassword(password);

			logger.info("Request: Signup with username '{}'", username);
			Credentials request = Credentials.newBuilder()
					.setUsername(username)
					.setPassword(Hashing.sha256()
						.hashString(CharBuffer.wrap(password) , StandardCharsets.UTF_8)
						.toString())
					.build();
			authBlockingStub.signup(request);
			logger.info("Signup successful");
		} catch (StatusRuntimeException e) {
			logger.warn("Signup failed: {}", e.getMessage());
			throw new ClientException(e.getMessage());
		} finally {
			CryptoTools.clear(password);
			CryptoTools.clear(pwAgain);
		}
	}

	/**
	 * Updates state so that client is logged in
	 * @param username
	 * @param pw
	 * @param key
	 */
	private void setLoggedIn(String username, String pw, SecretKey key) {
		this.username = username;
		this.passwordHash = pw;
		this.symmetricKey = key;
		this.isLoggedIn = true;
	}

	/**
	 * Updates state so that nobody is logged in
	 * @throws ClientException when destruction of key is unsuccessful
	 */
	private void setLoggedOut() {
		// Should destroy the key, but destroy() is not overriden
		// by a password-derived key?
		/*
		if (this.symmetricKey != null) {
			this.symmetricKey.destroy();
			if (this.symmetricKey.isDestroyed())
				this.symmetricKey = null;
		}
		*/
		this.symmetricKey = null;
		this.username = null;
		this.passwordHash = null;
		this.isLoggedIn = false;
	}

	/**
	 * Authenticates a user on the server
	 * @param username
	 * @param password
	 * @throws ClientException when authentication fails or a user is logged in
	 */
	public void login(String username, char[] password) throws ClientException {
		try {
			if (isLoggedIn)
				throw new ClientException(String.format("Logged in as '%s', logout first", this.username));

			SecretKey key = cryptoHelper.deriveKey(password,
				Hashing.sha256()
					.hashString(username, StandardCharsets.UTF_8)
					.asBytes());
			String passwordToServer = Hashing.sha256()
				.hashString(CharBuffer.wrap(password), StandardCharsets.UTF_8)
				.toString();

			logger.info("Request: Login with username '{}'", username);
			Credentials request = Credentials.newBuilder()
					.setUsername(username)
					.setPassword(passwordToServer)
					.build();
			authBlockingStub.login(request);
			setLoggedIn(username, passwordToServer, key);
			logger.info("Login successful");
		} catch (StatusRuntimeException e) {
			logger.warn("Login failed: {}", e.getMessage());
			throw new ClientException(e.getMessage());
		} finally {
			CryptoTools.clear(password);
		}
	}
	
	/**
	 * Logs a user out
	 * @throws ClientException when the user is already logged out
	 */
	public void logout() throws ClientException, ClientAggregateException {
		if (!isLoggedIn)
			throw new ClientException("Not logged in");

		try {
			justCloseAll();
		} finally {
			setLoggedOut();
		}
	}

	/**
	 * Attempt to download a file
	 * @param filename name of file to download
	 * @throws ClientException if user is not logged in or download failed
	 */
	public void download(String filename) throws ClientException {
		if (!isLoggedIn)
			throw new ClientException("Not logged in");

		BufferedOutputStream writer = null;
		try {

			Path userPath = Paths.get(SYSTEM_PATH.toString(), this.username);
			// first time downloading user-wide
			if (!Files.isDirectory(userPath)) {
				logger.info("Directory for user '{}' does not exist, creating one", this.username);
				Files.deleteIfExists(userPath);
				Files.createDirectories(userPath);
			}

			logger.info("Request: download file '{}' to '{}'", filename, userPath);

			DownloadRequest request = DownloadRequest.newBuilder()
				.setCredentials(
					Credentials.newBuilder()
						.setUsername(this.username)
						.setPassword(this.passwordHash))
				.setFilename(filename)
				.build();

			Iterator<Chunk> content = downloadBlockingStub.download(request);

			File destinationPath = Paths.get(userPath.toString(), filename).toFile();
			writer = new BufferedOutputStream(new FileOutputStream(destinationPath));
			while (content.hasNext()) {
				writer.write(content.next().getContent().toByteArray());
			}
			writer.flush();
			logger.info("Download of '{}' to '{}' successful", filename, userPath);

		} catch (StatusRuntimeException e) {
			logger.warn("Download failed: {}", e.getMessage());
			throw new ClientException("Download failed: " + e.getMessage());
		} catch (IOException e) {
			logger.error("Error downloading file", e);
			throw new ClientException(e.getMessage());
		} finally {
			if (writer != null) {
				try {
					writer.close();
				} catch (IOException e) {
					logger.error("Error cleaning up: {}", e.getMessage());
					throw new ClientException("Unable to cleanup after error", e);
				}
			}
		}
	}

	/**
	 * Attemps to upload a file
	 * @param filename
	 * @throws InterruptedException
	 * @throws ClientException if user is not logged in or upload failed
	 */
	public void upload(String filename) throws InterruptedException, ClientException {
		if (!isLoggedIn)
			throw new ClientException("Not logged in");

		// check if file with said name exists as regular file
		Path userPath = Paths.get(SYSTEM_PATH.toString(), this.username);
		Path filePath = Paths.get(userPath.toString(), filename);
		if (!Files.isRegularFile(filePath))
			throw new ClientException(String.format("%s does not exist or is not a file", filePath));

		final CountDownLatch finishLatch = new CountDownLatch(1);
		final AtomicBoolean errorHappened = new AtomicBoolean(false);

		StreamObserver<UploadResponse> responseObserver = new StreamObserver<UploadResponse>() {
			@Override
			public void onNext(UploadResponse response) {
				logger.info("Response for file upload '{}' received", filePath);
			}

			@Override
			public void onError(Throwable t) {
				logger.warn("Error uploading file to server: {}", t.getMessage());
				finishLatch.countDown();
				errorHappened.set(true);
			}

			@Override
			public void onCompleted() {
				logger.info("Finished uploading file '{}'", filePath);
				finishLatch.countDown();
			}
		};

		StreamObserver<UploadData> requestObserver = uploadStub.upload(responseObserver);
		BufferedInputStream reader = null;
		try {
			reader = new BufferedInputStream(new FileInputStream(filePath.toFile()));
			byte[] buffer = new byte[BUFFER_SIZE];
			int read;

			logger.info("Started uploading file '{}'", filePath);

			Credentials creds = Credentials.newBuilder()
				.setUsername(this.username)
				.setPassword(this.passwordHash)
				.build();

			while ((read = reader.read(buffer)) != -1) {
				requestObserver.onNext(UploadData.newBuilder()
					.setCredentials(creds)
					.setFilename(filePath.toFile().getName())
					.setContent(ByteString.copyFrom(buffer, 0, read))
					.build()
				);
				// RPC completed or errored before sending finished
				if (finishLatch.getCount() == 0) {
					throw new ClientException(String.format("Unable to upload %s successfully", filename));
				}
			}

		} catch (IOException e) {
			logger.error("Could not read file: {}", e.getMessage());
			requestObserver.onError(e);
			throw new ClientException(e.getMessage());
		} catch (StatusRuntimeException e) {
			logger.warn("Upload failed: {}", e.getMessage());
			requestObserver.onError(e);
			throw new ClientException(String.format("Upload failed: %s", e.getMessage()));
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					logger.error("Error cleaning up: {}", e.getMessage());
					throw new ClientException("Unable to cleanup after error", e);
				}
			}
		}

		requestObserver.onCompleted();
		// if RPC errored after finished sending data
		if (errorHappened.get() == true) {
			throw new ClientException(String.format("Unable to upload %s successfully", filename));
		}
		// response is received asynchronously
		finishLatch.await();
	}

	/**
	 * Adds a local file to the system
	 * @param pathName path to the local file
	 * @throws ClientException if user is not logged in, file does not exist or failed adding
	 */
	public void add(String pathName) throws ClientException {
		if (!isLoggedIn)
			throw new ClientException("Not logged in");

		Path path = Paths.get(pathName);
		if (!Files.isRegularFile(path))
			throw new ClientException(String.format("%s does not exist or is not a file", path));

		try {

			Path userPath = Paths.get(SYSTEM_PATH.toString(), this.username);
			if (!Files.isDirectory(userPath)) {
				logger.info("Directory for user '{}' does not exist, creating one", this.username);
				Files.deleteIfExists(userPath);
				Files.createDirectories(userPath);
			}
			String outPath = Paths.get(userPath.toString(), path.getFileName().toString()).toString();
			cryptoHelper.encrypt(pathName, outPath, this.symmetricKey);

		} catch (FileNotFoundException e) {
			logger.warn(e.getMessage());
			throw new ClientException(e.getMessage());
		} catch (IOException e) {
			logger.error("Error adding file: ", e.getMessage());
			throw new ClientException(e.getMessage());
		}
	}

	/**
	 * "Opens" a file, i.e. decrypts its contents
	 * @param filename name of file to encrypt
	 * @throws ClientException if not logged in, file does not exist,
	 * already opened previously or decryption failed
	 */
	public void open(String filename) throws ClientException {
		if (!isLoggedIn)
			throw new ClientException("Not logged in");

		// check if file with said name exists as regular file
		Path userPath = Paths.get(SYSTEM_PATH.toString(), this.username);
		Path filePath = Paths.get(userPath.toString(), filename);
		if (!Files.isRegularFile(filePath))
			throw new ClientException(String.format("%s does not exist or is not a file", filePath));

		Path outPath = null;
		try {

			if (openFiles.contains(filename)) {
				throw new ClientException(String.format("File '%s' is already decrypted", filename));
			}

			outPath = Paths.get(userPath.toString(), filename + "_");
			cryptoHelper.decrypt(filePath.toString(), outPath.toString(), this.symmetricKey);
			Files.move(outPath, filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

			openFiles.add(filename);
			logger.info("Successfully decrypted file '{}'", filename);

		} catch (FileNotFoundException e) {
			logger.warn(e.getMessage());
			throw new ClientException(e.getMessage());
		} catch (IOException e) {
			logger.error("Error opening file: {}", e.getMessage());
			throw new ClientException(e.getMessage());
		} catch (SignatureException e) {
			logger.error("Error opening file: {}", e.getMessage());
			throw new ClientException("File is corrupted");
		} catch (GeneralSecurityException e) {
			logger.error(e.getMessage());
			throw new ClientException("Error decrypting file: incorrect secret key or file is corrupted");
		} finally {
			if (outPath != null) {
				try {
					Files.deleteIfExists(outPath);
				} catch (IOException e) {
					logger.error("Error cleaning up file: {}", e.getMessage());
					throw new ClientException(String.format("Could not cleanup after error: %s", e.getMessage()));
				}
			}
		}
	}

	/**
	 * Only closes the file, does not modify the opened files structure
	 * @param filename
	 * @throws ClientException
	 */
	private void justClose(String filename) throws ClientException {
		Path outPath = null;
		try {
			// check if file with said name exists as regular file
			Path userPath = Paths.get(SYSTEM_PATH.toString(), this.username);
			Path filePath = Paths.get(userPath.toString(), filename);
			if (!Files.isRegularFile(filePath))
				throw new ClientException(String.format("%s does not exist or is not a file", filePath));

			outPath = Paths.get(userPath.toString(), filename + "_");
			cryptoHelper.encrypt(filePath.toString(), outPath.toString(), this.symmetricKey);
			Files.move(outPath, filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			logger.info("Successfully encrypted file '{}'", filename);

		} catch (FileNotFoundException e) {
			logger.warn(e.getMessage());
			throw new ClientException(e.getMessage());
		} catch (IOException e) {
			logger.error("Error closing file: {}", e.getMessage());
			throw new ClientException(e.getMessage());
		} finally {
			if (outPath != null) {
				try {
					Files.deleteIfExists(outPath);
				} catch (IOException e) {
					logger.error("Error cleaning up file: {}", e.getMessage());
					throw new ClientException(String.format("Could not cleanup after error: %s", e.getMessage()));
				}
			}
		}
	}

	/**
	 * "Closes" a file, i.e. encrypts its contents
	 * @param filename name of file to encrypt
	 * @throws ClientException
	 */
	public void close(String filename) throws ClientException {
		if (!isLoggedIn)
			throw new ClientException("Not logged in");

		if (openFiles.isEmpty())
			throw new ClientException("No open files");
		if (!openFiles.contains(filename))
			throw new ClientException(String.format("File '%s' is already encrypted", filename));
		justClose(filename);
		openFiles.remove(filename);
	}

	/**
	 * Closes all files
	 * @throws ClientException if unable to close a file
	 */
	private void justCloseAll() throws ClientAggregateException {
		List<Throwable> exceptions = new ArrayList<>();
		Iterator<String> it = this.openFiles.iterator();
		while (it.hasNext()) {
			String filename = it.next();
			try {
				justClose(filename);
			} catch (ClientException e) {
				logger.warn("Error while closing all files: {}", e.getMessage());
				exceptions.add(e);
			} finally {
				it.remove();
			}
		}
		if (!exceptions.isEmpty()) {
			throw new ClientAggregateException(
				String.format("Some files were not properly closed, manually check path %s",
					Paths.get(SYSTEM_PATH.toString(), this.username)),
				exceptions);
		}
	}

	/**
	 * Closes all
	 * @throws ClientException if not logged in, no opened files or unable to close a file
	 */
	public void closeAll() throws ClientException, ClientAggregateException {
		if (!isLoggedIn)
			throw new ClientException("Not logged in");
		if (this.openFiles.isEmpty())
			throw new ClientException("No open files");
		justCloseAll();
	}

	/**
	 * Lists all client files
	 * @return formatted string with file information
	 * @throws ClientException
	 */
	public String list() throws ClientException {
		if (!isLoggedIn)
			throw new ClientException("Not logged in");

		try {
			StringBuilder sb = new StringBuilder();
			Path userPath = Paths.get(SYSTEM_PATH.toString(), this.username);
			String separator = new String(new char[106]).replace('\0', '-');
			if (Files.isDirectory(userPath)) {
				sb.append(String.format(
					"%-30s| %-10s| %-8s| %-30s| %-20s",
					"Name", "Type", "State", "Modification Date", "Size (bytes)"))
				.append('\n')
				.append(separator);
				Iterator<Path> it = Files.list(userPath).iterator();
				while (it.hasNext()) {
					sb.append('\n');
					Path next = it.next();
					String toAppend = String.format(
						"%-30s| %-10s| %-8s| %-30s| %-20s",
						next.getFileName().toString(),
						"LOCAL",
						this.openFiles.contains(next.getFileName().toString()) ? "OPEN" : "CLOSED",
						Files.getLastModifiedTime(next),
						Files.size(next)
					);
					sb.append(toAppend);
				}
			}
			sb.append('\n').append(separator);
			return sb.toString();
		} catch (IOException e) {
			logger.error("Unable to list files: {}", e);
			throw new ClientException("Unable to list files: " + e.getMessage());
		}
	}
}
