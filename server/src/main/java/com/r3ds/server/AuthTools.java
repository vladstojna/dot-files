package com.r3ds.server;

import com.r3ds.server.exception.AuthException;
import com.r3ds.server.exception.DatabaseException;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AuthTools {

	private final Database db;

	public AuthTools(Database db) {
		this.db = db;
	}
	
	/**
	 *
	 * @param username
	 * @param password
	 * @throws AuthException
	 */
	public void signup(String username, String password) throws AuthException, DatabaseException {
		PreparedStatement stmt = null;
		
		try {
			db.openConnection();
			stmt = db.getConnection()
					.prepareStatement("INSERT INTO r3ds_user(username, password) VALUES (?, ?)");
			stmt.setString(1, username);
			stmt.setString(2, BCrypt.hashpw(password, BCrypt.gensalt()));
			stmt.executeUpdate();
			
		} catch (SQLException e) {
			if (e.getSQLState().equals("23505"))
				throw new AuthException("There is already an account with that username.", e);
			else {
				throw new DatabaseException("Something happened with database.", e);
			}
		} finally {
			if (stmt != null) {
				try {
					stmt.close();
				} catch (SQLException e) {
					throw new DatabaseException("Something happened in database", e);
				}
			}
			if (db != null && db.getConnection() != null)
				db.closeConnection();
		}
	}
	
	/**
	 *
	 * @param username
	 * @param password
	 * @throws AuthException
	 */
	public void login(String username, String password) throws AuthException, DatabaseException {
		PreparedStatement stmt = null;
		ResultSet rs = null;
		
		try {
			db.openConnection();
			stmt = db.getConnection().prepareStatement("SELECT password " +
					"FROM r3ds_user " +
					"WHERE username = ?"
			);
			stmt.setString(1, username);
			rs = stmt.executeQuery();
			String passwordInDb = null;
			if (rs.next())
				passwordInDb = rs.getString("password");
			
			if (passwordInDb == null || !BCrypt.checkpw(password, passwordInDb))
				throw new AuthException("There are no user with that username and password combination.");
		} catch (SQLException e) {
			e.printStackTrace();
			throw new DatabaseException("Something happened with database.", e);
		} finally {
			try {
				if (rs != null)
					rs.close();
				
				if (stmt != null)
					stmt.close();
			} catch (SQLException e) {
				throw new DatabaseException("Something happened with database.", e);
			}
			
			if (db != null && db.getConnection() != null)
				db.closeConnection();
		}
	}
}
