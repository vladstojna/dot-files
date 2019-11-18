package com.r3ds.server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;

import com.google.protobuf.ByteString;

import com.r3ds.FileTransfer.Chunk;
import com.r3ds.FileTransfer.Chunk.Builder;
import com.r3ds.FileTransfer.DownloadRequest;
import com.r3ds.FileTransfer.UploadData;
import com.r3ds.FileTransfer.UploadResponse;
import com.r3ds.FileTransferServiceGrpc.FileTransferServiceImplBase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.stub.StreamObserver;
import io.grpc.Status;


public class FileTransferServiceImpl extends FileTransferServiceImplBase {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private static final int BUFFER_SIZE = 4096;

	@Override
	public void download(DownloadRequest request, StreamObserver<Chunk> responseObserver) {
		try {

			logger.info("Download request from '{}' for file '{}'",
				request.getCredentials().getUsername(), request.getFilename());

			// check database for path and if user can download it
			// temporary path value, user's home
			String path = Paths.get(System.getProperty("user.home"), request.getFilename()).toString();

			BufferedInputStream reader = new BufferedInputStream(
				new FileInputStream(path));
			byte[] buffer = new byte[BUFFER_SIZE];
			int length;

			Builder response = Chunk.newBuilder();
			while ((length = reader.read(buffer, 0, BUFFER_SIZE)) != -1) {
				responseObserver.onNext(response
					.setContent(ByteString.copyFrom(buffer, 0, length))
					.build());
			}
			reader.close();
			responseObserver.onCompleted();

		} catch (FileNotFoundException e) {
			logger.error("File not found: {}", e.getMessage());
			responseObserver.onError(Status.INTERNAL
				.withDescription("File not found: " + request.getFilename())
				.withCause(e)
				.asRuntimeException());

		} catch (IOException e) {
			e.printStackTrace();
			responseObserver.onError(Status.INTERNAL
				.withDescription("Error downloading file, please try again.")
				.withCause(e)
				.asRuntimeException());
		}
	}

	@Override
	public StreamObserver<UploadData> upload(final StreamObserver<UploadResponse> responseObserver) {

		return new StreamObserver<UploadData>() {

			// TODO: validate in DB and write to file
			@Override
			public void onNext(UploadData uploadData) {
			}

			@Override
			public void onError(Throwable t) {
				logger.error(t.getMessage());
			}

			@Override
			public void onCompleted() {
				responseObserver.onNext(UploadResponse.newBuilder().build());
				responseObserver.onCompleted();
			}
		};
	}
}