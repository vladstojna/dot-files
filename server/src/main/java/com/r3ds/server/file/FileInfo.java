package com.r3ds.server.file;

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
	
	public FileInfo(String currentUsername, String ownerUsername, int fileId, String filename, String path, boolean shared) {
		this.currentUsername = currentUsername;
		this.ownerUsername = ownerUsername;
		this.fileId = fileId;
		this.filename = filename;
		this.previousFilename = filename;
		this.path = path;
		this.previousPath = path;
		this.shared = shared;
		this.previousShared = shared;
	}
	
	public FileInfo(String currentUsername, String ownerUsername, String filename, boolean shared) {
		this(currentUsername, ownerUsername, 0, filename, null, shared);
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
	
	public void commit() {
		if (!this.previousPath.equals(this.path)) {
			//Files.move(Paths.get(this.path) , Paths.get(this.previousPath), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		}
		
		this.previousPath = this.path;
		this.previousFilename = this.filename;
		this.previousShared = this.shared;
	}
	
	public void rollback() {
		this.path = this.previousPath;
		this.filename = this.previousFilename;
		this.shared = this.previousShared;
	}
	
	public boolean isNewFile() {
		return this.fileId == 0 && path == null;
	}
}
