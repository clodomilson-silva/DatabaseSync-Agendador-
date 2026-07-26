package com.diverteduc.sync.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Carrega e valida as configurações do arquivo sync.properties.
 * Procura o arquivo na seguinte ordem:
 *   1. ./config/sync.properties (diretório atual)
 *   2. sync.properties (classpath / JAR)
 */
public class SyncConfig {

    private static final Logger log = LoggerFactory.getLogger(SyncConfig.class);

    // Origem (produção - NeonDB)
    private String sourceHost;
    private int    sourcePort;
    private String sourceDb;
    private String sourceUser;
    private String sourcePassword;

    // Destino (local)
    private String targetHost;
    private int    targetPort;
    private String targetDb;
    private String targetUser;
    private String targetPassword;

    // Agendamento
    private String syncCron;

    // Backup
    private int    backupRetainCount;
    private String backupDir;
    private String logDir;

    // Ferramentas PostgreSQL
    private String pgDumpPath;
    private String pgRestorePath;
    private String psqlPath;

    private SyncConfig() {}

    /**
     * Carrega as configurações do sync.properties.
     */
    public static SyncConfig load() throws IOException {
        Properties props = new Properties();

        // Tenta carregar do sistema de arquivos primeiro
        Path externalConfig = Paths.get("config", "sync.properties");
        if (Files.exists(externalConfig)) {
            log.info("Carregando configurações de: {}", externalConfig.toAbsolutePath());
            try (InputStream is = new FileInputStream(externalConfig.toFile())) {
                props.load(is);
            }
        } else {
            // Fallback: classpath (dentro do JAR)
            log.info("Carregando configurações do classpath...");
            try (InputStream is = SyncConfig.class.getClassLoader().getResourceAsStream("sync.properties")) {
                if (is == null) {
                    throw new IOException(
                        "Arquivo sync.properties não encontrado!\n" +
                        "Crie o arquivo em: " + externalConfig.toAbsolutePath()
                    );
                }
                props.load(is);
            }
        }

        SyncConfig cfg = new SyncConfig();

        // Origem
        cfg.sourceHost     = require(props, "source.host");
        cfg.sourcePort     = Integer.parseInt(props.getProperty("source.port", "5432"));
        cfg.sourceDb       = require(props, "source.db");
        cfg.sourceUser     = require(props, "source.user");
        cfg.sourcePassword = require(props, "source.password");

        // Destino
        cfg.targetHost     = props.getProperty("target.host", "localhost");
        cfg.targetPort     = Integer.parseInt(props.getProperty("target.port", "5432"));
        cfg.targetDb       = props.getProperty("target.db", "diverteduc_dev");
        cfg.targetUser     = require(props, "target.user");
        cfg.targetPassword = require(props, "target.password");

        // Agendamento (padrão: toda meia-noite)
        cfg.syncCron = props.getProperty("sync.cron", "0 0 0 * * ?");

        // Backup
        cfg.backupRetainCount = Integer.parseInt(props.getProperty("backup.retain.count", "7"));
        cfg.backupDir         = props.getProperty("backup.dir", "backups");
        cfg.logDir            = props.getProperty("log.dir", "logs");

        // Caminhos das ferramentas PostgreSQL
        cfg.pgDumpPath    = props.getProperty("pgdump.path", "pg_dump");
        cfg.pgRestorePath = props.getProperty("pgrestore.path", "pg_restore");
        cfg.psqlPath      = props.getProperty("psql.path", "psql");

        cfg.validate();
        return cfg;
    }

    private static String require(Properties props, String key) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                "Configuração obrigatória ausente: '" + key + "' no sync.properties"
            );
        }
        return value.trim();
    }

    private void validate() {
        if (targetDb.equalsIgnoreCase(sourceDb) &&
            targetHost.equalsIgnoreCase(sourceHost)) {
            throw new IllegalArgumentException(
                "SEGURANÇA: banco de destino não pode ser igual ao de origem! " +
                "Isso poderia sobrescrever a produção."
            );
        }
        log.debug("Configuração validada com sucesso.");
    }

    // ─── Getters ─────────────────────────────────────────────────────────────

    public String getSourceHost()     { return sourceHost; }
    public int    getSourcePort()     { return sourcePort; }
    public String getSourceDb()       { return sourceDb; }
    public String getSourceUser()     { return sourceUser; }
    public String getSourcePassword() { return sourcePassword; }

    public String getTargetHost()     { return targetHost; }
    public int    getTargetPort()     { return targetPort; }
    public String getTargetDb()       { return targetDb; }
    public String getTargetUser()     { return targetUser; }
    public String getTargetPassword() { return targetPassword; }

    public String getSyncCron()           { return syncCron; }
    public int    getBackupRetainCount()  { return backupRetainCount; }
    public String getBackupDir()          { return backupDir; }
    public String getLogDir()             { return logDir; }
    public String getPgDumpPath()         { return pgDumpPath; }
    public String getPgRestorePath()      { return pgRestorePath; }
    public String getPsqlPath()           { return psqlPath; }

    /**
     * Retorna a connection string JDBC da origem (para testes de conectividade).
     */
    public String getSourceJdbcUrl() {
        return String.format(
            "jdbc:postgresql://%s:%d/%s?sslmode=require&ssl=true&sslfactory=org.postgresql.ssl.NonValidatingFactory&connect_timeout=10",
            sourceHost, sourcePort, sourceDb
        );
    }

    /**
     * Retorna a connection string JDBC do destino.
     */
    public String getTargetJdbcUrl() {
        return String.format(
            "jdbc:postgresql://%s:%d/%s",
            targetHost, targetPort, targetDb
        );
    }
}
