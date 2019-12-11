package com.r3ds.server.file;

import com.r3ds.server.exception.FileInfoException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FileInfo {
	private String currentUsername;
	private String ownerUsername;
	private int fileId;
	private String filename;
	private String previousFilename;
	private String path;
	private String previousPath;
	private boolean shared;
	private boolean previousShared;
	private byte[] sharedKey;
	private byte[] previousSharedKey;
	
	public FileInfo(String currentUsername, String ownerUsername, int fileId, String filename,
	                String path, boolean shared, byte[] sharedKey) {
		this.currentUsername = currentUsername;
		this.ownerUsername = ownerUsername;
		this.fileId = fileId;
		this.filename = filename;
		this.previousFilename = filename;
		this.path = path;
		this.previousPath = path;
		this.shared = shared;
		this.previousShared = shared;
		this.sharedKey = sharedKey;
		this.previousSharedKey = sharedKey;
	}
	
	public FileInfo(String currentUsername, String ownerUsername, String filename, boolean shared) {
		this(currentUsername, ownerUsername, 0, filename, null, shared, null);
	}
	
	public FileInfo(String currentUsername, String ownerUsername, String filename) {
		this(currentUsername, ownerUsername, 0, filename, null, false, null);
	}
	
	public String getCurrentUsername() {
		return currentUsername;
	}
	
	public String getOwnerUsername() {
		return ownerUsername;
	}
	
	public String getFilename() {
		return filename;
	}
	
	public int getFileId() {
		return fileId;
	}
	
	public void setFileId(int fileId) {
		this.fileId = fileId;
	}
	
	public String getPath() {
		return path;
	}
	
	public void setPath(String path) {
		this.path = path;
	}
	
	public boolean isShared() {
		return shared;
	}
	
	public void setShared(boolean shared) {
		this.shared = shared;
	}
	
	public byte[] getSharedKey() {
		return sharedKey;
	}
	
	public void setSharedKey(byte[] sharedKey) {
		this.sharedKey = sharedKey;
	}
	
	public void commit() throws FileInfoException {
		if (this.previousPath != null && this.path != null && !this.previousPath.equals(this.path)) {
			try {
				Files.move(Paths.get(this.path), Paths.get(this.previousPath), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				throw new FileInfoException("There was an error updating the file in server.", e);
			}
		}
	
		this.previousPath = this.path;
		this.previousFilename = this.filename;
		this.previousShared = this.shared;
		this.previousSharedKey = this.sharedKey;
	}
	
	public void rollback() {
		this.path = this.previousPath;
		this.filename = this.previousFilename;
		this.shared = this.previousShared;
		this.sharedKey = this.previousSharedKey;
	}
	
	public boolean isNewFile() {
		return this.fileId == 0 && path == null;
	}
}
