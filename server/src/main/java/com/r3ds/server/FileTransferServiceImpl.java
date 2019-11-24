package com.r3ds.server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.stub.StreamObserver;
import io.grpc.Status;


public class FileTransferServiceImpl extends FileTransferServiceImplBase {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private static final int BUFFER_SIZE = 4096;
	
	private enum LogStatus {
		CREATE,
		READ,
		UPDATE_NAME,
		UPDATE_CONTENT,
		DELETE
	}

	@Override
	public void download(DownloadRequest request, StreamObserver<Chunk> responseObserver) {
		try {
			AuthTools.login(
					Database.getConnection(),
					request.getCredentials().getUsername(),
					request.getCredentials().getPassword()
			);
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
		}
		
		try {

			logger.info("Download request from '{}' for file '{}'",
				request.getCredentials().getUsername(), request.getFilename());

			// check database for path and if user can download it
			// temporary path value, user's home
			String path = Paths.get(System.getProperty("user.home"), "r3ds-files", "server", request.getFilename()).toString();

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
				
				// temporary path value, user's home
				String path = Paths.get(System.getProperty("user.home"), "r3ds-files", "server",
						uploadData.getCredentials().getUsername(), uploadData.getFilename()).toString();
				pathToFile = path;
						
				byte[] content = uploadData.getContent().toByteArray();

				try {
					if (writer == null) {
						writer = new BufferedOutputStream(new FileOutputStream(path));
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
				
				try {
					Connection conn = Database.getConnection();
					
					AuthTools.login(
							conn,
							lastUploadData.getCredentials().getUsername(),
							lastUploadData.getCredentials().getPassword()
					);
					
					String path = pathToFile;
					boolean fileAlreadyExists = false;
					
					// check database if file already exists
					String filePathInDB = FileTools.existFile(
							conn,
							lastUploadData.getCredentials().getUsername(),
							lastUploadData.getFilename()
					);
					
					if (filePathInDB != null) {
						path = filePathInDB;
						fileAlreadyExists = true;
					}
					
					PreparedStatement stmt = conn.prepareStatement("INSERT INTO file(owner_username, user_filename, local_path) " +
							"VALUES(?, ?, ?) ON DUPLICATE KEY UPDATE file_id = LAST_INSERT_ID(file_id)", Statement.RETURN_GENERATED_KEYS);
					stmt.setString(1, lastUploadData.getCredentials().getUsername());
					stmt.setString(2, lastUploadData.getFilename());
					stmt.setString(3, path);
					stmt.executeUpdate();
					int fileId = stmt.getGeneratedKeys().getInt(1);
					stmt.close();
					
					LogStatus logStatus = (fileAlreadyExists ? LogStatus.UPDATE_CONTENT : LogStatus.CREATE);
					logActionInFile(conn, fileId, lastUploadData.getCredentials().getUsername(), logStatus);
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
				}
				
				responseObserver.onCompleted();
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
	
	/**
	 *
	 * @param conn
	 * @param fileId
	 * @param username
	 * @param status
	 * @throws SQLException
	 */
	private void logActionInFile(Connection conn, int fileId, String username, LogStatus status) throws SQLException {
		PreparedStatement stmt = conn.prepareStatement("INSERT INTO log_file(file_id, username, status) " +
				"VALUES(?, ?, ?)");
		stmt.setInt(1, fileId);
		stmt.setString(2, username);
		String statusText = "";
		switch (status) {
			case CREATE:
				statusText = "Create";
				break;
			case READ:
				statusText = "Read";
				break;
			case UPDATE_NAME:
				statusText = "Update Name";
				break;
			case UPDATE_CONTENT:
				statusText = "Update Content";
				break;
			case DELETE:
				statusText = "Delete";
				break;
		}
		stmt.setString(3, statusText);
		stmt.executeUpdate();
		stmt.close();
	}
}