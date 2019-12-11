package com.r3ds.backupsv;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import com.google.protobuf.ByteString;
import com.r3ds.Common.Chunk;
import com.r3ds.Common.Chunk.Builder;
import com.r3ds.IntegrityCheck.DownloadBackupRequest;
import com.r3ds.IntegrityCheckServiceGrpc.IntegrityCheckServiceImplBase;
import com.r3ds.server.FileTools;
import com.r3ds.server.exception.DatabaseException;
import com.r3ds.server.file.FileInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

public class IntegrityCheckServiceImpl extends IntegrityCheckServiceImplBase {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private static final int BUFFER_SIZE = 16 * 1024;

	private final FileTools fileTools;

	public IntegrityCheckServiceImpl(FileTools fileTools) {
		this.fileTools = fileTools;
	}

	@Override
	public void downloadBackup(DownloadBackupRequest request, StreamObserver<Chunk> responseObserver) {

		BufferedInputStream reader = null;
		try {
			logger.info("Backup download request for file '{}' (owner: '{}')",
				request.getFile().getFilename(),
				request.getFile().getOwnerUsername());

			// check database for path and if user can download it
			FileInfo fileInfo = fileTools.getFileInfo(
				request.getFile().getFilename(),
				request.getFile().getOwnerUsername()
			);

			// should never happen
			if (fileInfo.isNewFile())
				throw new FileNotFoundException(String.format("File %s was not found.", fileInfo.getFilename()));

			reader = new BufferedInputStream(
				new FileInputStream(fileInfo.getPath()));
			byte[] buffer = new byte[BUFFER_SIZE];
			int length;

			Builder response = Chunk.newBuilder();
			while ((length = reader.read(buffer, 0, BUFFER_SIZE)) != -1) {
				responseObserver.onNext(response
					.setContent(ByteString.copyFrom(buffer, 0, length))
					.build());
			}
			responseObserver.onCompleted();

		} catch (DatabaseException e) {
			e.printStackTrace();
			responseObserver.onError(Status.INTERNAL
					.withDescription("Something unexpected happened with DB.")
					.withCause(e)
					.asRuntimeException());
		} catch (FileNotFoundException e) {
			logger.error("File not found: {}", e.getMessage());
			responseObserver.onError(Status.NOT_FOUND
				.withDescription("File not found: " + request.getFile().getFilename())
				.withCause(e)
				.asRuntimeException());
		} catch (IOException e) {
			e.printStackTrace();
			responseObserver.onError(Status.INTERNAL
				.withDescription("Error downloading file, please try again.")
				.withCause(e)
				.asRuntimeException());
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					e.printStackTrace();
					logger.error("Error closing stream");
				}
			}
		}
	}
}
