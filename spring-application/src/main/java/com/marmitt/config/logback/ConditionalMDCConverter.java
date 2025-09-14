package com.marmitt.config.logback;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.slf4j.MDC;

/**
 * Converter personalizado para Logback que só exibe campos MDC quando eles têm valor.
 * Evita exibir [correlation:N/A] quando não há correlationId.
 */
public class ConditionalMDCConverter extends ClassicConverter {

    private String mdcKey;
    private String label;
    
    @Override
    public void start() {
        // Formato: %cMDC{correlationId,correlation}
        String[] parts = getFirstOption() != null ? getFirstOption().split(",") : new String[]{};
        
        if (parts.length >= 1) {
            mdcKey = parts[0].trim();
        }
        if (parts.length >= 2) {
            label = parts[1].trim();
        } else {
            label = mdcKey; // usa a chave como label se não especificado
        }
        
        super.start();
    }
    
    @Override
    public String convert(ILoggingEvent event) {
        if (mdcKey == null) {
            return "";
        }
        
        String value = event.getMDCPropertyMap().get(mdcKey);
        
        // Só retorna o campo se houver valor válido
        if (value != null && !value.isEmpty() && !"N/A".equals(value)) {
            return "[" + label + ":" + value + "] ";
        }
        
        return "";
    }
}