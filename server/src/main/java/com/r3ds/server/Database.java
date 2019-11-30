package com.r3ds.server;

import com.r3ds.server.exception.DatabaseException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Properties;

public class Database {
	private Connection conn;
	
	public Database() throws DatabaseException {
		try (InputStream input = new FileInputStream(
				new File(Database.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getParent() +
						"/../src/main/resources/database/config.properties"
		)) {
			Properties prop = new Properties();
			
			// load a properties file
			prop.load(input);
			
			// get the property value and print it out
			String dbHost = prop.getProperty("db.host");
			String dbName = prop.getProperty("db.name");
			int dbPort = Integer.parseInt(prop.getProperty("db.port"));
			String dbUsername = prop.getProperty("db.user");
			String dbPassword = prop.getProperty("db.pass");
			
			Class.forName("org.postgresql.Driver");
			// TODO: ver como usar certificados na ligacao com a base de dados
			Connection conn = DriverManager.getConnection(
					"jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName,
					dbUsername,
					dbPassword
			);
			
			PreparedStatement stmt = conn.prepareStatement("SET search_path TO 'dot-files'");
			stmt.execute();
			stmt.close();
			
			this.conn = conn;
			
		} catch (IOException | ClassNotFoundException | SQLException e) {
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
