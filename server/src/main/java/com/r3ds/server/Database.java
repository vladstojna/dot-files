package com.r3ds.server;

import com.r3ds.server.exception.DatabaseException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Properties;

public class Database {
	private Connection conn;

	private final String dbHost;
	private final String dbName;
	private final int dbPort;
	private final String dbUsername;
	private final String dbPassword;

	private String getUrl() {
		return "jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName;
	}

	public Database(Properties props) throws DatabaseException {
		this.dbHost = props.getProperty("db.host");
		this.dbName = props.getProperty("db.name");
		this.dbPort = Integer.parseInt(props.getProperty("db.port"));
		this.dbUsername = props.getProperty("db.user");
		this.dbPassword = props.getProperty("db.pass");
	}

	/**
	 * Opens a connection to the DB
	 * @throws DatabaseException
	 */
	public void openConnection() throws DatabaseException {
		try {
			Class.forName("org.postgresql.Driver");
			this.conn = DriverManager.getConnection(getUrl(), dbUsername, dbPassword);
			PreparedStatement stmt = conn.prepareStatement("SET search_path TO 'dot-files'");
			stmt.execute();
			stmt.close();
		} catch (SQLException | ClassNotFoundException e) {
			closeConnection();
			throw new DatabaseException("The database connection failed.", e);
		}
	}
	
	/**
	 * Return a connection to DB
	 *
	 * @return Connection
	 */
	public Connection getConnection() {
		return this.conn;
	}
	
	/**
	 * @throws DatabaseException
	 */
	public void closeConnection() throws DatabaseException {
		try {
			this.conn.close();
			this.conn = null;
		} catch (SQLException e) {
			throw new DatabaseException("Impossible to close connection.", e);
		}
	}
}
