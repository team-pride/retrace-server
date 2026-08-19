package com.pride.server.domain.checkin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Tag(name = "Checkin", description = "체크인/D-day 카운트다운 관련 API")
@RestController
@RequestMapping("/checkin")
@RequiredArgsConstructor
public class CheckinController {

    private final WebClient aiServerWebClient;

    private static final int CHECKIN_INTERVAL_DAYS = 90; // 체크인 기준일, 필요시 조정
    private static final double REMEASURE_MAGNITUDE_THRESHOLD = 5.0; // 재측정 권유 임계값, 필요시 조정

    @Operation(summary = "체크인 상태 조회", description = "가장 최근 마커 기준 D-day, 체크인 도달 여부 반환")
    @GetMapping("/status")
    public Map<String, Object> status(
            @RequestParam @Parameter(description = "사용자 UUID") String userId
    ) {
        Map<String, Object> markerResponse = aiServerWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/marker/list")
                        .queryParam("user_id", userId)
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (markerResponse == null) {
            return Map.of(
                    "hasMarker", false,
                    "message", "AI 서버 응답이 비어있습니다."
            );
        }

        List<Map<String, Object>> markers = (List<Map<String, Object>>) markerResponse.get("markers");

        if (markers == null || markers.isEmpty()) {
            return Map.of(
                    "hasMarker", false,
                    "message", "등록된 마커가 없습니다."
            );
        }

        Map<String, Object> latestMarker = markers.stream()
                .max((a, b) -> ((String) a.get("marker_date")).compareTo((String) b.get("marker_date")))
                .orElseThrow();

        LocalDate markerDate = LocalDate.parse((String) latestMarker.get("marker_date"));
        LocalDate today = LocalDate.now();

        long daysSince = java.time.temporal.ChronoUnit.DAYS.between(markerDate, today);
        long daysRemaining = CHECKIN_INTERVAL_DAYS - daysSince;

        boolean isCheckinTime = daysRemaining <= 0;

        return Map.of(
                "hasMarker", true,
                "markerId", latestMarker.getOrDefault("marker_id", ""),
                "markerDate", latestMarker.getOrDefault("marker_date", ""),
                "daysSince", daysSince,
                "daysRemaining", Math.max(daysRemaining, 0),
                "isCheckinTime", isCheckinTime
        );
    }

    @Operation(summary = "재측정 권유 여부 판단", description = "곡선 변화가 크거나 신뢰 구간 벗어나면 오프라인 정밀 측정 권유")
    @GetMapping("/remeasure-suggestion")
    public Map<String, Object> remeasureSuggestion(
            @RequestParam @Parameter(description = "사용자 UUID") String userId,
            @RequestParam @Parameter(description = "지표명") String indicator
    ) {
        Map<String, Object> curveResponse = aiServerWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/indicator/curve")
                        .queryParam("user_id", userId)
                        .queryParam("indicator", indicator)
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (curveResponse == null) {
            return Map.of(
                    "suggestRemeasure", false,
                    "reasons", List.of(),
                    "message", "AI 서버 응답이 비어있습니다."
            );
        }

        List<Map<String, Object>> changePoints = (List<Map<String, Object>>) curveResponse.get("change_points");
        Boolean eligible = (Boolean) curveResponse.get("eligible");

        boolean suggestRemeasure = false;
        List<String> reasons = new ArrayList<>();

        if (Boolean.TRUE.equals(eligible) && changePoints != null) {
            for (Map<String, Object> cp : changePoints) {
                Object magnitude = cp.get("magnitude");
                if (magnitude != null && Math.abs(((Number) magnitude).doubleValue()) > REMEASURE_MAGNITUDE_THRESHOLD) {
                    suggestRemeasure = true;
                    reasons.add("곡선 변화 폭이 기준(" + REMEASURE_MAGNITUDE_THRESHOLD + ")을 초과했습니다");
                    break;
                }
            }
        }

        return Map.of(
                "suggestRemeasure", suggestRemeasure,
                "reasons", reasons,
                "message", suggestRemeasure
                        ? "외부에서 전문 장비로 확인해보시길 권합니다."
                        : "지금은 재측정이 필요하지 않아요."
        );
    }
}