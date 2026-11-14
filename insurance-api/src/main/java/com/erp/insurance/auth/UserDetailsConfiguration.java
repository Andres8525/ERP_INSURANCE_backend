package com.erp.insurance.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@Configuration
public class UserDetailsConfiguration {
    @Bean
    UserDetailsService userDetailsService(UserRepository users) {
        return email -> users.findByEmailIgnoreCaseAndActiveTrue(email)
                .map(user -> User.withUsername(user.getEmail())
                        .password(user.getPasswordHash())
                        .authorities("ROLE_" + user.getRole().name())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }

    @Bean
    UserDetailsPasswordService userDetailsPasswordService(UserRepository users) {
        return (user, newEncodedPassword) -> {
            users.findByEmailIgnoreCaseAndActiveTrue(user.getUsername()).ifPresent(entity -> {
                entity.setPasswordHash(newEncodedPassword);
                users.save(entity);
            });
            return User.withUserDetails(user).password(newEncodedPassword).build();
        };
    }
}
