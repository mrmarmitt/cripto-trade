package com.marmitt.ctrade;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.marmitt.ctrade.config.TradingProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(TradingProperties.class)
public class CtradeApplication {

	public static void main(String[] args) {
		SpringApplication.run(CtradeApplication.class, args);
	}

}
