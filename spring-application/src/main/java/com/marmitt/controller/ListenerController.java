package com.marmitt.controller;

import com.marmitt.core.dto.listener.ListenerStats;
import com.marmitt.core.ports.inbound.listener.ManageListenersPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller REST para gerenciamento de listeners.
 * Fornece endpoints para monitorar estatísticas e gerenciar listeners.
 */
@RestController
@RequestMapping("/api/listeners")
@Slf4j
public class ListenerController {
    
    private final ManageListenersPort manageListenersPort;
    
    public ListenerController(ManageListenersPort manageListenersPort) {
        this.manageListenersPort = manageListenersPort;
    }
    
    /**
     * Retorna estatísticas dos listeners registrados.
     * 
     * @return estatísticas dos listeners
     */
    @GetMapping("/stats")
    public ResponseEntity<ListenerStats> getListenerStats() {
        log.debug("Fetching listener statistics");
        ListenerStats stats = manageListenersPort.getListenerStats();
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Limpa todos os listeners registrados.
     * Útil para testes ou reinicialização do sistema.
     * 
     * @return confirmação da operação
     */
    @DeleteMapping("/clear")
    public ResponseEntity<String> clearAllListeners() {
        log.info("Clearing all listeners");
        manageListenersPort.clearAllListeners();
        return ResponseEntity.ok("All listeners cleared successfully");
    }
    
    /**
     * Lista todos os listeners de atualizações de ordens registrados.
     * 
     * @return lista de listeners registrados
     */
    @GetMapping("/orders")
    public ResponseEntity<ListenerListResponse> getOrderUpdateListeners() {
        log.debug("Fetching all order update listeners");
        var listeners = manageListenersPort.getAllOrderUpdateListeners();
        
        ListenerListResponse response = new ListenerListResponse(
            "OrderUpdateListener",
            listeners.size(),
            listeners.stream()
                .map(listener -> listener.getClass().getSimpleName())
                .toList()
        );
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Lista todos os listeners de atualizações de preços registrados.
     * 
     * @return lista de listeners registrados
     */
    @GetMapping("/prices")
    public ResponseEntity<ListenerListResponse> getPriceUpdateListeners() {
        log.debug("Fetching all price update listeners");
        var listeners = manageListenersPort.getAllPriceUpdateListeners();
        
        ListenerListResponse response = new ListenerListResponse(
            "PriceUpdateListener",
            listeners.size(),
            listeners.stream()
                .map(listener -> listener.getClass().getSimpleName())
                .toList()
        );
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Endpoint de health check para o sistema de listeners.
     * 
     * @return status do sistema
     */
    @GetMapping("/health")
    public ResponseEntity<ListenerHealthResponse> getListenerHealth() {
        ListenerStats stats = manageListenersPort.getListenerStats();
        
        ListenerHealthResponse health = new ListenerHealthResponse(
            "OK",
            stats.totalListenerCount() > 0 ? "ACTIVE" : "INACTIVE",
            stats
        );
        
        return ResponseEntity.ok(health);
    }
    
    /**
     * Response object para listagem de listeners.
     */
    public record ListenerListResponse(
        String listenerType,
        int count,
        java.util.List<String> listenerClassNames
    ) {}
    
    /**
     * Response object para health check.
     */
    public record ListenerHealthResponse(
        String status,
        String listenerSystemStatus,
        ListenerStats stats
    ) {}
}