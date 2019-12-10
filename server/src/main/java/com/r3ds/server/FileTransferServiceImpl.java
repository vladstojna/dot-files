package com.r3ds.server;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.protobuf.ByteString;

import com.r3ds.FileTransfer;
import com.r3ds.FileTransfer.Chunk;
import com.r3ds.FileTransfer.Chunk.Builder;
import com.r3ds.FileTransfer.DownloadRequest;
import com.r3ds.FileTransfer.UploadData;
import com.r3ds.FileTransfer.UploadResponse;
import com.r3ds.FileTransferServiceGrpc.FileTransferServiceImplBase;

import com.r3ds.server.exception.AuthException;
import com.r3ds.server.exception.DatabaseException;
import com.r3ds.server.file.FileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.stub.StreamObserver;
import io.grpc.Status;


public class FileTransferServiceImpl extends FileTransferServiceImplBase {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private static final int BUFFER_SIZE = 4096;

	@Override
	public void download(DownloadRequest request, StreamObserver<Chunk> responseObserver) {
		FileTools fileTools = new FileTools();
		String fileRelativePath = fileTools.getRelativePathForUsernameAndFilename(
				request.getFile().getOwnerUsername(),
				request.getFile().getFilename(),
				request.getFile().getShared()
		).toString();
		
		try {
			AuthTools authTools = new AuthTools();
			authTools.login(request.getCredentials().getUsername(), request.getCredentials().getPassword());
			
			logger.info("Download request from '{}' for file '{}' (owner: '{}'; shared: {})",
				request.getCredentials().getUsername(), request.getFile().getFilename(),
					request.getFile().getOwnerUsername(), request.getFile().getShared());
			
			// check database for path and if user can download it
			FileInfo fileInfo = fileTools.existFileInDB(
					request.getCredentials().getUsername(),
					request.getFile().getOwnerUsername(),
					request.getFile().getFilename(),
					request.getFile().getShared()
			);
			
			if (fileInfo.isNewFile())
				throw new FileNotFoundException(String.format("File %s was not found.", fileRelativePath));
			
			String path = fileInfo.getPath();

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
			
			fileTools.saveFileReadInLog(fileInfo, request.getCredentials().getUsername());
			responseObserver.onCompleted();
			
		} catch (AuthException e) {
			logger.info("Username and password provided are not a match.");
			responseObserver.onError(Status.INTERNAL
					.withDescription("You are not logged in.")
					.withCause(e)
					.asRuntimeException());
		} catch (DatabaseException e) {
			e.printStackTrace();
			responseObserver.onError(Status.INTERNAL
					.withDescription("Something unexpected happened with DB.")
					.withCause(e)
					.asRuntimeException());
		} catch (FileNotFoundException e) {
			logger.error("File not found: {}", e.getMessage());
			responseObserver.onError(Status.INTERNAL
				.withDescription("File not found: " + fileRelativePath)
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
	
	/**
	 *
	 * @param request
	 * @param responseObserver
	 */
	@Override
	public void downloadKey(com.r3ds.FileTransfer.DownloadKeyRequest request,
	                        io.grpc.stub.StreamObserver<com.r3ds.FileTransfer.DownloadKeyResponse> responseObserver) {
		try {
			AuthTools authTools = new AuthTools();
			authTools.login(request.getCredentials().getUsername(), request.getCredentials().getPassword());
			
			FileTools fileTools = new FileTools();
			FileInfo fileInfo = fileTools.existFileInDB(
					request.getCredentials().getUsername(),
					request.getFile().getOwnerUsername(),
					request.getFile().getFilename(),
					request.getFile().getShared()
			);
			
			responseObserver.onNext(
					FileTransfer.DownloadKeyResponse.newBuilder().setSharedKey(
							ByteString.copyFrom(fileInfo.getSharedKey())
					).build()
			);
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
	public StreamObserver<UploadData> upload(final StreamObserver<UploadResponse> responseObserver) {

		return new StreamObserver<UploadData>() {
			int callCount = 0;
			BufferedOutputStream writer = null;
			UploadData lastUploadData = null;
			String pathToFile = null;

			// TODO: validate in DB and write to file
			@Override
			public void onNext(UploadData uploadData) {
				lastUploadData = uploadData;
				logger.info("Upload chunk #{} from '{}' for file '{}' (owner: '{}'; shared: {})", callCount,
					uploadData.getCredentials().getUsername(), uploadData.getFile().getFilename(),
						uploadData.getFile().getOwnerUsername(), uploadData.getFile().getShared());
				callCount++;
				
				
				try {
					FileTools fileTools = new FileTools();
					Path filePath = fileTools.getLocalPathToDirectoryForUsername(
							uploadData.getFile().getOwnerUsername(),
							uploadData.getFile().getShared()
					);
					
					if (!Files.isDirectory(filePath)) {
						logger.info("Directory for user '{}' (shared: {}) does not exist, creating one",
								uploadData.getFile().getOwnerUsername(), uploadData.getFile().getShared());
						Files.deleteIfExists(filePath);
						Files.createDirectories(filePath);
					}
					pathToFile = fileTools.getLocalPathForUsernameAndFilename(
							uploadData.getFile().getOwnerUsername(),
							uploadData.getFile().getFilename(),
							uploadData.getFile().getShared()
					).toString();
					
					byte[] content = uploadData.getContent().toByteArray();
					
					if (writer == null) {
						writer = new BufferedOutputStream(new FileOutputStream(pathToFile));
					}
					writer.write(content);
					writer.flush();
				} catch (IOException e) {
					e.printStackTrace();
					responseObserver.onError(Status.INTERNAL
						.withDescription("Error uploading file, please try again.")
						.withCause(e)
						.asRuntimeException());
				}
				
			}

			@Override
			public void onError(Throwable t) {
				logger.error("Encountered error during upload", t);
				(new File(pathToFile)).delete();
				
				if (writer != null) {
					try {
						writer.close();
					} catch (IOException e) {
						e.printStackTrace();
					} finally {
						writer = null;
					}
				}
			}

			@Override
			public void onCompleted() {
				responseObserver.onNext(UploadResponse.newBuilder().build());
				
				try {
					AuthTools authTools = new AuthTools();
					authTools.login(
							lastUploadData.getCredentials().getUsername(),
							lastUploadData.getCredentials().getPassword()
					);
					
					// check database if file already exists
					FileTools fileTools = new FileTools();
					FileInfo fileInfo = fileTools.existFileInDB(
							lastUploadData.getCredentials().getUsername(),
							lastUploadData.getFile().getOwnerUsername(),
							lastUploadData.getFile().getFilename(),
							lastUploadData.getFile().getShared()
					);
					
					if (fileInfo.isNewFile()) {
						// if new, it will save in db
						fileTools.createFileInDB(fileInfo, pathToFile, lastUploadData.getFile().getShared());
					} else {
						// if already exists
						fileTools.saveFileWriteInLog(fileInfo, lastUploadData.getCredentials().getUsername());
						pathToFile = fileInfo.getPath();
					}
					
					responseObserver.onCompleted();
					
				} catch (DatabaseException e) {
					e.printStackTrace();
					(new File(pathToFile)).delete();
					
					responseObserver.onError(Status.INTERNAL
							.withDescription("Error uploading file, please try again.")
							.withCause(e)
							.asRuntimeException());
				} catch (AuthException e) {
					logger.info("Username and password provided are not a match.");
					(new File(pathToFile)).delete();
					
					responseObserver.onError(Status.INTERNAL
							.withDescription("You are not logged in.")
							.withCause(e)
							.asRuntimeException());
				} finally {
					if (writer != null) {
						try {
							writer.close();
						} catch (IOException e) {
							e.printStackTrace();
						} finally {
							writer = null;
						}
					}
				}
			}
		};
	}
}