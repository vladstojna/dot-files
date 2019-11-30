package com.r3ds.server;

import com.r3ds.server.exception.DatabaseException;
import com.r3ds.server.file.FileInfo;

import java.sql.*;
import java.time.LocalDateTime;

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
	 * @param username
	 * @param filename
	 * @return
	 * @throws DatabaseException
	 */
	public FileInfo existFileInDB(String username, String filename) throws DatabaseException {
		Database db = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;
		FileInfo fileInfo = new FileInfo(username, filename);
		
		try {
			db = new Database();
			stmt = db.getConnection().prepareStatement("SELECT file.file_id, local_path " +
					"FROM file " +
					"JOIN user_file ON file.file_id = user_file.file_id " +
					"WHERE username = ? " +
					"AND filename = ?");
			stmt.setString(1, username);
			stmt.setString(2, filename);
			rs = stmt.executeQuery();
			if (rs.next()) {
				fileInfo.setFileId(rs.getInt("file_id"));
				fileInfo.setPath(rs.getString("local_path"));
			}
			
		} catch (SQLException e) {
			throw new DatabaseException("Something happened with database", e);
		} finally {
			try {
				if (rs != null)
					rs.close();
				
				if (stmt != null)
					stmt.close();
			} catch (SQLException e) {
				throw new DatabaseException("Something happened with database", e);
			} finally {
				if (db != null && db.getConnection() != null)
					db.closeConnection();
			}
		}
		
		return fileInfo;
	}
	
	/**
	 *
	 * @param fileInfo
	 * @param path
	 * @return
	 * @throws DatabaseException
	 */
	public FileInfo createFileInDB(FileInfo fileInfo, String path) throws DatabaseException {
		Database db = null;
		PreparedStatement stmt = null;
		
		try {
			db = new Database();
			db.getConnection().setAutoCommit(false);
			
			stmt = db.getConnection().prepareStatement("INSERT INTO file(filename, local_path, shared) " +
					"VALUES(?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
			stmt.setString(1, fileInfo.getFilename());
			stmt.setString(2, path);
			stmt.setBoolean(3, false);
			stmt.executeUpdate();
			
			int fileId = 0;
			try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
				if (generatedKeys.next())
					fileId = generatedKeys.getInt("file_id");
				else
					throw new DatabaseException("File was not inserted correctly.");
			}
			stmt.close();
			
			stmt = db.getConnection().prepareStatement("INSERT INTO user_file(username, file_id, shared_key) " +
					"VALUES(?, ?, ?)");
			stmt.setString(1, fileInfo.getUsername());
			stmt.setInt(2, fileId);
			stmt.setNull(3, Types.VARCHAR);
			stmt.executeUpdate();
			stmt.close();
			
			fileInfo.setFileId(fileId);
			fileInfo.setPath(path);
			
			this.logActionInFile(db.getConnection(), fileInfo, FileTools.LogStatus.CREATE);
			
			db.getConnection().commit();
			fileInfo.commit();
			
			return fileInfo;
			
		} catch (SQLException e) {
			if (db != null && db.getConnection() != null) {
				try {
					db.getConnection().rollback();
					fileInfo.rollback();
				} catch (SQLException ex) {
					throw new DatabaseException("Something happened with database.", ex);
				}
			}
			throw new DatabaseException("Something happened with database.", e);
		} finally {
			try {
				if (stmt != null)
					stmt.close();
			} catch (SQLException e) {
				throw new DatabaseException("Something happened with database.", e);
			}
			
			if (db != null && db.getConnection() != null)
				db.closeConnection();
		}
	}
	
	/**
	 *
	 * @param fileInfo
	 * @return
	 * @throws DatabaseException
	 */
	public FileInfo getFileToReadFromDB(FileInfo fileInfo) throws DatabaseException {
		this.logActionInFile((new Database()).getConnection(), fileInfo, LogStatus.READ);
		return fileInfo;
	}
	
	/**
	 *
	 * @param fileInfo
	 * @return
	 */
	public FileInfo updateContentFileInDB(FileInfo fileInfo) throws DatabaseException {
		this.logActionInFile((new Database()).getConnection(), fileInfo, LogStatus.UPDATE_CONTENT);
		return fileInfo;
	}
	
	/**
	 *
	 * @param conn
	 * @param fileInfo
	 * @param status
	 * @throws SQLException
	 */
	public void logActionInFile(Connection conn, FileInfo fileInfo, LogStatus status) throws DatabaseException {
		PreparedStatement stmt = null;
		
		try {
			stmt = conn.prepareStatement("INSERT INTO log_file(file_id, date_time, username, status) " +
					"VALUES(?, ?, ?, CAST(? AS log_status))");
			stmt.setInt(1, fileInfo.getFileId());
			stmt.setObject(2, LocalDateTime.now());
			stmt.setString(3, fileInfo.getUsername());
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
			stmt.setString(4, statusText);
			stmt.executeUpdate();
		} catch (SQLException e) {
			throw new DatabaseException("Something happened in database", e);
		} finally {
			if (stmt != null)
				try {
					stmt.close();
				} catch (SQLException e) {
					throw new DatabaseException("Something happened in database", e);
				}
		}
	}
}
