package com.r3ds.client;

import com.google.common.hash.Hashing;
import com.r3ds.Auth;
import com.r3ds.AuthServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

import com.r3ds.PingServiceGrpc;
import com.r3ds.Ping.PingRequest;
import com.r3ds.Ping.PingResponse;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLException;

/**
 * A simple client that requests a greeting from the {@link HelloWorldServerTls} with TLS.
 */
public class ClientTls {
	private static final Logger logger = Logger.getLogger(ClientTls.class.getName());

	private final ManagedChannel channel;
	private final PingServiceGrpc.PingServiceBlockingStub blockingStub;
	private final AuthServiceGrpc.AuthServiceBlockingStub authBlockingStub;
	
	private String username;

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
		
		// it means that nobody is logged in
		this.username = null;
	}

	public void shutdown() throws InterruptedException {
		channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
	}

	/**
	 * Say hello to server.
	 */
	public void ping(String message) {
		logger.log(Level.INFO, "Request: {0}", message);
		PingRequest request = PingRequest.newBuilder().setMessage(message).build();
		PingResponse response;
		try {
			response = blockingStub.ping(request);
		} catch (StatusRuntimeException e) {
			logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
			return;
		}
		logger.log(Level.INFO, "Response: {0}", response.getMessage());
	}
	
	/**
	 * Creates a new user in the server
	 *
	 * @param args
	 */
	public void signup(List<String> args) {
		String username = args.get(0);
		String password = Hashing.sha256()
				.hashString(args.get(1), StandardCharsets.UTF_8)
				.toString();
		logger.log(Level.INFO, () -> String.format("Request: Signup with username '%1$s' and password '%2$s'", username, password));
		Auth.Credentials request = Auth.Credentials.newBuilder()
				.setUsername(username)
				.setPassword(password)
				.build();
		try {
			authBlockingStub.signup(request);
		} catch (StatusRuntimeException e) {
			logger.log(Level.WARNING, "Signup failed: {0}", e.getMessage());
			return;
		}
		logger.log(Level.INFO, "Signup successful");
	}
	
	/**
	 * Authenticates a user in the server
	 *
	 * @param args
	 */
	public void login(List<String> args) {
		String username = args.get(0);
		String realPassword = args.get(1);
		String passwordToServer = Hashing.sha256()
				.hashString(realPassword, StandardCharsets.UTF_8)
				.toString();
		logger.log(Level.INFO, () -> String.format("Request: Login with username '%1$s' and password '%2$s'", username, passwordToServer));
		Auth.Credentials request = Auth.Credentials.newBuilder()
				.setUsername(username)
				.setPassword(passwordToServer)
				.build();
		try {
			authBlockingStub.login(request);
		} catch (StatusRuntimeException e) {
			logger.log(Level.WARNING, "Login failed: {0}", e.getMessage());
			return;
		}
		// it means that is logged in from now on
		this.username = username;
		logger.log(Level.INFO, "Login successful");
	}
	
	/**
	 * Removes the client authentication (from client side app)
	 */
	public void logout() {
		logger.log(Level.INFO, () -> String.format("Request: Logout with username '%1$s'", this.username));
		// it means that nobody is logged in from now on
		this.username = null;
		logger.log(Level.INFO, "Logout successful");
	}
}
