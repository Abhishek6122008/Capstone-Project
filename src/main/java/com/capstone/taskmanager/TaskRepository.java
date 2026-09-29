package com.capstone.taskmanager;

import org.springframework.data.jpa.repository.JpaRepository;

// Spring Data generates the SQL for findAll/findById/save/delete at runtime
public interface TaskRepository extends JpaRepository<Task, Long> {
}
