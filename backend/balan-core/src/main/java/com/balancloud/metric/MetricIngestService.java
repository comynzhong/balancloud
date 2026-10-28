package com.balancloud.metric;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class MetricIngestService {

    private static final Logger log = LoggerFactory.getLogger(MetricIngestService.class);
    private static final int MAX_QUEUE_SIZE = 20000;
    private static final int FLUSH_BATCH_SIZE = 500;

    private final TdengineClient tdengineClient;
    private final LinkedBlockingQueue<MetricPoint> queue = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);
    private final AtomicLong lastErrorLogAt = new AtomicLong(0);

    public MetricIngestService(TdengineClient tdengineClient) {
        this.tdengineClient = tdengineClient;
    }

    public void enqueue(MetricPoint point) {
        if (!queue.offer(point)) {
            log.warn("Metric queue is full, dropping point charger={} port={}", point.chargerId(), point.portNo());
        }
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 2000)
    public void flush() {
        if (queue.isEmpty()) {
            return;
        }
        List<MetricPoint> batch = new ArrayList<>(FLUSH_BATCH_SIZE);
        queue.drainTo(batch, FLUSH_BATCH_SIZE);
        if (batch.isEmpty()) {
            return;
        }
        try {
            tdengineClient.insert(batch);
        } catch (Exception e) {
            long now = System.currentTimeMillis();
            if (now - lastErrorLogAt.get() > 30_000) {
                lastErrorLogAt.set(now);
                log.error("Failed to write {} metric points to TDengine: {}", batch.size(), e.getMessage());
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        List<MetricPoint> batch = new ArrayList<>();
        queue.drainTo(batch);
        if (!batch.isEmpty()) {
            try {
                tdengineClient.insert(batch);
            } catch (Exception e) {
                log.warn("Failed to flush metrics during shutdown: {}", e.getMessage());
            }
        }
    }
}
