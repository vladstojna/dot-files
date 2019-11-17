package com.r3ds.server;

import com.r3ds.server.exception.DatabaseException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class Database {

    /**
     * Return a connection to DB
     *
     * @return Connection
     * @throws DatabaseException
     */
    public static Connection getConnection() throws DatabaseException {
        try (InputStream input = new FileInputStream("../../../resources/database/config.properties")) {
            Properties prop = new Properties();

            // load a properties file
            prop.load(input);

            // get the property value and print it out
            String dbName = prop.getProperty("db.name");
            int dbPort = Integer.parseInt(prop.getProperty("db.port"));
            String dbUsername = prop.getProperty("db.user");
            String dbPassword = prop.getProperty("db.pass");

            Class.forName("com.mysql.jdbc.Driver");
            return DriverManager.getConnection(
                    "jdbc:mysql://localhost:" + dbPort + "/" + dbName, dbUsername, dbPassword);
        } catch (IOException | ClassNotFoundException | SQLException e) {
            System.out.println(e);
            throw new DatabaseException("The database connection failed.", e);
        }
    }
}
