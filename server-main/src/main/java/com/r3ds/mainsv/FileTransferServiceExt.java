package com.r3ds.mainsv;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import com.r3ds.FileTransferServiceGrpc;
import com.r3ds.Common.Credentials;
import com.r3ds.FileTransfer.Chunk;
import com.r3ds.FileTransfer.DownloadKeyRequest;
import com.r3ds.FileTransfer.DownloadKeyResponse;
import com.r3ds.FileTransfer.DownloadRequest;
import com.r3ds.FileTransfer.ListResponse;
import com.r3ds.FileTransfer.UploadData;
import com.r3ds.FileTransfer.UploadResponse;
import com.r3ds.server.AuthTools;
import com.r3ds.server.FileTools;
import com.r3ds.server.FileTransferServiceImpl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

public class FileTransferServiceExt extends FileTransferServiceImpl {

	private Logger logger = LoggerFactory.getLogger(FileTransferServiceExt.class);

	private final FileTransferServiceGrpc.FileTransferServiceBlockingStub backupStub;
	private final FileTransferServiceGrpc.FileTransferServiceStub backupAsyncStub;

	public FileTransferServiceExt(AuthTools authTools, FileTools fileTools,
			FileTransferServiceGrpc.FileTransferServiceBlockingStub backupStub,
			FileTransferServiceGrpc.FileTransferServiceStub backupAsyncStub) {
		super(authTools, fileTools);
		this.backupStub = backupStub;
		this.backupAsyncStub = backupAsyncStub;
	}

	@Override
	public void download(DownloadRequest request, StreamObserver<Chunk> responseObserver) {
		super.download(request, responseObserver);
		logger.info("No replication RPC needed for 'download' operation");
	}

	@Override
	public void downloadKey(DownloadKeyRequest request, StreamObserver<DownloadKeyResponse> responseObserver) {
		super.downloadKey(request, responseObserver);
		logger.info("No replication RPC needed for 'downloadKey' operation");
	}

	@Override
	public void listFiles(Credentials request, StreamObserver<ListResponse> responseObserver) {
		super.listFiles(request, responseObserver);
		logger.info("No replication RPC needed for 'listFiles' operation");
	}

	@Override
	public StreamObserver<UploadData> upload(final StreamObserver<UploadResponse> responseObserver) {
		StreamObserver<UploadData> result = super.upload(responseObserver);

		final CountDownLatch finishLatch = new CountDownLatch(1);
		final AtomicBoolean errorHappened = new AtomicBoolean(false);

		StreamObserver<UploadResponse> bckResponseObserver = new StreamObserver<UploadResponse>() {
			@Override
			public void onNext(UploadResponse response) {
				logger.info("Response for file upload received");
			}

			@Override
			public void onError(Throwable t) {
				logger.warn("Error uploading file to server: {}", t.getMessage());
				finishLatch.countDown();
				errorHappened.set(true);
			}

			@Override
			public void onCompleted() {
				logger.info("Finished uploading file");
				finishLatch.countDown();
			}
		};

		backupAsyncStub.upload(bckResponseObserver);

		try {
			finishLatch.await();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		return result;
	}
}