package com.pride.server.domain.photo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PhotoEvaluationRepository extends JpaRepository<PhotoEvaluation, UUID> {
    List<PhotoEvaluation> findByUserId(String userId);
}