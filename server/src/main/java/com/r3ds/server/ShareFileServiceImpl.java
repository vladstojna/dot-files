package com.r3ds.server;

import com.google.protobuf.ByteString;
import com.r3ds.Common;
import com.r3ds.ShareFile;
import com.r3ds.ShareFileServiceGrpc;
import com.r3ds.server.exception.AuthException;
import com.r3ds.server.exception.DatabaseException;
import com.r3ds.server.exception.FileInfoException;
import com.r3ds.server.exception.ShareException;
import com.r3ds.server.file.FileInfo;
import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

public class ShareFileServiceImpl extends ShareFileServiceGrpc.ShareFileServiceImplBase {
	
	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final AuthTools authTools;
	private final FileTools fileTools;

	public ShareFileServiceImpl(AuthTools authTools, FileTools fileTools) {
		this.authTools = authTools;
		this.fileTools = fileTools;
	}
	
	@Override
	public void isFileShared(com.r3ds.ShareFile.IsFileSharedRequest request,
	                         io.grpc.stub.StreamObserver<com.r3ds.ShareFile.IsFileSharedResponse> responseObserver) {
		
		try {
			authTools.login(request.getCredentials().getUsername(), request.getCredentials().getPassword());
			
			FileInfo fileInfo = fileTools.existFileInDB(
					request.getCredentials().getUsername(),
					request.getOwnerUsername(),
					request.getFilename()
			);
			
			if (fileInfo.isNewFile())
				throw new FileNotFoundException(String.format("File %s was not found.", fileInfo.getFilename()));
			
			responseObserver.onNext(ShareFile.IsFileSharedResponse.newBuilder().setIsShared(fileInfo.isShared()).build());
			responseObserver.onCompleted();
		} catch (DatabaseException e) {
			e.printStackTrace();
			responseObserver.onError(Status.INTERNAL
					.withDescription("Something unexpected happened with DB.")
					.withCause(e)
					.asRuntimeException());
		} catch (FileNotFoundException e) {
			logger.error(e.getMessage());
			responseObserver.onError(Status.NOT_FOUND
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
	
	@Override
	public void shareFile(com.r3ds.ShareFile.ShareRequest request,
	                      io.grpc.stub.StreamObserver<com.r3ds.ShareFile.ShareResponse> responseObserver) {
		FileInfo fileInfo = null;
		String fileToDeletePath = null;
		
		try {
			authTools.login(request.getCredentials().getUsername(), request.getCredentials().getPassword());
			
			fileInfo = fileTools.existFileInDB(
					request.getCredentials().getUsername(),
					request.getFile().getOwnerUsername(),
					request.getFile().getFilename()
			);
			
			if (fileInfo.isNewFile())
				throw new FileNotFoundException(String.format("File %s was not found.", fileInfo.getFilename()));
			
			if (!request.getCredentials().getUsername().equals(request.getFile().getOwnerUsername()))
				throw new ShareException("The user must be the owner of the file to share it.");
			
			fileToDeletePath = fileInfo.getPath();
			
			fileInfo.setShared(true);
			fileInfo.setPath(fileTools.getLocalPathForUsernameAndFilename(
					fileInfo.getOwnerUsername(), fileInfo.getFilename(), true
			).toString());
			
			fileTools.shareFile(fileInfo, request.getCredentials().getUsername(), request.getReceivingUsername(),
					request.getSharedKeyFromSendingUser().toByteArray(),
					request.getSharedKeyFromReceivingUser().toByteArray()
			);
			
			fileInfo.commit();
			
			// delete previous file that is no longer valid
			(new File(fileToDeletePath)).delete();
			
			ShareFile.ShareResponse response = ShareFile.ShareResponse.newBuilder()
					.build();
			
			responseObserver.onNext(response);
			responseObserver.onCompleted();
		} catch (ShareException e) {
			logger.info("The user ('{}') is not the owner ('{}') of the file.",
					request.getCredentials().getUsername(), request.getFile().getOwnerUsername());
			responseObserver.onError(Status.PERMISSION_DENIED
					.withDescription(e.getMessage())
					.withCause(e)
					.asRuntimeException());
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
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getMessage())
					.withCause(e)
					.asRuntimeException());
		} catch (FileNotFoundException e) {
			logger.error(e.getMessage());
			responseObserver.onError(Status.NOT_FOUND
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
	public void unshareFile(com.r3ds.ShareFile.UnshareRequest request,
	                        io.grpc.stub.StreamObserver<com.r3ds.ShareFile.UnshareResponse> responseObserver) {
		
		try {
			authTools.login(request.getCredentials().getUsername(), request.getCredentials().getPassword());
			
			FileInfo fileInfo = fileTools.existFileInDB(
					request.getRejectedUsername(),
					request.getFile().getOwnerUsername(),
					request.getFile().getFilename()
			);
			
			if (fileInfo.isNewFile())
				throw new FileNotFoundException(String.format("File %s was not found.", fileInfo.getFilename()));
			
			if (!request.getCredentials().getUsername().equals(request.getFile().getOwnerUsername()))
				throw new ShareException("The user must be the owner of the file to unshare it.");
			
			// if owner is trying to unshare with himself, this operation will have no effect
			if (!request.getFile().getOwnerUsername().equals(request.getRejectedUsername()))
				fileTools.unshareFile(fileInfo, request.getRejectedUsername());
		} catch (ShareException e) {
			logger.info("The user ('{}') is not the owner ('{}') of the file.",
					request.getCredentials().getUsername(), request.getFile().getOwnerUsername());
			responseObserver.onError(Status.PERMISSION_DENIED
					.withDescription(e.getMessage())
					.withCause(e)
					.asRuntimeException());
		} catch (DatabaseException e) {
			e.printStackTrace();
			responseObserver.onError(Status.INTERNAL
					.withDescription("Something unexpected happened with DB.")
					.withCause(e)
					.asRuntimeException());
		} catch (FileNotFoundException e) {
			logger.error(e.getMessage());
			responseObserver.onError(Status.NOT_FOUND
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
		
		try {
			authTools.login(request.getUsername(), request.getPassword());
			
			ShareFile.GetFilesToShareResponse.Builder responseBuilder = ShareFile.GetFilesToShareResponse.newBuilder();
			
			List<FileInfo> files = fileTools.getFilesToShare(request.getUsername());
			for (FileInfo file : files) {
				ShareFile.FileDataWithSharedKey.Builder fileWithKeyBuilder = ShareFile.FileDataWithSharedKey.newBuilder();
				Common.FileData.Builder fileBuilder = Common.FileData.newBuilder();
				fileBuilder.setOwnerUsername(file.getOwnerUsername());
				fileBuilder.setFilename(file.getFilename());
				fileBuilder.setShared(file.isShared());
				fileWithKeyBuilder.setFile(fileBuilder.build());
				fileWithKeyBuilder.setSharedKey(ByteString.copyFrom(file.getSharedKey()));
				responseBuilder.addFiles(fileWithKeyBuilder.build());
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
	
	/**
	 *
	 * @param request
	 * @param responseObserver
	 */
	@Override
	public void fileTotallyShared(com.r3ds.ShareFile.FileTotallySharedRequest request,
	                              io.grpc.stub.StreamObserver<com.r3ds.ShareFile.FileTotallySharedResponse> responseObserver) {
		
		try {
			authTools.login(request.getCredentials().getUsername(), request.getCredentials().getPassword());
			
			FileInfo fileInfo = fileTools.existFileInDB(
					request.getCredentials().getUsername(),
					request.getFile().getOwnerUsername(),
					request.getFile().getFilename()
			);
			
			if (fileInfo.isNewFile())
				throw new FileNotFoundException(String.format("File %s was not found.", fileInfo.getFilename()));
			
			fileTools.fileIsTotallyShared(
					fileInfo,
					request.getSendingUsername(),
					request.getCredentials().getUsername(),
					request.getSharedKey().toByteArray()
			);
		} catch (DatabaseException e) {
			e.printStackTrace();
			responseObserver.onError(Status.INTERNAL
					.withDescription("Something unexpected happened with DB.")
					.withCause(e)
					.asRuntimeException());
		} catch (FileNotFoundException e) {
			logger.error(e.getMessage());
			responseObserver.onError(Status.NOT_FOUND
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
}
