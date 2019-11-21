package com.r3ds.client;

import com.google.common.hash.HashCode;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.net.ssl.SSLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

	// PBEKeySpec does not permit an empty salt
	// Sadly we cannot use a random salt due to the key being generated
	// for the same account multiple times (e.g. per startup)
	private final String keyDerivationAlgo = "PBKDF2WithHmacSHA512";
	private final int iterations = 1024;
	private final int keyLen = 256;

	private final String encryptionAlgo = "AES_256";

	private final int BUFFER_SIZE = 4096;

	// this users unique symmetric key used to cipher their documents
	private Key symmetricKey;

	// username and password hash are stored for further authentication
	private String username;
	private String passwordHash;

	private boolean isLoggedIn;

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

	private static Key deriveKey(char[] pw, byte[] salt, int iterations, int keyLen, String kdAlgo) {
		PBEKeySpec spec = new PBEKeySpec(pw, salt, iterations, keyLen);
		try {
			SecretKeyFactory kf = SecretKeyFactory.getInstance(kdAlgo);
			Key key = kf.generateSecret(spec);
			return key;
		} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
			throw new AssertionError("Error deriving key", e);
		} finally {
			spec.clearPassword();
		}
	}

	private Key deriveKey(String password, String username) {
		return deriveKey(password.toCharArray(), hash(username).asBytes(), iterations, keyLen, keyDerivationAlgo);
	}

	private HashCode hash(String text) {
		return Hashing.sha256().hashString(text, StandardCharsets.UTF_8);
	}

	public ClientTls(String host, int port, String trustCertCollectionFilePath) throws SSLException {
		this(NettyChannelBuilder
			.forAddress(host, port)
			.sslContext(getSslContext(trustCertCollectionFilePath))
			.build());
	}

	private ClientTls(ManagedChannel channel) {
		this.channel = channel;
		this.blockingStub = PingServiceGrpc.newBlockingStub(channel);
		this.authBlockingStub = AuthServiceGrpc.newBlockingStub(channel);
		this.downloadBlockingStub = FileTransferServiceGrpc.newBlockingStub(channel);
		this.uploadStub = FileTransferServiceGrpc.newStub(channel);
		setLoggedOut();
	}

	public void shutdown() throws InterruptedException {
		channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
	}

	/**
	 * Say hello to server.
	 */
	public void ping(String message) {
		logger.info("Request: {}", message);
		PingRequest request = PingRequest.newBuilder().setMessage(message).build();
		PingResponse response;
		try {
			response = blockingStub.ping(request);
		} catch (StatusRuntimeException e) {
			logger.warn("RPC failed: {}", e.getStatus());
			return;
		}
		logger.info("Response: {}", response.getMessage());
	}
	
	/**
	 * Creates a new user in the server
	 *
	 * @param args
	 */
	public void signup(List<String> args) {
		if (isLoggedIn) {
			logger.info("Logged in as {}, logout first", this.username);
			return;
		}

		String username = args.get(0);
		String password = hash(args.get(1)).toString();
		logger.info("Request: Signup with username '{}' and password '{}'", username, password);
		Credentials request = Credentials.newBuilder()
				.setUsername(username)
				.setPassword(password)
				.build();
		try {
			authBlockingStub.signup(request);
		} catch (StatusRuntimeException e) {
			logger.warn("Signup failed: {}", e.getMessage());
			return;
		}
		logger.info("Signup successful");
	}

	/**
	 * Updates state so that client is logged in
	 *
	 * @param username
	 * @param pw
	 * @param key
	 */
	private void setLoggedIn(String username, String pw, Key key) {
		this.username = username;
		this.passwordHash = pw;
		this.symmetricKey = key;
		this.isLoggedIn = true;
	}

	/**
	 * Updates state so that nobody is logged in
	 */
	private void setLoggedOut() {
		this.username = null;
		this.symmetricKey = null;
		this.passwordHash = null;
		this.isLoggedIn = false;
	}
	
	/**
	 * Authenticates a user in the server
	 *
	 * @param args
	 */
	public void login(List<String> args) {
		if (isLoggedIn) {
			logger.info("Logged in as {}", this.username);
			return;
		}

		String username = args.get(0);
		String realPassword = args.get(1);
		String passwordToServer = hash(realPassword).toString();
		logger.info("Request: Login with username '{}' and password '{}'", username, passwordToServer);
		Credentials request = Credentials.newBuilder()
				.setUsername(username)
				.setPassword(passwordToServer)
				.build();
		try {
			authBlockingStub.login(request);
		} catch (StatusRuntimeException e) {
			logger.warn("Login failed: {}", e.getMessage());
			return;
		}
		setLoggedIn(username, passwordToServer, deriveKey(realPassword, username));
		logger.info("Login successful");
	}
	
	/**
	 * Removes the client authentication (from client side app)
	 */
	public void logout() {
		if (!isLoggedIn) {
			logger.info("Not logged in");
			return;
		}

		logger.info("Request: Logout with username '{}'", this.username);
		setLoggedOut();
		logger.info("Logout successful");
	}

	/**
	 * Requests to download file from server
	 *
	 * @param args
	 */
	public void download(List<String> args) throws ClientException {
		if (!isLoggedIn) {
			logger.info("Not logged in");
			return;
		}

		String filename = args.get(0);
		String destinationPath = args.get(1);

		logger.info("Request: download file '{}' to '{}'", filename, destinationPath);

		DownloadRequest request = DownloadRequest.newBuilder()
			.setCredentials(
				Credentials.newBuilder()
					.setUsername(this.username)
					.setPassword(this.passwordHash))
			.setFilename(filename)
			.build();

		File file = new File(destinationPath);

		// if destination is directory then append filename to destination
		if (file.isDirectory()) {
			file = Paths.get(destinationPath, filename).toFile();
		} else if (file.getParentFile() != null && !file.getParentFile().isDirectory()) {
			logger.warn("{} (no such file or directory)}", destinationPath);
			throw new ClientException(destinationPath + "(no such file or directory)");
		}

		Iterator<Chunk> content;
		try {
			content = downloadBlockingStub.download(request);
			BufferedOutputStream writer = new BufferedOutputStream(new FileOutputStream(file));
			while (content.hasNext()) {
				writer.write(content.next().getContent().toByteArray());
			}
			writer.flush();
			writer.close();
		} catch (StatusRuntimeException e) {
			logger.warn("Download failed: {}", e.getMessage());
			throw new ClientException("Download failed: " + e.getMessage());
		} catch (FileNotFoundException e) {
			// should never happen since we test existence before
			logger.warn(e.getMessage());
			throw new ClientException(e.getMessage());
		} catch (IOException e) {
			logger.error("Error writing file: {}", e.getMessage());
			throw new ClientException(e.getMessage());
		}

		logger.info("Download successful", filename, destinationPath);
	}

	/**
	 * Attemps to upload a file to the server
	 * @param args
	 */
	public void upload(List<String> args) throws InterruptedException, ClientException {
		if (!isLoggedIn) {
			logger.info("Not logged in");
			return;
		}

		final File file = new File(args.get(0));
		final CountDownLatch finishLatch = new CountDownLatch(1);
		BufferedInputStream reader = null;

		StreamObserver<UploadResponse> responseObserver = new StreamObserver<UploadResponse>() {
			@Override
			public void onNext(UploadResponse response) {
				logger.info("Reponse for file upload '{}' received", file.getPath());
			}

			@Override
			public void onError(Throwable t) {
				logger.warn("Error uploading file to server", t.getMessage());
				finishLatch.countDown();
			}

			@Override
			public void onCompleted() {
				logger.info("Finished uploading file '{}'", file.getPath());
				finishLatch.countDown();
			}
		};

		try {
			reader = new BufferedInputStream(new FileInputStream(file));
		} catch (FileNotFoundException e) {
			logger.error("File not found: {}", e.getMessage());
			throw new ClientException(e.getMessage());
		}

		StreamObserver<UploadData> requestObserver = uploadStub.upload(responseObserver);

		byte[] buffer = new byte[BUFFER_SIZE];
		int read;

		logger.info("Started uploading file '{}'", file.getPath());

		Credentials creds = Credentials.newBuilder()
			.setUsername(this.username)
			.setPassword(this.passwordHash)
			.build();

		try {
			while ((read = reader.read(buffer)) != -1) {
				requestObserver.onNext(UploadData.newBuilder()
					.setCredentials(creds)
					.setFilename(file.getName())
					.setContent(ByteString.copyFrom(buffer, 0, read))
					.build()
				);
				// RPC completed or errored before sending finished
				if (finishLatch.getCount() == 0) {
					reader.close();
					return;
				}
			}
			reader.close();
		} catch (IOException e) {
			logger.error("Could not read file", e);
			requestObserver.onError(e);
			throw new ClientException(e.getMessage());
		} catch (StatusRuntimeException e) {
			requestObserver.onError(e);
			throw new ClientException("Upload failed: " + e.getMessage());
		}

		requestObserver.onCompleted();

		// response is received asynchronously
		finishLatch.await();
	}
}
