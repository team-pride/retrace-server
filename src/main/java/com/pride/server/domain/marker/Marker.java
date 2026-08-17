package com.pride.server.domain.marker;

import com.pride.server.domain.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "markers")
@Getter
@NoArgsConstructor
public class Marker {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String rawText;        // 사용자가 입력한 자유 문장 원문
    private String type;           // LLM이 뽑아낸 관리 종류 ("시술", "홈케어" 등)
    private LocalDate markerDate;  // 마커 날짜

    private Boolean isNoAction;    // "아무것도 안 함" 마커인지 (대조군용)

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDate createdAt;
}