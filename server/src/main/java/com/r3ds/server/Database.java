package com.r3ds.server;

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

	/**
	 * Return a connection to DB
	 *
	 * @return Connection
	 * @throws SQLException
	 */
	public static Connection getConnection() throws SQLException {
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
			
			return conn;
		} catch (IOException | ClassNotFoundException | SQLException e) {
			System.out.println(e);
			throw new SQLException("The database connection failed.", e);
		}
	}
	
	public static void closeConnection(Connection conn) {
		if (conn != null) {
			try {
				conn.close();
			} catch (SQLException e) {
				/* ignored */
				System.out.println("Close failure");
			}
		}
	}
}
