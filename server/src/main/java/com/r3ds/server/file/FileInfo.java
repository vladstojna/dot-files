package com.r3ds.server.file;

public class FileInfo {
	private String username;
	private String filename;
	private String previousFilename;
	private int fileId;
	private int previousFileId;
	private String path;
	private String previousPath;
	
	public FileInfo(String username, String filename, int fileId, String path) {
		this.username = username;
		this.filename = filename;
		this.previousFilename = filename;
		this.fileId = fileId;
		this.previousFileId = fileId;
		this.path = path;
		this.previousPath = path;
	}
	
	public FileInfo(String username, String filename) {
		this(username, filename, 0, null);
	}
	
	public String getUsername() {
		return username;
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
	
	public void commit() {
		this.previousFileId = fileId;
		this.previousPath = previousPath;
	}
	
	public void rollback() {
		this.fileId = this.previousFileId;
		this.path = this.previousPath;
	}
	
	public boolean isNewFile() {
		return this.fileId == 0 && path == null;
	}
}
