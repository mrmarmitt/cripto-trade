package com.marmitt.application.spring.controller;

import com.marmitt.binance.filters.SymbolFilterCache;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final List<SymbolFilterCache> filterCaches;

    public AdminController(List<SymbolFilterCache> filterCaches) {
        this.filterCaches = filterCaches;
    }

    @PostMapping("/filters/refresh")
    public ResponseEntity<Void> refreshFilters() {
        filterCaches.forEach(SymbolFilterCache::invalidateAll);
        return ResponseEntity.noContent().build();
    }
}
