package com.r3ds.rootca;

import java.io.File;
import java.io.IOException;
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
	private final String signedCertsPath;

	public ServerTls(int port,
			String certChainFilePath,
			String privateKeyFilePath,
			String signedCertsPath) {
		this.port = port;
		this.certChainFilePath = certChainFilePath;
		this.privateKeyFilePath = privateKeyFilePath;
		this.signedCertsPath = signedCertsPath;
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

	public void start() throws IOException {
		server = NettyServerBuilder.forPort(port)
			.addService(new PingServiceImpl())
			.addService(new CertificateServiceImpl(signedCertsPath, privateKeyFilePath))
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

	private void stop() {
		if (server != null)
			server.shutdown();
	}

	public void blockUntilShutdown() throws InterruptedException {
		if (server != null)
			server.awaitTermination();
	}
}

