package com.r3ds.server;

import com.r3ds.server.file.FileInfo;

import java.sql.*;

public class FileTools {
	
	public enum LogStatus {
		CREATE,
		READ,
		UPDATE_NAME,
		UPDATE_CONTENT,
		DELETE
	}
	
	/**
	 *
	 * @param conn
	 * @param username
	 * @param filename
	 * @return
	 * @throws SQLException
	 */
	public static FileInfo existFile(Connection conn, String username, String filename) throws SQLException {
		PreparedStatement stmt = conn.prepareStatement("SELECT file.file_id, local_path " +
				"FROM file " +
				"JOIN user_file ON file.file_id = user_file.file_id " +
				"WHERE username = ? " +
				"AND filename = ?");
		stmt.setString(1, username);
		stmt.setString(2, filename);
		ResultSet rs = stmt.executeQuery();
		if (rs.next()) {
			FileInfo fileInfo = new FileInfo(rs.getInt("file_id"), rs.getString("local_path"));
			rs.close();
			stmt.close();
			return fileInfo;
		}
		return new FileInfo();
	}
	
	/**
	 *
	 * @param conn
	 * @param username
	 * @param filename
	 * @param path
	 * @return
	 * @throws SQLException
	 */
	public static int createFileInDB(Connection conn, String username, String filename, String path) throws SQLException {
		PreparedStatement stmt = conn.prepareStatement("INSERT INTO file(filename, local_path, shared) " +
				"VALUES(?, ?, ?)");
		stmt.setString(1, filename);
		stmt.setString(2, path);
		stmt.setBoolean(3, false);
		int fileId = stmt.executeUpdate();
		stmt.close();
		
		stmt = conn.prepareStatement("INSERT INTO user_file(username, file_id, shared_key) " +
				"VALUES(?, ?, ?)");
		stmt.setString(1, username);
		stmt.setInt(2, fileId);
		stmt.setNull(3, Types.VARCHAR);
		stmt.executeUpdate();
		stmt.close();
		
		return fileId;
	}
	
	/**
	 *
	 * @param conn
	 * @param fileId
	 * @param username
	 * @param status
	 * @throws SQLException
	 */
	public static void logActionInFile(Connection conn, int fileId, String username, LogStatus status) throws SQLException {
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
