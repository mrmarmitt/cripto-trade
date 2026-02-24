package com.marmitt.application.spring.infrastructure.persistence.entity;

import com.marmitt.core.enums.CapitalPoolingMode;
import com.marmitt.core.enums.SafeModeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("portfolios")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioEntity {

    @Id
    private UUID id;

    private String name;
    private boolean isActive;
    private SafeModeStatus safeModeStatus;
    private CapitalPoolingMode capitalPoolingMode;
    private Instant createdAt;

    @Version
    private Long version;
}
