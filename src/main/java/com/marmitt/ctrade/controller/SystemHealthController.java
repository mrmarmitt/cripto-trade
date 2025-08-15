package com.marmitt.ctrade.controller;

import com.marmitt.ctrade.application.service.HealthCheckService;
import com.marmitt.ctrade.controller.dto.HealthCheckResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
@Tag(name = "System Health", description = "Endpoints para verificar status do sistema")
public class SystemHealthController {
    
    private final HealthCheckService healthCheckService;
    
    @GetMapping("/health")
    @Operation(
        summary = "Verificar status do sistema",
        description = "Retorna informações detalhadas sobre o status do cache e WebSocket"
    )
    @ApiResponse(responseCode = "200", description = "Status do sistema")
    @ApiResponse(responseCode = "503", description = "Serviço indisponível")
    public ResponseEntity<HealthCheckResponse> getSystemHealth() {
        HealthCheckResponse health = healthCheckService.getSystemHealth();
        
        // Retornar status HTTP baseado na saúde geral
        return switch (health.getStatus()) {
            case "UP" -> ResponseEntity.ok(health);
            case "DEGRADED" -> ResponseEntity.status(207).body(health); // Multi-Status
            case "DOWN" -> ResponseEntity.status(503).body(health); // Service Unavailable
            default -> ResponseEntity.ok(health);
        };
    }
}