package com.marmitt.application.spring.config;

import com.marmitt.application.spring.web.OperatorCommandInterceptor;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Contrato da API publicado para quem opera o ctrade — principalmente um agente (T55): autenticação,
 * cabeçalhos obrigatórios dos comandos e a lista de códigos de recusa.
 */
@Configuration
public class OpenApiConfig {

    private static final String ADMIN_KEY = "adminKey";

    @Bean
    public OpenAPI ctradeOpenApi() {
        return new OpenAPI()
                .info(new Info().title("ctrade API").description("""
                        Operated by an AI agent (see .ai/product-direction.md).

                        Every request needs `X-Admin-Key`. Every command (POST, PUT, PATCH, DELETE) needs
                        `X-Reason` and may send `X-Operator`; commands are recorded in /api/admin/operator-actions.
                        Repeating a command already applied returns the current state without a new effect.

                        Every refusal returns ApiError with a stable `code`:
                        ADMIN_KEY_INVALID, REASON_REQUIRED, INVALID_REQUEST, MALFORMED_BODY, RESOURCE_NOT_FOUND,
                        METHOD_NOT_ALLOWED, UNSUPPORTED_MEDIA_TYPE, CAPABILITY_UNSUPPORTED, HTTP_ERROR,
                        INTERNAL_ERROR, RUNNER_NOT_FOUND, PORTFOLIO_NOT_FOUND, DEAD_LETTER_NOT_FOUND,
                        DEAD_LETTER_REPROCESS_REFUSED, RUNNER_INVALID_STATE, RUNNER_RECONCILIATION_INCOMPLETE,
                        RUNNER_NOT_ACCEPTING_SIGNALS, EXCHANGE_UNAVAILABLE, EXCHANGE_QUERY_FAILED, ORDER_NOT_FOUND,
                        POSITION_NOT_FOUND, POSITION_LOCKED, NO_MARKET_PRICE, SAFE_MODE_TRANSITION_REFUSED,
                        SAFE_MODE_STEP_TOO_LARGE, CONFIRMATION_REQUIRED, STRATEGY_NOT_FOUND,
                        STRATEGY_DEFINITION_CONFLICT, STRATEGY_REF_IN_USE, INVALID_STRATEGY_DEFINITION."""))
                .components(new Components().addSecuritySchemes(ADMIN_KEY, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-Admin-Key")))
                .addSecurityItem(new SecurityRequirement().addList(ADMIN_KEY));
    }

    /**
     * Declara {@code X-Reason} (obrigatório) e {@code X-Operator} em todo comando, sem repetir em cada
     * controller: quem aplica a regra é o {@link OperatorCommandInterceptor}.
     */
    @Bean
    public OpenApiCustomizer operatorCommandHeaders() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().values().forEach(path -> commands(path).forEach((method, operation) -> {
                if (operation.getParameters() != null && operation.getParameters().stream()
                        .anyMatch(p -> OperatorCommandInterceptor.REASON.equals(p.getName()))) {
                    return;
                }
                operation.addParametersItem(new HeaderParameter().name(OperatorCommandInterceptor.REASON)
                        .required(true).schema(new StringSchema())
                        .description("Why this command is being sent; recorded in the operator action log."));
                operation.addParametersItem(new HeaderParameter().name(OperatorCommandInterceptor.OPERATOR)
                        .required(false).schema(new StringSchema())
                        .description("Who is sending the command (for example the agent name)."));
            }));
        };
    }

    private static Map<PathItem.HttpMethod, Operation> commands(PathItem path) {
        Map<PathItem.HttpMethod, Operation> operations = new java.util.EnumMap<>(PathItem.HttpMethod.class);
        path.readOperationsMap().forEach((method, operation) -> {
            if (method == PathItem.HttpMethod.POST || method == PathItem.HttpMethod.PUT
                    || method == PathItem.HttpMethod.PATCH || method == PathItem.HttpMethod.DELETE) {
                operations.put(method, operation);
            }
        });
        return operations;
    }
}
