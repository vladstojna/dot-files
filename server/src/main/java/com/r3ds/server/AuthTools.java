package com.r3ds.server;

import com.r3ds.server.exception.AuthException;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AuthTools {
	
	/**
	 *
	 * @param conn
	 * @param username
	 * @param password
	 * @throws AuthException
	 * @throws SQLException
	 */
	public static void signup(Connection conn, String username, String password) throws AuthException, SQLException {
		try {
			PreparedStatement stmt = conn.prepareStatement("INSERT INTO r3ds_user(username, password) VALUES (?, ?)");
			stmt.setString(1, username);
			stmt.setString(2, BCrypt.hashpw(password, BCrypt.gensalt()));
			stmt.executeUpdate();
			stmt.close();
		} catch (SQLException e) {
			if (e.getSQLState().equals("23000"))
				throw new AuthException("There is already an account with that username.", e);
			else {
				e.printStackTrace();
				throw e;
			}
		} finally {
			Database.closeConnection(conn);
		}
	}
	
	/**
	 *
	 * @param conn
	 * @param username
	 * @param password
	 * @throws AuthException
	 */
	public static void login(Connection conn, String username, String password) throws AuthException, SQLException {
		PreparedStatement stmt = conn.prepareStatement("SELECT password " +
				"FROM r3ds_user " +
				"WHERE username = ?"
		);
		stmt.setString(1, username);
		ResultSet rs = stmt.executeQuery();
		
		if (!rs.next() || !BCrypt.checkpw(password, rs.getString("password"))) {
			rs.close();
			stmt.close();
			throw new AuthException("There are no user with that username and password combination.");
		}
	}
}
