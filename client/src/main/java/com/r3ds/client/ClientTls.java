package com.r3ds.client;

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
	 *
	 * @param args
	 */
	public void signup(List<String> args) {
		logger.log(Level.INFO, "Request: Signup with ", args.get(0));
		Auth.SignupRequest request = Auth.SignupRequest.newBuilder()
				.setUsername(args.get(0))
				.setPassword(args.get(1))
				.build();
		Auth.SignupResponse response;
		try {
			response = authBlockingStub.signup(request);
		} catch (StatusRuntimeException e) {
			logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
			return;
		}
		logger.log(Level.INFO, "Response: {0}", response.getMessage());

	}
}
