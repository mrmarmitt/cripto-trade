package com.marmitt.application.spring.handler;

import com.marmitt.application.spring.bootstrap.BootFailFastEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class BootAlertListener {

    @EventListener
    public void onBootFailFast(BootFailFastEvent event) {
        log.error("BOOT_FAILFAST_ALERT: runId={} phase={} code={} message={} at={}",
                event.runId(), event.phase(), event.code(), event.message(), event.timestamp());
    }
}
