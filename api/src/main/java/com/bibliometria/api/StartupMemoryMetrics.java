package com.bibliometria.api;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public final class StartupMemoryMetrics implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(StartupMemoryMetrics.class);

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        Runtime runtime = Runtime.getRuntime();
        LOGGER.info(
                "Startup memory metrics: heapUsedBytes={}, heapCommittedBytes={}, "
                        + "heapMaxBytes={}, jvmTotalMemoryBytes={}, jvmFreeMemoryBytes={}",
                heap.getUsed(),
                heap.getCommitted(),
                heap.getMax(),
                runtime.totalMemory(),
                runtime.freeMemory());
    }
}
