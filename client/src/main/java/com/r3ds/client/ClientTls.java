package com.r3ds.client;

import com.google.common.hash.Hashing;
import com.google.protobuf.ByteString;
import com.r3ds.AuthServiceGrpc;
import com.r3ds.CertificateServiceGrpc;
import com.r3ds.FileTransferServiceGrpc;
import com.r3ds.Common.Credentials;
import com.r3ds.Common.FileData;
import com.r3ds.Common.Chunk;
import com.r3ds.FileTransfer.DownloadRequest;
import com.r3ds.FileTransfer.ListResponse;
import com.r3ds.FileTransfer.UploadData;
import com.r3ds.FileTransfer.UploadResponse;
import com.r3ds.PingServiceGrpc;
import com.r3ds.ShareFileServiceGrpc;
import com.r3ds.Certification.CertificateRequest;
import com.r3ds.Certification.CertificateResponse;
import com.r3ds.Certification.CertificateSignatureRequest;
import com.r3ds.Ping.PingRequest;
import com.r3ds.Ping.PingResponse;
import com.r3ds.ShareFile.FileDataWithSharedKey;
import com.r3ds.ShareFile.GetFilesToShareResponse;
import com.r3ds.ShareFile.ShareRequest;
import com.r3ds.ShareFile.UnshareRequest;
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
import java.security.KeyPair;
import java.security.SignatureException;
import java.security.cert.Certificate;
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

final class FileInfo {
	private String name;
	private String owner;

	public FileInfo(String name, String owner) {
		this.name = name;
		this.owner = owner;
	}

	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @return the owner
	 */
	public String getOwner() {
		return owner;
	}

	@Override
	public boolean equals(Object other) {
		if (other == null)
			return false;
		FileInfo fi = (FileInfo)other;
		return name.equals(fi.getName()) &&
			owner == null ? owner == fi.getOwner() : owner.equals(fi.getOwner());
	}

	@Override
	public int hashCode() {
		return (name.hashCode() << 1) + (owner == null ? 0 : owner.hashCode());
	}
}

/**
 * A simple client that requests a greeting from the {@link HelloWorldServerTls}
 * with TLS.
 */
public class ClientTls {

	private static AtomicBoolean BACKUP = new AtomicBoolean(true);

	private static final Logger logger = LoggerFactory.getLogger(ClientTls.class);

	private final Object loginLock = new Object();

	private final ManagedChannel serverChannel;
	private final ManagedChannel caChannel;
	private final PingServiceGrpc.PingServiceBlockingStub pingServerBlockingStub;
	private final PingServiceGrpc.PingServiceBlockingStub pingCaBlockingStub;
	private final CertificateServiceGrpc.CertificateServiceBlockingStub certBlockingStub;
	private final AuthServiceGrpc.AuthServiceBlockingStub authBlockingStub;
	private final ShareFileServiceGrpc.ShareFileServiceBlockingStub shareBlockingStub;
	private final FileTransferServiceGrpc.FileTransferServiceBlockingStub fileTransferBlockingStub;
	private final FileTransferServiceGrpc.FileTransferServiceStub fileTransferStub;

	private Certificate rootCertificate;

	private int POLLING_INTERVAL;
	private int BUFFER_SIZE;
	private Path SYSTEM_PATH;
	private Path SHARED_PATH;

	// this users unique symmetric key used to cipher their documents
	private SecretKey symmetricKey;

	// this users unique key-pair used to share per-document keys with other users
	private KeyPair keyPair;

	// username and password hash are stored for further authentication
	private String username;
	private String passwordHash;

	private boolean isLoggedIn;

	private CryptoTools cryptoHelper;

	// maintains a set of opened files
	private Set<FileInfo> openFiles;

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
	 * Builds a secure channel
	 * @param host
	 * @param port
	 * @param trustCertCollectionFilePath
	 * @return
	 * @throws SSLException
	 * @throws ClientException
	 */
	private static ManagedChannel buildChannel(String host, int port, String trustCertCollectionFilePath) throws SSLException, ClientException {
		return NettyChannelBuilder
			.forAddress(host, port)
			.sslContext(getSslContext(trustCertCollectionFilePath))
			.build();
	}

	/**
	 * Constructor for client
	 * @param host
	 * @param port
	 * @param trustCertCollectionFilePath
	 * @throws SSLException if unable to build SSL context
	 * @throws ClientException
	 */
	public ClientTls(String host, int port, String caHost, int caPort, String trustCertCollectionFilePath)
			throws SSLException, ClientException, GeneralSecurityException, FileNotFoundException {
		this(buildChannel(host, port, trustCertCollectionFilePath),
			buildChannel(caHost, caPort, trustCertCollectionFilePath));
		this.rootCertificate = CryptoTools.getCertificate(trustCertCollectionFilePath);
	}

	/**
	 * Constructor for client
	 * @param channel
	 * @throws ClientException
	 */
	private ClientTls(ManagedChannel serverChannel, ManagedChannel caChannel) throws ClientException {
		this.serverChannel = serverChannel;
		this.caChannel = caChannel;

		this.pingServerBlockingStub = PingServiceGrpc.newBlockingStub(serverChannel);
		this.pingCaBlockingStub = PingServiceGrpc.newBlockingStub(caChannel);

		this.certBlockingStub = CertificateServiceGrpc.newBlockingStub(caChannel);
		this.authBlockingStub = AuthServiceGrpc.newBlockingStub(serverChannel);
		this.shareBlockingStub = ShareFileServiceGrpc.newBlockingStub(serverChannel);
		this.fileTransferBlockingStub = FileTransferServiceGrpc.newBlockingStub(serverChannel);
		this.fileTransferStub = FileTransferServiceGrpc.newStub(serverChannel);
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
			POLLING_INTERVAL = Integer.parseInt(prop.getProperty("polling.interval"));
			SYSTEM_PATH = Paths.get(System.getProperty("user.home"), prop.getProperty("r3ds.path"));
			SHARED_PATH = Paths.get(prop.getProperty("r3ds.shared.path"));
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
		serverChannel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
		caChannel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
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
			response = pingServerBlockingStub.ping(request);
			logger.info("Response: {}", response.getMessage());
			response = pingCaBlockingStub.ping(request);
			logger.info("Response: {}", response.getMessage());
		} catch (StatusRuntimeException e) {
			logger.warn("RPC failed: {}", e.getStatus());
			throw new ClientException(e.getMessage());
		}
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

			Credentials request = Credentials.newBuilder()
					.setUsername(username)
					.setPassword(Hashing.sha256()
						.hashString(CharBuffer.wrap(password) , StandardCharsets.UTF_8)
						.toString())
					.build();

			authBlockingStub.signup(request);
			logger.info("Signup successful");

			/* login and ask to get a signed certificate */

			login(username, password);

			CertificateSignatureRequest certSignReq = CertificateSignatureRequest.newBuilder()
				.setCommonName("localhost")
				.setUsername(username)
				.setPublicKey(ByteString.copyFrom(this.keyPair.getPublic().getEncoded()))
				.build();

			certBlockingStub.sign(certSignReq);

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
	private void setLoggedIn(String username, String pw, SecretKey key, KeyPair keyPair) {
		synchronized(loginLock) {
			this.username = username;
			this.passwordHash = pw;
			this.symmetricKey = key;
			this.keyPair = keyPair;
			this.isLoggedIn = true;
		}
	}

	/**
	 * Updates state so that nobody is logged in
	 * @throws ClientException when destruction of key is unsuccessful
	 */
	private void setLoggedOut() {
		synchronized (loginLock) {
			// Should destroy the key, but destroy() is not overriden
			// by a password-derived key?
			this.symmetricKey = null;
			this.keyPair = null;
			this.username = null;
			this.passwordHash = null;
			this.isLoggedIn = false;
		}
	}

	/**
	 * Authenticates a user on the server
	 * @param username
	 * @param password
	 * @throws ClientException when authentication fails or a user is logged in
	 */
	public void login(String username, char[] password) throws ClientException {
		byte[] pwAsByteArray = null;
		try {

			if (isLoggedIn)
				throw new ClientException(String.format("Logged in as '%s', logout first", this.username));

			/* derive keys */
			byte[] salt = Hashing.sha256().hashString(username, StandardCharsets.UTF_8).asBytes();
			SecretKey key = cryptoHelper.deriveKey(password, salt);
			KeyPair keyPair = cryptoHelper.deriveKeyPair(key);

			/* generate password to send to server */
			pwAsByteArray = CryptoTools.toBytes(password);
			String passwordToServer = Hashing.sha256().hashBytes(pwAsByteArray).toString();

			Credentials request = Credentials.newBuilder()
				.setUsername(username)
				.setPassword(passwordToServer)
				.build();

			authBlockingStub.login(request);
			setLoggedIn(username, passwordToServer, key, keyPair);

			startSharedCheckerThread();

		} catch (StatusRuntimeException e) {
			logger.warn("Login failed: {}", e.getMessage());
			throw new ClientException(e.getMessage(), e);
		} finally {
			CryptoTools.clear(password);
			if (pwAsByteArray != null)
				CryptoTools.clear(pwAsByteArray);
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

	private Path getDestinationPath(String owner) {
		if (owner == null)
			return Paths.get(SYSTEM_PATH.toString(), this.username);
		return Paths.get(SYSTEM_PATH.toString(), this.username, SHARED_PATH.toString(), owner);
	}

	/**
	 * Attempt to download a file
	 * @param filename name of file to download
	 * @param owner owner of this file
	 * @throws ClientException if user is not logged in or download failed
	 */
	public void download(String filename, String owner) throws ClientException {
		if (!isLoggedIn)
			throw new ClientException("Not logged in");

		BufferedOutputStream writer = null;
		try {
			DownloadRequest request = DownloadRequest.newBuilder()
				.setCredentials(
					Credentials.newBuilder()
						.setUsername(this.username)
						.setPassword(this.passwordHash))
				.setFile(
					FileData.newBuilder()
						.setFilename(filename)
						.setOwnerUsername(owner == null ? this.username : owner)
						.setShared(owner == null ? false : true))
				.build();
			Iterator<Chunk> content = fileTransferBlockingStub.download(request);

			Path destDirectory = getDestinationPath(owner);
			// first time downloading user-wide
			if (!Files.isDirectory(destDirectory)) {
				logger.info("Directory '{}' does not exist, creating one", destDirectory);
				Files.deleteIfExists(destDirectory);
				Files.createDirectories(destDirectory);
			}

			File destinationPath = Paths.get(destDirectory.toString(), filename).toFile();
			writer = new BufferedOutputStream(new FileOutputStream(destinationPath));
			while (content.hasNext()) {
				writer.write(content.next().getContent().toByteArray());
			}
			writer.flush();
			logger.info("Download of '{}' to '{}' successful", filename, destinationPath);

		} catch (StatusRuntimeException e) {
			logger.warn("Download failed: {}", e.getMessage());
			throw new ClientException(e.getMessage(), e);
		} catch (IOException e) {
			logger.error("Error downloading file", e);
			throw new ClientException(e.getMessage(), e);
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
	 * @param owner
	 * @throws InterruptedException
	 * @throws ClientException if user is not logged in or upload failed
	 */
	public void upload(String filename, String owner) throws InterruptedException, ClientException {
		if (!isLoggedIn)
			throw new ClientException("Not logged in");

		// check if file with said name exists as regular file
		Path filePath = Paths.get(getDestinationPath(owner).toString(), filename);
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

		StreamObserver<UploadData> requestObserver = fileTransferStub.upload(responseObserver);
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

			FileData fileData = FileData.newBuilder()
				.setFilename(filename)
				.setOwnerUsername(owner == null ? this.username : owner)
				.setShared(owner == null ? false : true)
				.build();

			while ((read = reader.read(buffer)) != -1) {
				requestObserver.onNext(UploadData.newBuilder()
					.setCredentials(creds)
					.setFile(fileData)
					.setContent(ByteString.copyFrom(buffer, 0, read))
					.build()
				);
				// RPC completed or errored before sending finished
				if (finishLatch.getCount() == 0) {
					throw new ClientException(String.format("Unable to upload %s successfully", filename));
				}
			}

			if (BACKUP.compareAndSet(true, false))
				upload(filename, owner);

		} catch (IOException e) {
			logger.error("Could not read file: {}", e.getMessage());
			requestObserver.onError(e);
			throw new ClientException(e.getMessage(), e);
		} catch (StatusRuntimeException e) {
			logger.warn("Upload failed: {}", e.getMessage());
			requestObserver.onError(e);
			throw new ClientException(String.format("Upload failed: %s", e.getMessage()), e);
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

	private void deleteDirectory(Path path) throws IOException {
		Iterator<Path> contents = Files.list(path).iterator();
		while (contents.hasNext()) {
			deleteDirectory(contents.next());
		}
		Files.delete(path);
	}

	/**
	 * "Opens" a file, i.e. decrypts its contents
	 * @param filename name of file to encrypt
	 * @throws ClientException if not logged in, file does not exist,
	 * already opened previously or decryption failed
	 */
	public void open(String filename, String owner) throws ClientException {
		if (!isLoggedIn)
			throw new ClientException("Not logged in");

		Path tempPath = null;
		try {
			FileInfo fileInfo = new FileInfo(filename, owner == null ? this.username : owner);

			// check if file with said name exists as regular file
			Path destDirectory = getDestinationPath(owner);
			Path destPath = Paths.get(destDirectory.toString(), filename);
			if (!Files.isRegularFile(destPath)) {
				logger.info("{} does not exist or is not a file, downloading from server...", destPath);
				// delete directory recursively it there's a directory
				if (Files.isDirectory(destPath))
					deleteDirectory(destPath);
				// download the file
				download(filename, owner);
			} else if (openFiles.contains(fileInfo)) {
				throw new ClientException(String.format("File '%s' is already decrypted", filename));
			}

			// TODO: download document specific key if file is shared

			tempPath = Paths.get(destDirectory.toString(), filename + "_");
			cryptoHelper.decrypt(destPath.toString(), tempPath.toString(), this.symmetricKey);
			Files.move(tempPath, destPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

			openFiles.add(fileInfo);
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
			// TODO: must download backup here
			throw new ClientException("Error decrypting file: incorrect secret key or file is corrupted");
		} finally {
			if (tempPath != null) {
				try {
					Files.deleteIfExists(tempPath);
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
	private void justClose(String filename, String owner) throws ClientException {
		Path tempPath = null;
		try {
			// check if file with said name exists as regular file
			Path destDirectory = getDestinationPath(owner);
			Path filePath = Paths.get(destDirectory.toString(), filename);
			if (!Files.isRegularFile(filePath))
				throw new ClientException(String.format("%s does not exist or is not a file", filePath));

			tempPath = Paths.get(destDirectory.toString(), filename + "_");
			cryptoHelper.encrypt(filePath.toString(), tempPath.toString(), this.symmetricKey);
			Files.move(tempPath, filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			logger.info("Successfully encrypted file '{}'", filename);

		} catch (FileNotFoundException e) {
			logger.warn(e.getMessage());
			throw new ClientException(e.getMessage(), e);
		} catch (IOException e) {
			logger.error("Error closing file: {}", e.getMessage());
			throw new ClientException(e.getMessage(), e);
		} finally {
			if (tempPath != null) {
				try {
					Files.deleteIfExists(tempPath);
				} catch (IOException e) {
					logger.error("Error cleaning up file: {}", e.getMessage());
					throw new ClientException(String.format("Could not cleanup after error: %s", e.getMessage()), e);
				}
			}
		}
	}

	/**
	 * "Closes" a file, i.e. encrypts its contents
	 * @param filename name of file to encrypt
	 * @throws ClientException
	 */
	public void close(String filename, String owner) throws ClientException {
		if (!isLoggedIn)
			throw new ClientException("Not logged in");
		FileInfo fileInfo = new FileInfo(filename, owner == null ? this.username : owner);
		if (openFiles.isEmpty())
			throw new ClientException("No open files");
		if (!openFiles.contains(fileInfo))
			throw new ClientException(String.format("File '%s' is already encrypted", filename));
		justClose(filename, owner);
		openFiles.remove(fileInfo);
	}

	/**
	 * Closes all files
	 * @throws ClientException if unable to close a file
	 */
	private void justCloseAll() throws ClientAggregateException {
		List<Throwable> exceptions = new ArrayList<>();
		Iterator<FileInfo> it = this.openFiles.iterator();
		while (it.hasNext()) {
			FileInfo fileInfo = it.next();
			try {
				justClose(fileInfo.getName(), fileInfo.getOwner());
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
					"%-30s| %-10s| %-8s| %-15s| %-20s",
					"Name", "Type", "State", "Owner", "Size (bytes)"))
				.append('\n')
				.append(separator);
				Iterator<Path> it = Files.list(userPath).iterator();
				while (it.hasNext()) {
					sb.append('\n');
					Path next = it.next();
					String toAppend = String.format(
						"%-30s| %-10s| %-8s| %-15s| %-20s",
						next.getFileName().toString(),
						"LOCAL",
						this.openFiles.contains(new FileInfo(next.getFileName().toString(), this.username)) ?
							"OPEN" : "CLOSED",
						this.username,
						Files.size(next)
					);
					sb.append(toAppend);
				}
			}
			Path sharedPath = Paths.get(userPath.toString(), SHARED_PATH.toString());
			System.out.println(sharedPath);
			if (Files.isDirectory(sharedPath)) {
				Iterator<Path> it = Files.list(sharedPath).iterator();
				while (it.hasNext()) {
					Path next = it.next();
					System.out.println(next);
					if (Files.isDirectory(next)) {
						Iterator<Path> itInner = Files.list(next).iterator();
						while (itInner.hasNext()) {
							sb.append('\n');
							Path nextInner = itInner.next();
							String toAppend = String.format(
								"%-30s| %-10s| %-8s| %-15s| %-20s",
								nextInner.getFileName().toString(),
								"LOCAL",
								this.openFiles.contains(
									new FileInfo(nextInner.getFileName().toString(), next.getFileName().toString())) ?
									"OPEN" : "CLOSED",
								next.getFileName().toString(),
								Files.size(nextInner)
							);
							sb.append(toAppend);
						}
					}
				}
			}
			ListResponse remoteFilesResponse = fileTransferBlockingStub.listFiles(
				Credentials.newBuilder()
					.setUsername(this.username)
					.setPassword(this.passwordHash)
					.build());
			for (FileData fData : remoteFilesResponse.getFilesList()) {
				sb.append('\n');
					String toAppend = String.format(
						"%-30s| %-10s| %-8s| %-15s| %-20s",
						fData.getFilename(),
						"REMOTE",
						"N/A",
						fData.getOwnerUsername(),
						"N/A"
					);
					sb.append(toAppend);
			}
			sb.append('\n').append(separator);
			return sb.toString();
		} catch (IOException e) {
			logger.error("Unable to list files: {}", e);
			throw new ClientException("Unable to list files: " + e.getMessage());
		}
	}

	public void share(String filename, String username) throws ClientException, InterruptedException {
		if (!isLoggedIn)
			throw new ClientException("Not logged in");

		byte[] keyWrappedWithSecretKey = null;
		byte[] keyWrappedWithPublicKey = null;
		SecretKey documentKey = null;
		SecretKey oldKey = null;

		try {
			// obtain user certificate
			CertificateRequest certReq = CertificateRequest.newBuilder()
				.setUsername(username)
				.build();
			CertificateResponse certResponse = certBlockingStub.retrieve(certReq);
			Certificate userCert = CryptoTools.getCertificate(certResponse.getCertificate().toByteArray());
			CryptoTools.verifyCertificate(userCert, CryptoTools.getPublicKey(this.rootCertificate));

			documentKey = cryptoHelper.generateKey();

			open(filename, null);
			// hack: swap main key with document key just during this function
			oldKey = this.symmetricKey;
			this.symmetricKey = documentKey;
			close(filename, null);
			this.symmetricKey = oldKey;
			upload(filename, null);

			// build share request
			Credentials creds = Credentials.newBuilder()
				.setUsername(this.username)
				.setPassword(this.passwordHash)
				.build();

			FileData fileData = FileData.newBuilder()
				.setFilename(filename)
				.setOwnerUsername(this.username)
				.setShared(false)
				.build();

			keyWrappedWithSecretKey = cryptoHelper.wrapKey(documentKey, this.symmetricKey);
			keyWrappedWithPublicKey = cryptoHelper.wrapKey(documentKey, userCert);

			ShareRequest shareReq = ShareRequest.newBuilder()
				.setCredentials(creds)
				.setFile(fileData)
				.setReceivingUsername(username)
				.setSharedKeyFromSendingUser(ByteString.copyFrom(keyWrappedWithSecretKey))
				.setSharedKeyFromReceivingUser(ByteString.copyFrom(keyWrappedWithPublicKey))
				.build();

			shareBlockingStub.shareFile(shareReq);

		} catch (StatusRuntimeException e) {
			logger.warn("Share failed: {}", e.getMessage());
			// should rollback here but it won't be considered for this project
			throw new ClientException(String.format("Share failed: %s", e.getMessage()), e);
		} catch (GeneralSecurityException e) {
			logger.error("Certificate verification for user '{}' failed", username);
			throw new ClientException(e.getMessage(), e);
		} catch (ClientException e) {
			// should rollback here
			throw e;
		} finally {
			if (keyWrappedWithSecretKey != null)
				CryptoTools.clear(keyWrappedWithSecretKey);
			if (keyWrappedWithPublicKey != null)
				CryptoTools.clear(keyWrappedWithPublicKey);
			// ideally should destroy the keys
			documentKey = null;
			oldKey = null;
		}

	}

	public void unshare(String filename, String username) throws ClientException {
		if (!isLoggedIn)
			throw new ClientException("Not logged in");

		try {
			UnshareRequest unshareReq = UnshareRequest.newBuilder()
				.setCredentials(Credentials.newBuilder()
					.setUsername(this.username)
					.setPassword(this.passwordHash)
					.build())
				.setRejectedUsername(username)
				.setFile(FileData.newBuilder()
					.setFilename(filename)
					.setOwnerUsername(this.username)
					.setShared(false))
				.build();

			shareBlockingStub.unshareFile(unshareReq);
		} catch (StatusRuntimeException e) {
			logger.warn("Un-share failed: {}", e.getMessage());
			throw new ClientException(String.format("Un-share failed: %s", e.getMessage()), e);
		}
	}

	private void startSharedCheckerThread() {
		Runnable r = new Runnable(){
			@Override
			public void run() {
				checkShared();
			}
		};
		new Thread(r).start();
	}

	private void checkShared() {
		while (true) {
			try {
				if (isLoggedIn) {
					Credentials creds = null;
					synchronized (loginLock) {
						if (!isLoggedIn)
							continue;
						creds = Credentials.newBuilder()
							.setUsername(this.username)
							.setPassword(this.passwordHash)
							.build();
					}
					GetFilesToShareResponse response = shareBlockingStub.getFilesToShare(creds);
					if (response.getFilesCount() > 0)
						System.out.println(sharedNotificationMessage(response.getFilesList()));
				}
				Thread.sleep(1000 * POLLING_INTERVAL);
			} catch (InterruptedException e) {
				logger.warn("Shared check interrupted!");
			}
		}
	}

	private String sharedNotificationMessage(List<FileDataWithSharedKey> files) {
		StringBuilder sb = new StringBuilder();
		String separator = new String(new char[15]).replace('\0', '-');
		sb.append('\n')
			.append(separator)
			.append("Shared Notification Start")
			.append(separator)
			.append('\n');
		for (FileDataWithSharedKey data : files) {
			sb.append(data.getFile().getFilename())
				.append(" shared with you by ")
				.append(data.getFile().getOwnerUsername())
				.append('\n');
		}
		sb.append(separator).append("-Shared Notification End-").append(separator);
		return sb.toString();
	}

}
