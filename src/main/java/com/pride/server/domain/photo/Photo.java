package com.pride.server.domain.photo;

import com.pride.server.domain.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "photos")
@Getter
@NoArgsConstructor
public class Photo {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;              // 누구 사진인지

    private String filePath;        // 저장된 파일 경로
    private LocalDate takenAt;      // 촬영일 (EXIF에서 추출)

    @Enumerated(EnumType.STRING)
    private PhotoGrade grade;       // 통과 / 조건부 / 제외

    private String reason;          // 등급 사유 ("각도 12도로 벗어남" 등)

    // 지표값 (F1 통과 후 Python이 계산)
    private Double faceWidthRatio;   // 얼굴 폭 비율
    private Double jawAngle;         // 턱선 각도
    private Double eyelidHeight;     // 눈꺼풀 높이
    private Double mouthAngle;       // 입가 각도

    private Integer eyeDistancePx;   // 눈동자 간 거리(정규화 기준값)
}