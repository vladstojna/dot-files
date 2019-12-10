package com.r3ds.server;

import com.r3ds.server.exception.AuthException;
import com.r3ds.server.exception.DatabaseException;
import com.r3ds.server.exception.FileInfoException;
import com.r3ds.server.file.FileInfo;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileTools {
	
	public enum LogStatus {
		CREATE,
		READ,
		UPDATE_NAME,
		UPDATE_CONTENT,
		SHARED_BY,
		SHARED_WITH,
		DELETE
	}
	
	/**
	 *
	 * @param currentUsername
	 * @param ownerUsername
	 * @param filename
	 * @param shared
	 * @return
	 * @throws DatabaseException
	 */
	public FileInfo existFileInDB(String currentUsername, String ownerUsername, String filename,
	                              boolean shared) throws DatabaseException {
		Database db = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;
		FileInfo fileInfo = new FileInfo(currentUsername, ownerUsername, filename, shared);
		
		try {
			db = new Database();
			stmt = db.getConnection().prepareStatement("SELECT file.file_id, file.shared, file.local_path " +
					"FROM file " +
					"JOIN user_file ON file.file_id = user_file.file_id " +
					"WHERE file.owner_username = ? " +
					"AND file.filename = ? " +
					"AND file.shared = ?" +
					"AND user_file.username = ? ");
			stmt.setString(1, ownerUsername);
			stmt.setString(2, filename);
			stmt.setBoolean(3, shared);
			stmt.setString(4, currentUsername);
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
	public FileInfo createFileInDB(FileInfo fileInfo, String path, boolean shared) throws DatabaseException {
		Database db = null;
		PreparedStatement stmt = null;
		
		try {
			db = new Database();
			db.getConnection().setAutoCommit(false);
			
			stmt = db.getConnection().prepareStatement("INSERT INTO file(filename, shared, owner_username, local_path) " +
					"VALUES(?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
			stmt.setString(1, fileInfo.getFilename());
			stmt.setBoolean(2, shared);
			stmt.setString(3, fileInfo.getOwnerUsername());
			stmt.setString(4, fileInfo.getPath());
			stmt.executeUpdate();
			
			int fileId = 0;
			try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
				if (generatedKeys.next())
					fileId = generatedKeys.getInt("file_id");
				else
					throw new DatabaseException("File was not inserted correctly.");
			}
			stmt.close();
			
			stmt = db.getConnection().prepareStatement("INSERT INTO user_file(username, file_id," +
					" shared_key, local_path) " +
					"VALUES(?, ?, ?, ?)");
			stmt.setString(1, fileInfo.getOwnerUsername());
			stmt.setInt(2, fileId);
			stmt.setNull(3, Types.VARCHAR);
			stmt.setString(4, path);
			stmt.executeUpdate();
			stmt.close();
			
			fileInfo.setFileId(fileId);
			fileInfo.setPath(path);
			
			this.logActionInFile(db.getConnection(), fileInfo, fileInfo.getOwnerUsername(), FileTools.LogStatus.CREATE);
			
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
	 * @param usernameSending
	 * @param usernameReceiving
	 * @param sharedKeyFromUserSending
	 * @param sharedKeyFromUserReceiving - is the real key of the file ciphered with the certificate of receiving user
	 * @return
	 * @throws DatabaseException
	 */
	public FileInfo shareFile(FileInfo fileInfo, String usernameSending, String usernameReceiving,
	                          String sharedKeyFromUserSending, String sharedKeyFromUserReceiving)
			throws DatabaseException, FileInfoException {
		Database db = null;
		PreparedStatement stmt = null;
		
		try {
			db = new Database();
			db.getConnection().setAutoCommit(false);
			
			// update shared status and local path in server
			stmt = db.getConnection().prepareStatement("UPDATE file " +
					"SET shared = ?, " +
					"local_path = ? " +
					"WHERE file_id = ?");
			stmt.setBoolean(1, true);
			stmt.setInt(2, fileInfo.getFileId());
			stmt.setString(3, fileInfo.getPath());
			stmt.executeUpdate();
			stmt.close();
			
			// update key from who is sending
			stmt = db.getConnection().prepareStatement("UPDATE user_file " +
					"SET shared_key = ? " +
					"WHERE username = ? " +
					"AND file_id = ?");
			stmt.setString(1, sharedKeyFromUserSending);
			stmt.setString(2, usernameSending);
			stmt.setInt(3, fileInfo.getFileId());
			stmt.executeUpdate();
			stmt.close();
			
			// create permission to user who will receive file
			// shared_key is null because sharedKeyFromUserReceiving is the key of shared file
			// ciphered with public key of user with usernameReceiving
			stmt = db.getConnection().prepareStatement("INSERT INTO user_file(username, file_id, shared_key) " +
					"VALUES(?, ?, ?)");
			stmt.setString(1, usernameReceiving);
			stmt.setInt(2, fileInfo.getFileId());
			stmt.setNull(3, Types.VARCHAR);
			stmt.executeUpdate();
			stmt.close();
			
			// insert in file_in_transition to notify user who is supposed to receive the file
			stmt = db.getConnection().prepareStatement("INSERT INTO file_in_transition(username_send, file_id, " +
						"username_receive, shared_key)" +
					"VALUES(?, ?, ?, ?)");
			stmt.setString(1, usernameSending);
			stmt.setInt(2, fileInfo.getFileId());
			stmt.setString(3, usernameReceiving);
			stmt.setString(4, sharedKeyFromUserReceiving);
			stmt.executeUpdate();
			stmt.close();
			
			this.logActionInFile(db.getConnection(), fileInfo, usernameSending, LogStatus.SHARED_BY);
			
			db.getConnection().commit();
			fileInfo.commit();
		} catch (SQLException e) {
			if (e.getSQLState().equals("23505")) {
				throw new FileInfoException(
						"This file has a path that already exists. Please delete the following file: " + fileInfo.getPath(),
						e
				);
			}
			
			if(db != null && db.getConnection() != null) {
				try {
					db.getConnection().rollback();
				} catch (SQLException ex) {
					throw new DatabaseException("Something wrong happened with DB.", ex);
				}
			}
			
			throw new DatabaseException("Something wrong happened with DB.", e);
		} finally {
			if (stmt != null) {
				try {
					stmt.close();
				} catch (SQLException e) {
					throw new DatabaseException("Something happened to DB.", e);
				}
			}
			
			if (db != null && db.getConnection() != null)
				db.closeConnection();
		}
		
		return fileInfo;
	}
	
	/**
	 *
	 * @param username
	 * @return
	 * @throws DatabaseException
	 */
	public List<FileInfo> getFilesToShare(String username) throws DatabaseException {
		Database db = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;
		
		List<FileInfo> files = new ArrayList<>();
		
		try {
			stmt = db.getConnection().prepareStatement("SELECT file_id, filename, owner_username, local_path, shared " +
					"FROM file_in_transition " +
					"JOIN file ON file_in_transition.file_id = file.file_id " +
					"WHERE username_receiving = ?");
			stmt.setString(1, username);
			rs = stmt.executeQuery();
			
			while (rs.next()) {
				files.add(
						new FileInfo(
								username,
								rs.getString("owner_username"),
								rs.getInt("file_id"),
								rs.getString("filename"),
								rs.getString("local_path"),
								rs.getBoolean("shared")
						)
				);
			}
			
		} catch (SQLException e) {
			throw new DatabaseException("Something happened in DB.", e);
		} finally {
			try {
				if (rs != null)
					rs.close();
				
				if (stmt != null)
					stmt.close();
			} catch (SQLException e) {
				throw new DatabaseException("Something happened to DB.", e);
			}
			
			if (db != null && db.getConnection() != null)
				db.closeConnection();
		}
		
		return files;
	}
	
	/**
	 *
	 * @param fileInfo
	 * @param usernameSending
	 * @param usernameReceiving
	 * @param sharedKeyFromUserReceiving
	 */
	public void fileIsTotallyShared(FileInfo fileInfo, String usernameSending, String usernameReceiving,
	                                String sharedKeyFromUserReceiving) throws DatabaseException {
		Database db = null;
		PreparedStatement stmt = null;
		
		try {
			db = new Database();
			db.getConnection().setAutoCommit(false);
			
			// update people who have access to file
			stmt = db.getConnection().prepareStatement("UPDATE user_file " +
					"SET shared_key = ? " +
					"WHERE username = ? " +
					"AND file_id = ?");
			stmt.setString(1, sharedKeyFromUserReceiving);
			stmt.setString(2, usernameReceiving);
			stmt.setInt(3, fileInfo.getFileId());
			stmt.executeUpdate();
			stmt.close();
			
			stmt = db.getConnection().prepareStatement("DELETE FROM file_in_transition " +
					"WHERE username_sending = ? " +
					"AND file_id = ? " +
					"AND username_receiving = ?");
			stmt.setString(1, usernameSending);
			stmt.setInt(2, fileInfo.getFileId());
			stmt.setString(3, usernameReceiving);
			stmt.executeUpdate();
			stmt.close();
			
			this.logActionInFile(db.getConnection(), fileInfo, usernameReceiving, LogStatus.SHARED_WITH);
			
			db.getConnection().commit();
		} catch (SQLException e) {
			if(db != null && db.getConnection() != null) {
				try {
					db.getConnection().rollback();
				} catch (SQLException ex) {
					throw new DatabaseException("Something wrong happened with DB.", ex);
				}
			}
			
			throw new DatabaseException("Something wrong happened with DB.", e);
		} finally {
			if (stmt != null) {
				try {
					stmt.close();
				} catch (SQLException e) {
					throw new DatabaseException("Something happened in DB.", e);
				}
			}
			
			if (db != null && db.getConnection() != null)
				db.closeConnection();
		}
	
	}
	
	/**
	 *
	 * @param fileInfo
	 * @param username
	 * @return
	 * @throws DatabaseException
	 */
	public FileInfo saveFileReadInLog(FileInfo fileInfo, String username) throws DatabaseException {
		this.logActionInFile((new Database()).getConnection(), fileInfo, username, LogStatus.READ);
		return fileInfo;
	}
	
	/**
	 *
	 * @param fileInfo
	 * @param username
	 * @return
	 * @throws DatabaseException
	 */
	public FileInfo saveFileWriteInLog(FileInfo fileInfo, String username) throws DatabaseException {
		this.logActionInFile((new Database()).getConnection(), fileInfo, username, LogStatus.UPDATE_CONTENT);
		return fileInfo;
	}
	
	/**
	 *
	 * @param conn
	 * @param fileInfo
	 * @param status
	 * @throws SQLException
	 */
	private void logActionInFile(Connection conn, FileInfo fileInfo, String username, LogStatus status) throws DatabaseException {
		PreparedStatement stmt = null;
		
		try {
			stmt = conn.prepareStatement("INSERT INTO log_file(file_id, date_time, username, status) " +
					"VALUES(?, ?, ?, CAST(? AS log_status))");
			stmt.setInt(1, fileInfo.getFileId());
			stmt.setObject(2, LocalDateTime.now());
			stmt.setString(3, username);
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
				case SHARED_BY:
					statusText = "Share By";
					break;
				case SHARED_WITH:
					statusText = "Share With";
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
	
	/**
	 *
	 * @param ownerUsername
	 * @param shared
	 * @return
	 */
	public Path getRelativePathToDirectoryForUsername(String ownerUsername, boolean shared) {
		if (shared)
			return Paths.get("shared", ownerUsername);
		else
			return Paths.get(ownerUsername);
	}
	
	/**
	 *
	 * @param ownerUsername
	 * @param shared
	 * @return
	 */
	public Path getLocalPathToDirectoryForUsername(String ownerUsername, boolean shared) {
		return Paths.get(System.getProperty("user.home"), ".r3ds", "server",
				getRelativePathToDirectoryForUsername(ownerUsername, shared).toString());
	}
	
	/**
	 *
	 * @param ownerUsername
	 * @param filename
	 * @param shared
	 * @return
	 */
	public Path getRelativePathForUsernameAndFilename(String ownerUsername, String filename, boolean shared) {
		if (shared)
			return Paths.get("shared", ownerUsername, filename);
		else
			return Paths.get(ownerUsername, filename);
	}
	
	/**
	 *
	 * @param ownerUsername
	 * @param filename
	 * @param shared
	 * @return
	 */
	public Path getLocalPathForUsernameAndFilename(String ownerUsername, String filename, boolean shared) {
		return Paths.get(System.getProperty("user.home"), ".r3ds", "server",
				getRelativePathForUsernameAndFilename(ownerUsername, filename, shared).toString());
	}
}
