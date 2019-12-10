package com.r3ds.server;

import com.r3ds.Common;
import com.r3ds.ShareFile;
import com.r3ds.ShareFileServiceGrpc;
import com.r3ds.server.exception.AuthException;
import com.r3ds.server.exception.DatabaseException;
import com.r3ds.server.exception.FileInfoException;
import com.r3ds.server.file.FileInfo;
import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

public class ShareFileServiceImpl extends ShareFileServiceGrpc.ShareFileServiceImplBase {
	
	private final Logger logger = LoggerFactory.getLogger(getClass());
	
	/**
	 *
	 * @param request
	 * @param responseObserver
	 */
	@Override
	public void shareFile(com.r3ds.ShareFile.ShareRequest request,
	                      io.grpc.stub.StreamObserver<com.r3ds.ShareFile.ShareResponse> responseObserver) {
		AuthTools authTools = new AuthTools();
		FileTools fileTools = new FileTools();
		FileInfo fileInfo = null;
		String fileToDeletePath = null;
		
		try {
			authTools.login(request.getCredentials().getUsername(), request.getCredentials().getPassword());
			
			fileInfo = fileTools.existFileInDB(
					request.getCredentials().getUsername(),
					request.getFile().getOwnerUsername(),
					request.getFile().getFilename(),
					false
			);
			fileToDeletePath = fileInfo.getPath();
			
			fileInfo.setShared(true);
			fileInfo.setPath(fileTools.getLocalPathForUsernameAndFilename(
					fileInfo.getOwnerUsername(), fileInfo.getFilename(), true
			).toString());
			
			fileTools.shareFile(fileInfo, request.getCredentials().getUsername(), request.getReceivingUsername(),
					request.getSharedKeyFromSendingUser().toString(), request.getSharedKeyFromReceivingUser().toString());
			
			fileInfo.commit();
			
			// delete previous file that is no longer valid
			(new File(fileToDeletePath)).delete();
			
			ShareFile.ShareResponse response = ShareFile.ShareResponse.newBuilder()
					.build();
			
			responseObserver.onNext(response);
			responseObserver.onCompleted();
		} catch (DatabaseException e) {
			// if happened some error with database, it will not delete the previous file
			// will only delete the new file because it should not exist
			if (fileInfo != null && !fileInfo.getPath().equals(fileToDeletePath))
				(new File(fileInfo.getPath())).delete();
			
			e.printStackTrace();
			responseObserver.onError(Status.INTERNAL
					.withDescription("Something unexpected happened with DB.")
					.withCause(e)
					.asRuntimeException());
		} catch (FileInfoException e) {
			logger.error(e.getMessage());
			responseObserver.onError(Status.ALREADY_EXISTS
					.withDescription(e.getMessage())
					.withCause(e)
					.asRuntimeException());
		} catch (AuthException e) {
			logger.info("Username and password provided are not a match.");
			responseObserver.onError(Status.INTERNAL
					.withDescription("You are not logged in.")
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	/**
	 *
	 * @param request
	 * @param responseObserver
	 */
	@Override
	public void getFilesToShare(com.r3ds.Common.Credentials request,
	                           io.grpc.stub.StreamObserver<com.r3ds.ShareFile.GetFilesToShareResponse> responseObserver) {
		AuthTools authTools = new AuthTools();
		FileTools fileTools = new FileTools();
		
		try {
			authTools.login(request.getUsername(), request.getPassword());
			
			ShareFile.GetFilesToShareResponse.Builder responseBuilder = ShareFile.GetFilesToShareResponse.newBuilder();
			
			List<FileInfo> files = fileTools.getFilesToShare(request.getUsername());
			for (FileInfo file : files) {
				Common.FileData.Builder fileBuilder = Common.FileData.newBuilder();
				fileBuilder.setOwnerUsername(file.getOwnerUsername());
				fileBuilder.setFilename(file.getFilename());
				fileBuilder.setShared(file.isShared());
				responseBuilder.addFiles(fileBuilder.build());
			}
			
			responseObserver.onNext(responseBuilder.build());
			responseObserver.onCompleted();
		} catch (DatabaseException e) {
			e.printStackTrace();
			responseObserver.onError(Status.INTERNAL
					.withDescription("Something unexpected happened with DB.")
					.withCause(e)
					.asRuntimeException());
		} catch (AuthException e) {
			logger.info("Username and password provided are not a match.");
			responseObserver.onError(Status.INTERNAL
					.withDescription("You are not logged in.")
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	@Override
	public void fileTotallyShared(com.r3ds.ShareFile.FileTotallySharedRequest request,
	                              io.grpc.stub.StreamObserver<com.r3ds.ShareFile.FileTotallySharedResponse> responseObserver) {
		AuthTools authTools = new AuthTools();
		FileTools fileTools = new FileTools();
		
		try {
			authTools.login(request.getCredentials().getUsername(), request.getCredentials().getPassword());
			
			FileInfo fileInfo = fileTools.existFileInDB(
					request.getCredentials().getUsername(),
					request.getFile().getOwnerUsername(),
					request.getFile().getFilename(),
					false
			);
			
			fileTools.fileIsTotallyShared(
					fileInfo,
					request.getSendingUsername(),
					request.getCredentials().getUsername(),
					request.getSharedKey()
			);
		} catch (DatabaseException e) {
			e.printStackTrace();
			responseObserver.onError(Status.INTERNAL
					.withDescription("Something unexpected happened with DB.")
					.withCause(e)
					.asRuntimeException());
		} catch (AuthException e) {
			System.out.println("Username and password provided are not a match.");
			responseObserver.onError(Status.INTERNAL
					.withDescription("You are not logged in.")
					.withCause(e)
					.asRuntimeException());
		}
	}
}
