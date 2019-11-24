package com.r3ds.server;

import com.r3ds.server.exception.AuthException;
import io.grpc.Status;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AuthTools {
	
	public static boolean signup(Connection conn, String username, String password) throws AuthException {
		try {
			PreparedStatement stmt = conn.prepareStatement("INSERT INTO user(username, password) VALUES (?, ?)");
			stmt.setString(1, username);
			stmt.setString(2, BCrypt.hashpw(password, BCrypt.gensalt()));
			stmt.executeUpdate();
			stmt.close();
		} catch (SQLException e) {
			if (e.getSQLState().equals("23000")) {
				return false;
			} else {
				throw new AuthException("There was an error with database");
			}
		} finally {
			Database.closeConnection(conn);
		}
		return true;
	}
	
	public static void login(Connection conn, String username, String password) throws AuthException {
		try {
			conn = Database.getConnection();
			PreparedStatement stmt = conn.prepareStatement("SELECT password " +
					"FROM user " +
					"WHERE username = ?"
			);
			stmt.setString(1, username);
			ResultSet rs = stmt.executeQuery();
			stmt.close();
			
			if (!rs.next() || !BCrypt.checkpw(password, rs.getString("password")))
				throw new AuthException("There are no user with that username and password combination.");
		} catch (SQLException e) {
			throw new AuthException("There was an error with database", e);
		} finally {
			Database.closeConnection(conn);
		}
	}
}
