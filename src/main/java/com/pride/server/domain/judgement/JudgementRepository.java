package com.pride.server.domain.judgement;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface JudgementRepository extends JpaRepository<Judgement, UUID> {
    List<Judgement> findByMarkerId(java.util.UUID markerId);
}