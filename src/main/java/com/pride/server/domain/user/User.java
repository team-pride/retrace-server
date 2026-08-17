package com.pride.server.domain.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue
    private UUID id;

    @Lob
    private float[] faceVector;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}