package com.r3ds.mainsv;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.r3ds.IntegrityCheckServiceGrpc;
import com.r3ds.Common.Chunk;
import com.r3ds.Common.FileData;
import com.r3ds.IntegrityCheck.DownloadBackupRequest;
import com.r3ds.server.FileTools;
import com.r3ds.server.exception.DatabaseException;
import com.r3ds.server.file.FileInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IntegrityChecker {

	private final Logger logger = LoggerFactory.getLogger(IntegrityChecker.class);

	private static long INTERVAL = 10 * 1000; //30 * 60 * 1000; // every 30 minutes

	private static Lock lock = new ReentrantLock();

	private final FileTools fileTools;
	private final IntegrityCheckServiceGrpc.IntegrityCheckServiceBlockingStub backupStub;

	public IntegrityChecker(FileTools fileTools, IntegrityCheckServiceGrpc.IntegrityCheckServiceBlockingStub backupStub) {
		this.fileTools = fileTools;
		this.backupStub = backupStub;
	}

	public static void lock() {
		lock.lock();
	}

	public static void unlock() {
		lock.unlock();
	}

	public void doIntegrityCheck() {
		while (true) {
			try {
				Thread.sleep(INTERVAL);
			} catch (InterruptedException e) {
				logger.warn("Shared check interrupted!");
				return;
			}
			lock();
			Path path = null;
			BufferedOutputStream writer = null;
			try {
				List<FileInfo> fileList = fileTools.getAllFiles();
				for (FileInfo info : fileList) {
					Iterator<Chunk> chunks = backupStub.downloadBackup(DownloadBackupRequest.newBuilder()
						.setFile(FileData.newBuilder()
							.setFilename(info.getFilename())
							.setOwnerUsername(info.getOwnerUsername())
							.build())
						.build());

					path = Paths.get(info.getPath());
					if (!Files.isDirectory(path.getParent()))
						Files.createDirectories(path.getParent());
					writer = new BufferedOutputStream(
						new FileOutputStream(info.getPath()));
					while (chunks.hasNext()) {
						writer.write(chunks.next().getContent().toByteArray());
					}
					writer.flush();
				}
			} catch (DatabaseException e) {
				logger.error("Error during integrity check", e);
				continue;
			} catch (IOException e) {
				logger.error("Error during integrity check", e);
				if (path != null) {
					try {
						Files.deleteIfExists(path);
					} catch (IOException ex) {
						e.printStackTrace();
						logger.error("Error during cleanup", ex);
					}
				}
				continue;
			} finally {
				unlock();
				if (writer != null) {
					try {
						writer.close();
					} catch (IOException e) {
						logger.error("Error when closing writer", e);
					}
				}
			}
		}
	}

	public static void start(FileTools fileTools, IntegrityCheckServiceGrpc.IntegrityCheckServiceBlockingStub backupStub) {
		IntegrityChecker ic = new IntegrityChecker(fileTools, backupStub);
		Runnable r = new Runnable(){
			@Override
			public void run() {
				ic.doIntegrityCheck();
			}
		};
		new Thread(r).start();
	}

}