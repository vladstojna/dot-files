package com.r3ds.server.file;

public class FileInfo {
	private int fileId;
	private String path;
	
	public FileInfo(int fileId, String path) {
		this.fileId = fileId;
		this.path = path;
	}
	
	public FileInfo() {
		this(0, null);
	}
	
	public int getFileId() {
		return fileId;
	}
	
	public String getPath() {
		return path;
	}
	
	public boolean isNullFileInfo() {
		return this.fileId == 0 && path == null;
	}
}
