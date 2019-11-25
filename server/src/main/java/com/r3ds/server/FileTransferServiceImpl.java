package com.r3ds.server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;

import com.google.protobuf.ByteString;

import com.r3ds.FileTransfer.Chunk;
import com.r3ds.FileTransfer.Chunk.Builder;
import com.r3ds.FileTransfer.DownloadRequest;
import com.r3ds.FileTransfer.UploadData;
import com.r3ds.FileTransfer.UploadResponse;
import com.r3ds.FileTransferServiceGrpc.FileTransferServiceImplBase;

import com.r3ds.server.exception.AuthException;
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
		
		Connection conn = null;
		try {
			conn = Database.getConnection();
			AuthTools.login(
					Database.getConnection(),
					request.getCredentials().getUsername(),
					request.getCredentials().getPassword()
			);
			
			logger.info("Download request from '{}' for file '{}'",
				request.getCredentials().getUsername(), request.getFilename());
			
			// check database for path and if user can download it
			FileInfo fileInfo = FileTools.existFile(conn, request.getCredentials().getUsername(), request.getFilename());
			
			if (fileInfo.isNullFileInfo())
				throw new FileNotFoundException(String.format("File %s was not found.", request.getFilename()));
			
			int fileId = fileInfo.getFileId();
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
			
			FileTools.logActionInFile(conn, fileId, request.getCredentials().getUsername(), FileTools.LogStatus.READ);
			responseObserver.onCompleted();
			
		} catch (AuthException e) {
			System.out.println("Username and password provided are not a match.");
			responseObserver.onError(Status.INTERNAL
					.withDescription("You are not logged in.")
					.withCause(e)
					.asRuntimeException());
		} catch (SQLException e) {
			e.printStackTrace();
			responseObserver.onError(Status.INTERNAL
					.withDescription("Something unexpected happend with DB.")
					.withCause(e)
					.asRuntimeException());
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
		} finally {
			Database.closeConnection(conn);
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
				logger.info("Upload chunk #{} from '{}' for file '{}'", callCount,
					uploadData.getCredentials().getUsername(), uploadData.getFilename());
				callCount++;
				
				
				try {
					// temporary path value, user's home
					Path path = Paths.get(System.getProperty("user.home"), ".r3ds", "server",
							uploadData.getCredentials().getUsername());
					
					if (!Files.isDirectory(path)) {
						logger.info("Directory for user '{}' does not exist, creating one", uploadData.getCredentials().getUsername());
						Files.deleteIfExists(path);
						Files.createDirectories(path);
					}
					pathToFile = Paths.get(path.toString(), uploadData.getFilename()).toString();
					
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
				
				Connection conn = null;
				try {
					conn = Database.getConnection();
					
					AuthTools.login(
							conn,
							lastUploadData.getCredentials().getUsername(),
							lastUploadData.getCredentials().getPassword()
					);
					
					// check database if file already exists
					FileInfo fileInfo = FileTools.existFile(
							conn,
							lastUploadData.getCredentials().getUsername(),
							lastUploadData.getFilename()
					);
					
					if (fileInfo.isNullFileInfo()) {
						// if new, it will save in db
						int fileId = FileTools.createFileInDB(
								conn,
								lastUploadData.getCredentials().getUsername(),
								lastUploadData.getFilename(),
								pathToFile
						);
						FileTools.logActionInFile(
								conn,
								fileId,
								lastUploadData.getCredentials().getUsername(),
								FileTools.LogStatus.CREATE
						);
					} else {
						// if already exists
						pathToFile = fileInfo.getPath();
						FileTools.logActionInFile(
								conn,
								fileInfo.getFileId(),
								lastUploadData.getCredentials().getUsername(),
								FileTools.LogStatus.UPDATE_CONTENT
						);
					}
					
					responseObserver.onCompleted();
					
				} catch (SQLException e) {
					e.printStackTrace();
					responseObserver.onError(Status.INTERNAL
							.withDescription("Error uploading file, please try again.")
							.withCause(e)
							.asRuntimeException());
				} catch (AuthException e) {
					System.out.println("Username and password provided are not a match.");
					responseObserver.onError(Status.INTERNAL
							.withDescription("You are not logged in.")
							.withCause(e)
							.asRuntimeException());
				} finally {
					Database.closeConnection(conn);
				}
				
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
		};
	}
}