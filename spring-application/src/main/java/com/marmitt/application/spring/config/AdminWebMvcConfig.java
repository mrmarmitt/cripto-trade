package com.marmitt.application.spring.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AdminWebMvcConfig implements WebMvcConfigurer {

    private final AdminApiKeyInterceptor adminApiKeyInterceptor;

    public AdminWebMvcConfig(AdminApiKeyInterceptor adminApiKeyInterceptor) {
        this.adminApiKeyInterceptor = adminApiKeyInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // T55: um perímetro só. Toda a API (leitura e escrita) exige X-Admin-Key; o operador é um agente
        // e não há consumidor anônimo legítimo. /actuator (health, Prometheus) fica fora de /api.
        registry.addInterceptor(adminApiKeyInterceptor)
                .addPathPatterns("/api/**");
    }
}
