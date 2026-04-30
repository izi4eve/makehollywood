package com.makehollywood.repository;

import com.makehollywood.model.Idea;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IdeaRepository extends JpaRepository<Idea, Long> {
    List<Idea> findByUserIdOrderByCreatedAtDesc(Long userId);
}