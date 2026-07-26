package com.diverteduc.sync.service;

import com.diverteduc.sync.config.SyncConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Executa o pg_dump para exportar o banco de produção (NeonDB) para um arquivo .dump.
 */
public class DumpService {

    private static final Logger log = LoggerFactory.getLogger(DumpService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm");

    private final SyncConfig config;

    public DumpService(SyncConfig config) {
        this.config = config;
    }

    /**
     * Executa o pg_dump e retorna o Path do arquivo gerado, ou null em caso de falha.
     */
    public Path dump() throws Exception {
        // Garante que o diretório de backups existe
        Path backupDir = Paths.get(config.getBackupDir());
        Files.createDirectories(backupDir);

        // Nome do arquivo: neondb_20240726_0000.dump
        String timestamp = LocalDateTime.now().format(DATE_FMT);
        String fileName  = config.getSourceDb() + "_" + timestamp + ".dump";
        Path   outputFile = backupDir.resolve(fileName);

        log.info("Iniciando pg_dump...");
        log.info("  Origem : {}:{}/{}", config.getSourceHost(), config.getSourcePort(), config.getSourceDb());
        log.info("  Arquivo: {}", outputFile.toAbsolutePath());

        List<String> cmd = new ArrayList<>();
        cmd.add(config.getPgDumpPath());
        cmd.add("--format=custom");          // Formato binary comprimido
        cmd.add("--no-owner");               // Não inclui comandos de ALTER OWNER
        cmd.add("--no-acl");                 // Não inclui GRANT/REVOKE
        cmd.add("--host=" + config.getSourceHost());
        cmd.add("--port=" + config.getSourcePort());
        cmd.add("--username=" + config.getSourceUser());
        cmd.add("--dbname=" + config.getSourceDb());
        cmd.add("--file=" + outputFile.toAbsolutePath());
        cmd.add("--verbose");

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.environment().put("PGPASSWORD", config.getSourcePassword());
        pb.redirectErrorStream(true);        // Stderr → stdout

        log.debug("Comando: {}", String.join(" ", cmd).replaceAll("--username=\\S+", "--username=***"));

        long startTime = System.currentTimeMillis();
        Process process = pb.start();

        // Lê e loga a saída do processo em tempo real
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("[pg_dump] {}", line);
            }
        }

        int exitCode = process.waitFor();
        long elapsed = (System.currentTimeMillis() - startTime) / 1000;

        if (exitCode != 0) {
            log.error("pg_dump falhou com exit code: {}", exitCode);
            // Remove arquivo corrompido se existir
            Files.deleteIfExists(outputFile);
            return null;
        }

        long fileSizeMB = Files.size(outputFile) / (1024 * 1024);
        log.info("pg_dump concluído em {}s. Arquivo: {} ({} MB)",
                 elapsed, fileName, fileSizeMB);

        return outputFile;
    }
}
