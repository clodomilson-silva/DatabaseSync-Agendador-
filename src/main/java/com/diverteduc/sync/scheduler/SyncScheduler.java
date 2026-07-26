package com.diverteduc.sync.scheduler;

import com.diverteduc.sync.config.SyncConfig;
import com.diverteduc.sync.service.SyncOrchestrator;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * Agendador baseado em Quartz.
 * Executa o SyncOrchestrator conforme a cron expression definida no sync.properties.
 *
 * Cron padrão: "0 0 0 * * ?" → toda meia-noite
 *
 * Exemplos:
 *   "0 0 0 * * ?"    → todos os dias à meia-noite
 *   "0 0 * * * ?"    → toda hora em ponto
 *   "0 0 6,18 * * ?" → às 06:00 e 18:00
 */
public class SyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(SyncScheduler.class);

    private static final String JOB_GROUP     = "database-sync";
    private static final String JOB_NAME      = "sync-job";
    private static final String TRIGGER_GROUP = "database-sync-triggers";
    private static final String TRIGGER_NAME  = "sync-trigger";

    private Scheduler scheduler;

    private final SyncConfig     config;
    private final SyncOrchestrator orchestrator;

    public SyncScheduler(SyncConfig config, SyncOrchestrator orchestrator) {
        this.config       = config;
        this.orchestrator = orchestrator;
    }

    /**
     * Inicia o agendador Quartz.
     */
    public void start() throws SchedulerException {
        // Configura Quartz para rodar em memória (sem persistência em banco)
        Properties quartzProps = new Properties();
        quartzProps.setProperty("org.quartz.scheduler.instanceName",          "DatabaseSyncScheduler");
        quartzProps.setProperty("org.quartz.threadPool.threadCount",           "2");
        quartzProps.setProperty("org.quartz.jobStore.class",
                                "org.quartz.simpl.RAMJobStore");
        quartzProps.setProperty("org.quartz.scheduler.skipUpdateCheck",        "true");

        SchedulerFactory factory = new StdSchedulerFactory(quartzProps);
        scheduler = factory.getScheduler();

        // Passa o orquestrador para o job via JobDataMap
        JobDataMap dataMap = new JobDataMap();
        dataMap.put("orchestrator", orchestrator);

        JobDetail job = JobBuilder.newJob(SyncJob.class)
                .withIdentity(JOB_NAME, JOB_GROUP)
                .setJobData(dataMap)
                .build();

        String cron = config.getSyncCron();
        log.info("Agendamento configurado: cron = '{}'", cron);

        CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(TRIGGER_NAME, TRIGGER_GROUP)
                .withSchedule(CronScheduleBuilder.cronSchedule(cron))
                .build();

        scheduler.scheduleJob(job, trigger);
        scheduler.start();

        // Log da próxima execução agendada
        var nextFireTime = trigger.getNextFireTime();
        log.info("Próxima execução agendada: {}",
                 nextFireTime != null ? nextFireTime : "não calculado ainda");
    }

    /**
     * Para o agendador Quartz de forma limpa.
     */
    public void stop() {
        if (scheduler != null) {
            try {
                scheduler.shutdown(true); // Aguarda jobs em execução terminarem
                log.info("Agendador encerrado.");
            } catch (SchedulerException e) {
                log.error("Erro ao encerrar o agendador: {}", e.getMessage());
            }
        }
    }

    // ─── Job interno do Quartz ────────────────────────────────────────────────

    /**
     * Job Quartz que delega a execução para o SyncOrchestrator.
     */
    public static class SyncJob implements Job {

        private static final Logger jobLog = LoggerFactory.getLogger(SyncJob.class);

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            jobLog.info("Trigger do agendador disparado.");
            SyncOrchestrator orch = (SyncOrchestrator) context.getJobDetail()
                    .getJobDataMap()
                    .get("orchestrator");

            if (orch == null) {
                throw new JobExecutionException("SyncOrchestrator não encontrado no JobDataMap!");
            }

            boolean success = orch.execute();
            if (!success) {
                jobLog.error("Sync agendado falhou. Verifique os logs para detalhes.");
            }
        }
    }
}
