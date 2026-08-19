package com.pride.server.domain.photo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface StoredPhotoRepository extends JpaRepository<StoredPhoto, java.util.UUID> {
    List<StoredPhoto> findByUserIdOrderByCapturedAtAsc(String userId);
    List<StoredPhoto> findByUserIdAndCapturedAt(String userId, LocalDate capturedAt);
}