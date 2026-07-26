package com.diverteduc.sync.service;

import com.diverteduc.sync.config.SyncConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Executa a restauração do dump no banco local (diverteduc_dev).
 * Dropa e recria o banco antes de restaurar para garantir consistência total.
 */
public class RestoreService {

    private static final Logger log = LoggerFactory.getLogger(RestoreService.class);

    private final SyncConfig config;

    public RestoreService(SyncConfig config) {
        this.config = config;
    }

    /**
     * Restaura o arquivo dump no banco de destino.
     * @return true se bem-sucedido
     */
    public boolean restore(Path dumpFile) throws Exception {
        log.info("Iniciando restauração...");
        log.info("  Arquivo: {}", dumpFile.toAbsolutePath());
        log.info("  Destino: {}:{}/{}", config.getTargetHost(), config.getTargetPort(), config.getTargetDb());

        // Passo 1: Dropa e recria o banco de destino
        dropAndRecreateDatabase();

        // Passo 2: Executa pg_restore
        return runPgRestore(dumpFile);
    }

    /**
     * Dropa e recria o banco diverteduc_dev usando JDBC conectado ao banco 'postgres'.
     */
    private void dropAndRecreateDatabase() throws Exception {
        // Conecta ao banco padrão 'postgres' (não ao target, que pode não existir ainda)
        String jdbcUrl = String.format(
            "jdbc:postgresql://%s:%d/postgres",
            config.getTargetHost(), config.getTargetPort()
        );

        log.info("Recriando banco de destino '{}'...", config.getTargetDb());

        try (Connection conn = DriverManager.getConnection(
                jdbcUrl, config.getTargetUser(), config.getTargetPassword())) {

            // Desabilita autocommit para controle manual
            conn.setAutoCommit(true);

            try (Statement stmt = conn.createStatement()) {
                // Termina todas as conexões ativas no banco de destino
                stmt.execute(
                    "SELECT pg_terminate_backend(pid) " +
                    "FROM pg_stat_activity " +
                    "WHERE datname = '" + config.getTargetDb() + "' " +
                    "  AND pid <> pg_backend_pid()"
                );
                log.debug("Conexões ativas ao banco '{}' encerradas.", config.getTargetDb());

                // Dropa o banco se existir
                stmt.execute("DROP DATABASE IF EXISTS \"" + config.getTargetDb() + "\"");
                log.debug("Banco '{}' removido (se existia).", config.getTargetDb());

                // Cria o banco vazio
                stmt.execute("CREATE DATABASE \"" + config.getTargetDb() + "\"");
                log.info("Banco '{}' recriado.", config.getTargetDb());
            }
        }
    }

    /**
     * Executa pg_restore para importar o dump no banco de destino.
     */
    private boolean runPgRestore(Path dumpFile) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(config.getPgRestorePath());
        cmd.add("--host=" + config.getTargetHost());
        cmd.add("--port=" + config.getTargetPort());
        cmd.add("--username=" + config.getTargetUser());
        cmd.add("--dbname=" + config.getTargetDb());
        cmd.add("--no-owner");          // Ignora comandos de proprietário
        cmd.add("--no-acl");            // Ignora GRANT/REVOKE
        cmd.add("--exit-on-error");     // Para em caso de erro crítico
        cmd.add("--verbose");
        cmd.add(dumpFile.toAbsolutePath().toString());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.environment().put("PGPASSWORD", config.getTargetPassword());
        pb.redirectErrorStream(true);

        log.debug("Comando: {}", String.join(" ", cmd).replaceAll("--username=\\S+", "--username=***"));

        long startTime = System.currentTimeMillis();
        Process process = pb.start();

        int warningCount = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("error") || line.contains("ERROR")) {
                    log.warn("[pg_restore] {}", line);
                    warningCount++;
                } else {
                    log.debug("[pg_restore] {}", line);
                }
            }
        }

        int exitCode = process.waitFor();
        long elapsed = (System.currentTimeMillis() - startTime) / 1000;

        if (exitCode != 0) {
            log.error("pg_restore falhou com exit code: {}. Avisos: {}", exitCode, warningCount);
            return false;
        }

        if (warningCount > 0) {
            log.warn("pg_restore concluído com {} avisos em {}s.", warningCount, elapsed);
        } else {
            log.info("pg_restore concluído com sucesso em {}s.", elapsed);
        }

        return true;
    }
}
