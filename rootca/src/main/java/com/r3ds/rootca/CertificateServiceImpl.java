package com.r3ds.rootca;

import com.r3ds.CertificateServiceGrpc;
import com.r3ds.Certification.CertificateSignatureRequest;
import com.r3ds.Certification.CertificateSignatureResponse;
import com.r3ds.Certification.CertificateRequest;
import com.r3ds.Certification.CertificateResponse;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Date;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

public class CertificateServiceImpl extends CertificateServiceGrpc.CertificateServiceImplBase {

	private static final Logger logger = LoggerFactory.getLogger(CertificateServiceImpl.class);

	private static final SecureRandom secRng = new SecureRandom();

	private final String signedCertPath;
	private final String privateKeyPath;

	public CertificateServiceImpl(String signedCertPath, String privateKeyPath) {
		super();
		this.signedCertPath = signedCertPath;
		this.privateKeyPath = privateKeyPath;
	}

	private static String getDistinguishedName(String c, String o, String ou, String cn) {
		return "C=" + c + ",O=" + o + ",OU=" + ou + ",CN=" + cn;
	}

	private static BigInteger nextSerialNumber() {
		byte[] serial = new byte[20];
		secRng.nextBytes(serial);
		return new BigInteger(serial);
	}

	private static long oneYear() {
		return 1000 * 60 * 60 * 24 * 365;
	}

	@Override
	public void sign(CertificateSignatureRequest request, StreamObserver<CertificateSignatureResponse> responseObserver) {

		String username = request.getUsername();
		String filename = username + ".pem";
		byte[] publicKeyBytes = request.getPublicKey().toByteArray();
		String commonName = request.getCommonName();

		Path signedPath = Paths.get(signedCertPath);
		if (!Files.exists(signedPath)) {
			logger.info("Directory for signed certificates does not exist, creating one");
			try {
				Files.createDirectory(signedPath);
			} catch (IOException e) {
				logger.error(e.getMessage(), e);
				responseObserver.onError(Status.INTERNAL
					.withDescription("Error signing certificate")
					.withCause(e)
					.asRuntimeException());
				return;
			}
		}

		Path outPath = Paths.get(signedCertPath, filename);

		try (PEMParser parser = new PEMParser(new FileReader(privateKeyPath));
			JcaPEMWriter writer = new JcaPEMWriter(new FileWriter(outPath.toString())))
		{

			logger.info("Started signing process");

			PublicKey publicKey = KeyFactory.getInstance("RSA")
				.generatePublic(new X509EncodedKeySpec(publicKeyBytes));
			SubjectPublicKeyInfo publicKeyInfo = SubjectPublicKeyInfo.getInstance(publicKeyBytes);

			logger.info("Extracted public key data");

			PrivateKeyInfo privateKeyInfo = (PrivateKeyInfo)parser.readObject();
			PrivateKey privateKey = KeyFactory.getInstance("RSA")
				.generatePrivate(new PKCS8EncodedKeySpec(privateKeyInfo.getEncoded()));

			logger.info("Extracted private key data");

			PKCS10CertificationRequestBuilder p10Builder = new JcaPKCS10CertificationRequestBuilder(
				new X500Name(getDistinguishedName("PT", username, username, commonName)), publicKey);

			JcaContentSignerBuilder csBuilder = new JcaContentSignerBuilder("SHA256withRSA");
			ContentSigner signer = csBuilder.build(privateKey);

			PKCS10CertificationRequest p10Holder = p10Builder.build(signer);

			X509v3CertificateBuilder certificateBuilder = new X509v3CertificateBuilder(
				new X500Name(getDistinguishedName("PT", "rootca", "rootca", "localhost")),
				nextSerialNumber(),
				new Date(System.currentTimeMillis()),
				new Date(System.currentTimeMillis() + oneYear()),
				p10Holder.getSubject(),
				publicKeyInfo);

			X509CertificateHolder x509Holder = certificateBuilder.build(signer);

			logger.info("Signed certificate");

			writer.writeObject(x509Holder);
			logger.info("Finished writing certificate");

			responseObserver.onNext(CertificateSignatureResponse.newBuilder().build());
			responseObserver.onCompleted();

		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL
				.withDescription("Error signing certificate")
				.withCause(e)
				.asRuntimeException());
		}
	}

	@Override
	public void retrieve(CertificateRequest request, StreamObserver<CertificateResponse> responseObserver) {
		
	}

}

