package com.conduit.chainservice.auth

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val authenticationFilter: AuthenticationFilter
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { requests ->
                requests
                    // Public endpoints
                    .requestMatchers(
                        "/actuator/health",
                        "/actuator/info",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/api-docs/**",
                        "/v3/api-docs/**"
                    ).permitAll()
                    // All API endpoints require authentication
                    .requestMatchers("/api/**").authenticated()
                    // Any other request is permitted (shouldn't happen)
                    .anyRequest().permitAll()
            }
            .addFilterBefore(authenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()
    }
}