package com.capstone.taskmanager;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// "user" is a reserved word in PostgreSQL, hence the explicit table name
@Entity
@Table(name = "app_user")
public class AppUser {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 50)
	private String username;

	// BCrypt hash, never the plain password
	@Column(nullable = false)
	private String password;

	protected AppUser() {
	}

	public AppUser(String username, String password) {
		this.username = username;
		this.password = password;
	}

	public String getUsername() { return username; }
	public String getPassword() { return password; }
}
