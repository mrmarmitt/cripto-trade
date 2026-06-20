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
        // /api/exchanges/** expõe saldo e fills da conta na exchange — operações sensíveis
        // que devem exigir a mesma chave de admin que /api/admin/**.
        registry.addInterceptor(adminApiKeyInterceptor)
                .addPathPatterns("/api/admin/**", "/api/exchanges/**");
    }
}
