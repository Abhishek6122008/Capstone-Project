package com.capstone.taskmanager;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
				.authorizeHttpRequests(auth -> auth
						.anyRequest().authenticated())
				// Browser users get Spring's built-in login page at /login
				.formLogin(Customizer.withDefaults())
				// curl / Postman / CI smoke tests use: curl -u admin:admin123 ...
				.httpBasic(Customizer.withDefaults())
				.logout(Customizer.withDefaults())
				.csrf(csrf -> csrf
						// Token goes in the XSRF-TOKEN cookie; the UI echoes it back in the X-XSRF-TOKEN header
						.spa()
						// Requests that carry their own credentials can't be forged cross-site, so skip CSRF for them
						.ignoringRequestMatchers(req -> req.getHeader("Authorization") != null));
		return http.build();
	}

	// Loads users from the app_user table (proves login is DB-backed)
	@Bean
	UserDetailsService userDetailsService(AppUserRepository users) {
		return username -> users.findByUsername(username)
				.map(u -> User.withUsername(u.getUsername()).password(u.getPassword()).roles("USER").build())
				.orElseThrow(() -> new UsernameNotFoundException(username));
	}

	// Stores hashes as {bcrypt}$2a$...
	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	// Creates the admin account on first start. Credentials come from env vars (later a K8s Secret).
	@Bean
	CommandLineRunner seedAdmin(AppUserRepository users, PasswordEncoder encoder,
			@Value("${app.admin.username}") String username,
			@Value("${app.admin.password}") String password) {
		return args -> {
			if (users.findByUsername(username).isEmpty()) {
				users.save(new AppUser(username, encoder.encode(password)));
			}
		};
	}
}
