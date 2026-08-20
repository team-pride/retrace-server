package com.pride.server.domain.photo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * POST /photo/evaluate-batch(Python) 호출 결과를 저장하는 엔티티.
 * 업로드 기본 화면의 "통과/참고용/제외" 통계 집계에 사용된다.
 */
@Entity
@Table(name = "photo_evaluations")
@Getter
@Setter
@NoArgsConstructor
public class PhotoEvaluation {

    @Id
    @GeneratedValue
    private UUID id;

    private String userId;

    private String photoKey;

    /** pass / conditional / exclude */
    private String grade;

    @Column(columnDefinition = "TEXT")
    private String reasons; // JSON 문자열로 저장 (예: ["상하 각도 10.0도로 허용 범위 초과"])

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}