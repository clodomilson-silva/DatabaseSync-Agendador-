package com.diverteduc.sync;

import com.diverteduc.sync.config.SyncConfig;
import com.diverteduc.sync.scheduler.SyncScheduler;
import com.diverteduc.sync.service.SyncOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ponto de entrada da aplicação Database Sync.
 * Inicializa configurações, executa um sync inicial e agenda sincronizações diárias.
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        log.info("==========================================================");
        log.info("  Database Sync - NeonDB (produção) → diverteduc_dev");
        log.info("==========================================================");

        // Carrega configurações
        SyncConfig config = SyncConfig.load();
        log.info("Configuração carregada:");
        log.info("  Origem : {}:{}/{}", config.getSourceHost(), config.getSourcePort(), config.getSourceDb());
        log.info("  Destino: {}:{}/{}", config.getTargetHost(), config.getTargetPort(), config.getTargetDb());
        log.info("  Cron   : {}", config.getSyncCron());

        // Executa um sync inicial imediatamente ao iniciar
        log.info("Executando sincronização inicial...");
        SyncOrchestrator orchestrator = new SyncOrchestrator(config);
        boolean initialSuccess = orchestrator.execute();

        if (initialSuccess) {
            log.info("Sincronização inicial concluída com sucesso.");
        } else {
            log.error("Sincronização inicial FALHOU. Verifique os logs para detalhes.");
        }

        // Inicia o agendador para sincronizações futuras
        log.info("Iniciando agendador (cron: {})...", config.getSyncCron());
        SyncScheduler scheduler = new SyncScheduler(config, orchestrator);
        scheduler.start();

        log.info("Agendador ativo. Aguardando próximas execuções...");
        log.info("Pressione Ctrl+C para encerrar.");

        // Aguarda sinal de encerramento (SIGINT/SIGTERM)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Encerrando o Database Sync...");
            scheduler.stop();
            log.info("Encerrado.");
        }));

        // Mantém a JVM ativa
        Thread.currentThread().join();
    }
}
