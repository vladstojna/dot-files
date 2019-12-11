package com.r3ds.mainsv;

import com.r3ds.PingServiceGrpc;
import com.r3ds.Ping.PingRequest;
import com.r3ds.server.*;

import java.io.File;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Logger;

import javax.net.ssl.SSLException;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

public class ServerTls {
	private static final Logger logger = Logger.getLogger(ServerApp.class.getName());

	private Server server;

	private ManagedChannel channel;

	private final int port;
	private final String certChainFilePath;
	private final String privateKeyFilePath;
	private final String trustCertCollectionPath;
	private final String backupHost;
	private final int backupPort;

	public ServerTls(
			int port,
			String certChainFilePath,
			String privateKeyFilePath,
			String trustCertCollectionPath,
			String backupHost,
			int backupPort) {
		this.port = port;
		this.certChainFilePath = certChainFilePath;
		this.privateKeyFilePath = privateKeyFilePath;
		this.trustCertCollectionPath = trustCertCollectionPath;
		this.backupHost = backupHost;
		this.backupPort = backupPort;
	}

	private SslContext getServerSslContext() throws SSLException {
		SslContextBuilder builder = SslContextBuilder
			.forServer(
				new File(certChainFilePath),
				new File(privateKeyFilePath))
			.applicationProtocolConfig(
				new ApplicationProtocolConfig(
					ApplicationProtocolConfig.Protocol.ALPN,
					ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
					ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
					ApplicationProtocolNames.HTTP_2));
		return GrpcSslContexts.configure(builder).build();
	}

	private SslContext getClientSslContext() throws SSLException {
		SslContextBuilder builder = SslContextBuilder
			.forClient()
			.trustManager(new File(trustCertCollectionPath))
			.keyManager(new File(certChainFilePath), new File(privateKeyFilePath));
		return GrpcSslContexts.configure(builder).build();
	}

	public void start() throws Exception {
		Database db = createDatabase();
		AuthTools authTools = new AuthTools(db);
		FileTools fileTools = new FileTools(db);

		channel = NettyChannelBuilder.forAddress(backupHost, backupPort)
			.overrideAuthority("localhost")
			.sslContext(getClientSslContext())
			.build();

		PingServiceGrpc.PingServiceBlockingStub pingStub = PingServiceGrpc.newBlockingStub(channel);
		pingStub.ping(PingRequest.newBuilder().setMessage("hello backup, from main").build());

		server = NettyServerBuilder.forPort(port)
			.addService(new PingServiceExt(pingStub))
			.addService(new AuthServiceImpl(authTools))
			.addService(new FileTransferServiceImpl(authTools, fileTools))
			.addService(new ShareFileServiceImpl(authTools, fileTools))
			.sslContext(getServerSslContext())
			.build()
			.start();

		logger.info("Server started, listening on " + port);
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				// Use stderr here since the logger may have been reset by its JVM shutdown hook.
				System.err.println("*** shutting down gRPC server since JVM is shutting down");
				ServerTls.this.stop();
				System.err.println("*** server shut down");
			}
		});
	}

	private Database createDatabase() throws Exception {
		String rsrcName = "database/config.properties";
		Properties props = new Properties();
		props.load(ServerTls.class.getClassLoader().getResourceAsStream(rsrcName));
		return new Database(props);
	}

	private void stop() {
		if (server != null)
			server.shutdown();
	}

	public void blockUntilShutdown() throws InterruptedException {
		if (server != null)
			server.awaitTermination();
	}
}
