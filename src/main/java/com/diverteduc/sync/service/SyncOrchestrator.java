package com.diverteduc.sync.service;

import com.diverteduc.sync.config.SyncConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Coordena o fluxo completo de sincronização:
 *   1. Testa conectividade com a origem
 *   2. Executa pg_dump
 *   3. Executa pg_restore
 *   4. Remove backups antigos
 *   5. Valida o resultado
 */
public class SyncOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SyncOrchestrator.class);
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final SyncConfig     config;
    private final DumpService    dumpService;
    private final RestoreService restoreService;

    public SyncOrchestrator(SyncConfig config) {
        this.config         = config;
        this.dumpService    = new DumpService(config);
        this.restoreService = new RestoreService(config);
    }

    /**
     * Executa o ciclo completo de sincronização.
     * @return true se tudo ocorreu com sucesso
     */
    public synchronized boolean execute() {
        LocalDateTime start = LocalDateTime.now();
        log.info("══════════════════════════════════════════════════════");
        log.info("  INÍCIO DO SYNC - {}", start.format(DT_FMT));
        log.info("══════════════════════════════════════════════════════");

        try {
            // 1. Testa conectividade com a origem
            if (!testSourceConnection()) {
                log.error("Falha na conexão com a origem. Sync cancelado.");
                return false;
            }

            // 2. Executa pg_dump
            Path dumpFile = dumpService.dump();
            if (dumpFile == null) {
                log.error("pg_dump falhou. Sync cancelado.");
                return false;
            }

            // 3. Executa pg_restore
            boolean restored = restoreService.restore(dumpFile);
            if (!restored) {
                log.error("pg_restore falhou. O banco local pode estar incompleto.");
                return false;
            }

            // 4. Remove backups antigos
            cleanOldBackups();

            // 5. Validação pós-sync
            validate();

            LocalDateTime end = LocalDateTime.now();
            log.info("══════════════════════════════════════════════════════");
            log.info("  SYNC CONCLUÍDO COM SUCESSO - {}", end.format(DT_FMT));
            log.info("══════════════════════════════════════════════════════");
            return true;

        } catch (Exception e) {
            log.error("Erro inesperado durante o sync: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Testa conexão com o banco de produção antes de iniciar o dump.
     */
    private boolean testSourceConnection() {
        log.info("Testando conexão com a origem ({})...", config.getSourceJdbcUrl());
        try (Connection conn = DriverManager.getConnection(
                config.getSourceJdbcUrl(),
                config.getSourceUser(),
                config.getSourcePassword())) {

            try (Statement stmt = conn.createStatement();
                 ResultSet rs   = stmt.executeQuery("SELECT version()")) {
                if (rs.next()) {
                    log.info("Conexão OK. Versão: {}", rs.getString(1).split("\n")[0]);
                }
            }
            return true;
        } catch (Exception e) {
            log.error("Falha ao conectar na origem: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Verifica quantas tabelas foram restauradas no banco de destino.
     */
    private void validate() {
        log.info("Validando banco de destino...");
        String jdbcUrl = config.getTargetJdbcUrl();
        try (Connection conn = DriverManager.getConnection(
                jdbcUrl, config.getTargetUser(), config.getTargetPassword());
             Statement stmt = conn.createStatement();
             ResultSet rs   = stmt.executeQuery(
                 "SELECT COUNT(*) FROM information_schema.tables " +
                 "WHERE table_schema NOT IN ('pg_catalog','information_schema')"
             )) {
            if (rs.next()) {
                int tableCount = rs.getInt(1);
                log.info("Validação: {} tabela(s) encontrada(s) em '{}'.",
                         tableCount, config.getTargetDb());
                if (tableCount == 0) {
                    log.warn("Atenção: nenhuma tabela encontrada após o restore!");
                }
            }
        } catch (Exception e) {
            log.warn("Não foi possível validar o banco de destino: {}", e.getMessage());
        }
    }

    /**
     * Remove os backups mais antigos, mantendo apenas os N mais recentes.
     */
    private void cleanOldBackups() throws IOException {
        Path backupDir = Paths.get(config.getBackupDir());
        if (!Files.exists(backupDir)) return;

        List<Path> dumps = new ArrayList<>();
        try (Stream<Path> stream = Files.list(backupDir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".dump"))
                  .forEach(dumps::add);
        }

        // Ordena do mais recente para o mais antigo
        dumps.sort(Comparator.reverseOrder());

        int retain = config.getBackupRetainCount();
        if (dumps.size() > retain) {
            List<Path> toDelete = dumps.subList(retain, dumps.size());
            for (Path old : toDelete) {
                Files.deleteIfExists(old);
                log.info("Backup antigo removido: {}", old.getFileName());
            }
        }

        log.info("Backups mantidos: {}/{}", Math.min(dumps.size(), retain), retain);
    }
}
