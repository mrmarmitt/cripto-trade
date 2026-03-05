package com.marmitt.application.spring.controller;

import com.marmitt.application.spring.bootstrap.BootRunSnapshot;
import com.marmitt.application.spring.bootstrap.BootStatusTracker;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/boot")
public class BootController {

    private final BootStatusTracker bootStatusTracker;

    public BootController(BootStatusTracker bootStatusTracker) {
        this.bootStatusTracker = bootStatusTracker;
    }

    @GetMapping("/status")
    public ResponseEntity<BootRunSnapshot> getStatus() {
        return ResponseEntity.ok(bootStatusTracker.snapshot());
    }
}
