package com.r3ds.server;

import com.r3ds.server.exception.DatabaseException;

import java.io.File;
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
        try (InputStream input = new FileInputStream(
                new File(Database.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getParent() +
                        "/../src/main/resources/database/config.properties"
        )) {
            Properties prop = new Properties();

            // load a properties file
            prop.load(input);

            // get the property value and print it out
            String dbName = prop.getProperty("db.name");
            int dbPort = Integer.parseInt(prop.getProperty("db.port"));
            String dbUsername = prop.getProperty("db.user");
            String dbPassword = prop.getProperty("db.pass");

            Class.forName("com.mysql.jdbc.Driver");
            // TODO: ver como usar certificados na ligacao com a base de dados
            return DriverManager.getConnection(
                    "jdbc:mysql://localhost:" + dbPort + "/" + dbName + "?useUnicode=yes&characterEncoding=UTF-8&verifyServerCertificate=false&useSSL=true",
                    dbUsername,
                    dbPassword
            );
        } catch (IOException | ClassNotFoundException | SQLException e) {
            System.out.println(e);
            throw new DatabaseException("The database connection failed.", e);
        }
    }
}
