package com.r3ds.server;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class FileTools {
	
	/**
	 *
	 * @param conn
	 * @param username
	 * @param filename
	 * @return
	 * @throws SQLException
	 */
	public static String existFile(Connection conn, String username, String filename) throws SQLException {
		PreparedStatement stmt = conn.prepareStatement("SELECT local_path " +
				"FROM file " +
				"WHERE owner_username = ?" +
				"AND user_filename = ?");
		stmt.setString(1, username);
		stmt.setString(2, filename);
		ResultSet rs = stmt.executeQuery();
		stmt.close();
		if (rs.next()) {
			String path = rs.getString("local_path");
			rs.close();
			return path;
		}
		
		stmt = conn.prepareStatement("SELECT local_path " +
				"FROM shared_file " +
				"JOIN file ON file.file_id = shared_file.file_id " +
				"WHERE username = ?" +
				"AND user_filename = ?");
		stmt.setString(1, username);
		stmt.setString(2, filename);
		rs = stmt.executeQuery();
		stmt.close();
		if (rs.next()) {
			String path = rs.getString("local_path");
			rs.close();
			return path;
		}
		
		return null;
	}
}
