package com.example.application.benchmark;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Lets the harness call {@link BenchmarkEndpoint} without logging in. Its own chain, ahead of
 * the app's, matching actuator requests only: the app's pages stay as secured as before.
 */
@Configuration
@ConditionalOnProperty("management.server.port")
class BenchmarkSecurity {

    @Bean
    @Order(1)
    SecurityFilterChain benchmarkSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher(EndpointRequest.toAnyEndpoint())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
