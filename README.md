# Ransomware Resistant Remote Document Sharing

Steps to do the setup:

1 - in main directory, run mvn install
2 - create a database in Postgres with schema.sql (in main directory)
3 - setup config.properties in dot-files/server/src/main/resources/database based in config.properties.default
4 - setup config.properties in dot-files/client/src/main/resources/database based in config.properties.default

To run:

1 - go to dot-files/rootca and run mvn exec:java
2 - go to dot-files/server and run mvn exec:java
3 - go to dot-files/client and run mvn exec:java