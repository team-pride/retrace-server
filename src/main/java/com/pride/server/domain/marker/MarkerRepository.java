package com.pride.server.domain.marker;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface MarkerRepository extends JpaRepository<Marker, UUID> {
    List<Marker> findByUserId(UUID userId);
}