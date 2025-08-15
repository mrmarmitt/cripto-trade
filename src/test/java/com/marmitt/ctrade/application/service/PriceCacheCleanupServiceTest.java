package com.marmitt.ctrade.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceCacheCleanupServiceTest {
    
    @Mock
    private PriceCacheService priceCacheService;
    
    @InjectMocks
    private PriceCacheCleanupService cleanupService;
    
    @Test
    void shouldPerformScheduledCleanupSuccessfully() {
        when(priceCacheService.clearExpiredEntries()).thenReturn(5);
        
        cleanupService.cleanupExpiredCacheEntries();
        
        verify(priceCacheService).clearExpiredEntries();
    }
    
    @Test
    void shouldHandleNoExpiredEntries() {
        when(priceCacheService.clearExpiredEntries()).thenReturn(0);
        
        cleanupService.cleanupExpiredCacheEntries();
        
        verify(priceCacheService).clearExpiredEntries();
    }
    
    @Test
    void shouldHandleExceptionDuringCleanup() {
        when(priceCacheService.clearExpiredEntries()).thenThrow(new RuntimeException("Database error"));
        
        // Não deve lançar exceção - apenas logar
        cleanupService.cleanupExpiredCacheEntries();
        
        verify(priceCacheService).clearExpiredEntries();
    }
}