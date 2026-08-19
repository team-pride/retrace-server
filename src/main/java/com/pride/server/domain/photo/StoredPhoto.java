package com.pride.server.domain.photo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "stored_photos")
@Getter
@Setter
@NoArgsConstructor
public class StoredPhoto {

    @Id
    @GeneratedValue
    private UUID id;

    private String userId;

    private LocalDate capturedAt;

    @Column(columnDefinition = "TEXT")
    private String imageBase64;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}