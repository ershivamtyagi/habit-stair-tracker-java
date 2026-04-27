package com.stairprogress;

public record DatabaseConfig(
        String host,
        int port,
        String database,
        String username,
        String password
) {
    public static DatabaseConfig load() {
        String host = readValue("db.host", "DB_HOST", "localhost");
        int port = Integer.parseInt(readValue("db.port", "DB_PORT", "3306"));
        String database = readValue("db.name", "DB_NAME", "progress_tracker");
        String username = readValue("db.user", "DB_USER", "root");
        String password = readValue("db.password", "DB_PASSWORD", "logic");
        return new DatabaseConfig(host, port, database, username, password);
    }

    public String serverJdbcUrl() {
        return "jdbc:mysql://" + host + ":" + port
                + "/?createDatabaseIfNotExist=true&serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false";
    }

    public String databaseJdbcUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?createDatabaseIfNotExist=true&serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false";
    }

    private static String readValue(String propertyKey, String envKey, String defaultValue) {
        String propertyValue = System.getProperty(propertyKey);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue.trim();
        }

        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isBlank()) {
            return envValue.trim();
        }

        return defaultValue;
    }
}
