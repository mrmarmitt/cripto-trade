package com.marmitt.application.spring.handler;

import com.marmitt.application.spring.bootstrap.BootFailFastEvent;
import com.marmitt.core.dto.notification.ErrorNotificationEvent;
import com.marmitt.core.ports.outbound.notification.ErrorNotificationPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class BootAlertListener {

    private final ErrorNotificationPort errorNotification;

    public BootAlertListener(ErrorNotificationPort errorNotification) {
        this.errorNotification = errorNotification;
    }

    @EventListener
    public void onBootFailFast(BootFailFastEvent event) {
        log.error("BOOT_FAILFAST_ALERT: runId={} phase={} code={} message={} at={}",
                event.runId(), event.phase(), event.code(), event.message(), event.timestamp());

        errorNotification.notifyError(ErrorNotificationEvent.bootFailFast(
                "Boot fail-fast: " + event.phase(),
                "code=" + event.code() + " message=" + event.message(),
                event.runId(),
                event.timestamp()));
    }
}
