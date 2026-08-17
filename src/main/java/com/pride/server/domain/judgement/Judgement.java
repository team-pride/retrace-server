package com.pride.server.domain.judgement;
import org.hibernate.annotations.CreationTimestamp;

import com.pride.server.domain.marker.Marker;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "judgements")
@Getter
@NoArgsConstructor
public class Judgement {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "marker_id", nullable = false)
    private Marker marker;             // 어느 마커에 대한 판정인지

    private String metricType;         // 어느 지표 기준인지 ("얼굴폭", "턱선각도" 등)

    @Enumerated(EnumType.STRING)
    private JudgementResult result;    // 관찰됨 / 관찰되지 않음 / 판단보류

    private Double differenceValue;    // 예측선 대비 실제값 차이
    private Double confidence;         // 신뢰도

    @CreationTimestamp
    @Column(updatable = false)
    private String createdAt;
}