package com.biz.sccba.sqlanalyzer.profiling;

import com.biz.sccba.sqlanalyzer.repository.ProfilingRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lease-based polling worker for profiling jobs (development-guide §7.2): claim → extend lease →
 * execute asynchronously → settle. Stale RUNNING jobs past their lease are re-claimed.
 */
@Service
@ConditionalOnProperty(prefix = "sql-analyzer.worker", name = "enabled", havingValue = "true")
public class ProfilingWorker {

    private final ProfilingRepository dao;
    private final ProfilingService service;
    private final String workerId = "profiler_" + UUID.randomUUID();
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "sql-analyzer-profiler");
        t.setDaemon(true);
        return t;
    });

    public ProfilingWorker(ProfilingRepository dao,
                           org.springframework.beans.factory.ObjectProvider<ProfilingService> serviceProvider) {
        this.dao = dao;
        this.service = serviceProvider.getIfAvailable();
    }

    @Scheduled(fixedDelayString = "${sql-analyzer.worker.poll-delay-ms:500}")
    public void poll() {
        if (service == null) return;
        dao.claimJob(workerId).ifPresent(job -> {
            dao.extendJobLease(job.id(), 30);
            executor.execute(() -> service.runJob(job));
        });
    }
}
