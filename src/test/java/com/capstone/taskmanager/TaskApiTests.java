package com.capstone.taskmanager;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

// Runs against in-memory H2 (Postgres compatibility mode) so Jenkins needs no database
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:test;MODE=PostgreSQL")
@AutoConfigureMockMvc
class TaskApiTests {

	@Autowired
	MockMvc mvc;

	@Test
	void apiRequiresLogin() throws Exception {
		mvc.perform(get("/api/tasks")).andExpect(status().isUnauthorized());
		// A browser navigating to a page is sent to the login form instead
		mvc.perform(get("/").accept(MediaType.TEXT_HTML)).andExpect(status().is3xxRedirection());
		mvc.perform(get("/api/tasks").with(httpBasic("admin", "wrong"))).andExpect(status().isUnauthorized());
	}

	@Test
	void healthIsPublic() throws Exception {
		mvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

	@Test
	void crudFlowWithSeededAdmin() throws Exception {
		var admin = httpBasic("admin", "admin123");

		String body = mvc.perform(post("/api/tasks").with(admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"Write Dockerfile\",\"description\":\"week 2\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.title").value("Write Dockerfile"))
				.andExpect(jsonPath("$.done").value(false))
				.andReturn().getResponse().getContentAsString();
		int id = JsonPath.read(body, "$.id");

		mvc.perform(put("/api/tasks/" + id).with(admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"Write Dockerfile\",\"done\":true}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.done").value(true));

		mvc.perform(get("/api/tasks/" + id).with(admin)).andExpect(jsonPath("$.done").value(true));
		mvc.perform(delete("/api/tasks/" + id).with(admin)).andExpect(status().isNoContent());
		mvc.perform(get("/api/tasks/" + id).with(admin))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("Task " + id + " not found"));
	}

	@Test
	void blankTitleIsRejected() throws Exception {
		mvc.perform(post("/api/tasks").with(httpBasic("admin", "admin123"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\" \"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void sessionRequestsWithoutCsrfTokenAreBlocked() throws Exception {
		mvc.perform(post("/api/tasks").with(user("admin"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"forged\"}"))
				.andExpect(status().isForbidden());
	}
}
