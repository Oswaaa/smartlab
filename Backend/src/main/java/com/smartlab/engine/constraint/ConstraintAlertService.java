package com.smartlab.engine.constraint;

import com.smartlab.global.event.ConstraintAlertEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class ConstraintAlertService {
    private static final Logger log = LoggerFactory.getLogger(ConstraintAlertService.class);

    @EventListener
    public void handle(ConstraintAlertEvent event) {
        log.warn("约束告警, ruleId={}, constraint={}, taskId={}", event.constraintRuleId(), event.constraintName(), event.taskId());
    }
}
