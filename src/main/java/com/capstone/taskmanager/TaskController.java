package com.capstone.taskmanager;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * CRUD REST API. Errors (404, validation 400) are returned as RFC 7807 JSON
 * because spring.mvc.problemdetails.enabled=true, so no custom exception handler is needed.
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

	// Separate input type so clients can't set id/createdAt
	public record TaskRequest(
			@NotBlank @Size(max = 100) String title,
			@Size(max = 500) String description,
			Boolean done) {
	}

	private final TaskRepository tasks;

	public TaskController(TaskRepository tasks) {
		this.tasks = tasks;
	}

	@GetMapping
	public List<Task> list() {
		return tasks.findAll(Sort.by("id"));
	}

	@GetMapping("/{id}")
	public Task get(@PathVariable Long id) {
		return find(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Task create(@Valid @RequestBody TaskRequest req) {
		return tasks.save(apply(new Task(), req));
	}

	@PutMapping("/{id}")
	public Task update(@PathVariable Long id, @Valid @RequestBody TaskRequest req) {
		return tasks.save(apply(find(id), req));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		tasks.delete(find(id));
	}

	private Task find(Long id) {
		return tasks.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task " + id + " not found"));
	}

	private static Task apply(Task task, TaskRequest req) {
		task.setTitle(req.title().strip());
		task.setDescription(req.description());
		task.setDone(Boolean.TRUE.equals(req.done())); // missing "done" means not done
		return task;
	}
}
