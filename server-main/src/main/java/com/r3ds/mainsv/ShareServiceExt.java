package com.r3ds.mainsv;

import com.r3ds.ShareFileServiceGrpc;
import com.r3ds.Common.Credentials;
import com.r3ds.ShareFile.FileTotallySharedRequest;
import com.r3ds.ShareFile.FileTotallySharedResponse;
import com.r3ds.ShareFile.GetFilesToShareResponse;
import com.r3ds.ShareFile.ShareRequest;
import com.r3ds.ShareFile.ShareResponse;
import com.r3ds.ShareFile.UnshareRequest;
import com.r3ds.ShareFile.UnshareResponse;
import com.r3ds.server.AuthTools;
import com.r3ds.server.FileTools;
import com.r3ds.server.ShareFileServiceImpl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

public class ShareServiceExt extends ShareFileServiceImpl {

	private Logger logger = LoggerFactory.getLogger(ShareServiceExt.class);

	private final ShareFileServiceGrpc.ShareFileServiceBlockingStub backupStub;

	public ShareServiceExt(AuthTools authTools, FileTools fileTools, ShareFileServiceGrpc.ShareFileServiceBlockingStub backupStub) {
		super(authTools, fileTools);
		this.backupStub = backupStub;
	}

	@Override
	public void shareFile(ShareRequest request, StreamObserver<ShareResponse> responseObserver) {
		super.shareFile(request, responseObserver);
		try {
			backupStub.shareFile(request);
			logger.info("Replication RPC finished");
		} catch (StatusRuntimeException e) {
			logger.warn("Replication RPC failed: {}", e.getMessage());
		}
	}

	@Override
	public void unshareFile(UnshareRequest request, StreamObserver<UnshareResponse> responseObserver) {
		super.unshareFile(request, responseObserver);
		try {
			backupStub.unshareFile(request);
			logger.info("Replication RPC finished");
		} catch (StatusRuntimeException e) {
			logger.warn("Replication RPC failed: {}", e.getMessage());
		}
	}

	@Override
	public void getFilesToShare(Credentials request, StreamObserver<GetFilesToShareResponse> responseObserver) {
		super.getFilesToShare(request, responseObserver);
		try {
			backupStub.getFilesToShare(request);
			logger.info("Replication RPC finished");
		} catch (StatusRuntimeException e) {
			logger.warn("Replication RPC failed: {}", e.getMessage());
		}
	}

	@Override
	public void fileTotallyShared(FileTotallySharedRequest request, StreamObserver<FileTotallySharedResponse> responseObserver) {
		super.fileTotallyShared(request, responseObserver);
		try {
			backupStub.fileTotallyShared(request);
			logger.info("Replication RPC finished");
		} catch (StatusRuntimeException e) {
			logger.warn("Replication RPC failed: {}", e.getMessage());
		}
	}

}