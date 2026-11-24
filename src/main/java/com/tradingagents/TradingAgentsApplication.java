package com.tradingagents;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TradingAgentsApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradingAgentsApplication.class, args);
    }
}
