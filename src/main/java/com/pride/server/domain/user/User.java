package com.pride.server.domain.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue
    private UUID id;  // 익명 식별자 (로그인 없으니 이걸로 사용자 구분)

    @Lob
    private float[] faceVector;  // 본인 얼굴 기준 벡터 (Python이 계산해줄 값)

    @CreationTimestamp
    @Column(updatable = false)
    private String createdAt;
}