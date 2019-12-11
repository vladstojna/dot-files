package com.r3ds.server;

import java.io.File;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Logger;

import javax.net.ssl.SSLException;

import io.grpc.Server;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyServerBuilder;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

public class ServerTls {
	private static final Logger logger = Logger.getLogger(ServerApp.class.getName());

	private Server server;

	private final int port;
	private final String certChainFilePath;
	private final String privateKeyFilePath;

	public ServerTls(int port, String certChainFilePath, String privateKeyFilePath) {
		this.port = port;
		this.certChainFilePath = certChainFilePath;
		this.privateKeyFilePath = privateKeyFilePath;
	}

	private SslContext getSslContext() throws SSLException {
		SslContextBuilder sslClientContextBuilder = SslContextBuilder
			.forServer(
				new File(certChainFilePath),
				new File(privateKeyFilePath))
			.applicationProtocolConfig(
				new ApplicationProtocolConfig(
					ApplicationProtocolConfig.Protocol.ALPN,
					ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
					ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
					ApplicationProtocolNames.HTTP_2));
		return GrpcSslContexts.configure(sslClientContextBuilder).build();
	}

	public void start() throws Exception {
		Database db = createDatabase();
		AuthTools authTools = new AuthTools(db);
		FileTools fileTools = new FileTools(db);
		server = NettyServerBuilder.forPort(port)
			.addService(new PingServiceImpl())
			.addService(new AuthServiceImpl(authTools))
			.addService(new FileTransferServiceImpl(authTools, fileTools))
			.addService(new ShareFileServiceImpl(authTools, fileTools))
			.sslContext(getSslContext())
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

