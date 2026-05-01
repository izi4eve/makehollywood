package com.makehollywood.repository;

import com.makehollywood.model.Script;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScriptRepository extends JpaRepository<Script, Long> {
    List<Script> findByUserIdOrderByCreatedAtDesc(Long userId);
}
