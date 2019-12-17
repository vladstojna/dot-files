# R3DS - Ransomware Resistant Remote Document Sharing

Steps to do the setup:

1. We have to setup the database:
    - in the email sent to the professors, a config.properties was attached containing the configuration data needed if the setup is made with our database in sigma
    - go to server/src/main/resources/database
    - copy config.properties.default to its current folder and rename it to config.properties
    - edit config.properties with the database info found in the config file attached in the email

2. Setup the config files in client now:
    - go to client/src/main/resources
    - copy config.properties.default to its current folder and rename it to config.properties
    - edit config.properties (already filled with the default configurations)

3. Now it is time to run the system. For that, please do the following:
    - in the project's root folder, run mvn install
    - if you want to install each entity separately, go to the root folder of each entity and run mvn install
    - finally, in this order
      - open a new terminal, go to rootca and do mvn exec:java
      - open a new terminal, go to server and do mvn exec:java
      - open a new terminal, go to client and do mvn exec:java
        - open a new terminal for each client you want to execute