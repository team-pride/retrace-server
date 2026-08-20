package com.pride.server.domain.marker;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Tag(name = "Marker", description = "관리 마커 관련 API (Python 서버 연동)")
@RestController
@RequestMapping("/marker")
@RequiredArgsConstructor
public class MarkerController {

    private final WebClient aiServerWebClient;

    @Operation(summary = "관리 마커 등록", description = "시술/루틴 시작 등, 자유 문장 그대로 저장")
    @PostMapping("/register")
    public Map<String, Object> register(
            @RequestParam @Parameter(description = "사용자 UUID") String userId,
            @RequestParam @Parameter(description = "마커 날짜 (YYYY-MM-DD)") String markerDate,
            @RequestParam @Parameter(description = "자유 문장") String note
    ) {
        Map<String, Object> markerResponse = aiServerWebClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/marker/register")
                        .queryParam("user_id", userId)
                        .queryParam("marker_date", markerDate)
                        .queryParam("note", note)
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (markerResponse == null) {
            throw new IllegalStateException("AI 서버로부터 마커 등록 응답을 받지 못했습니다.");
        }

        // CheckinController와 동일하게 프론트 컨벤션(camelCase)으로 변환해서 반환
        return Map.of(
                "markerId", markerResponse.getOrDefault("marker_id", ""),
                "userId", markerResponse.getOrDefault("user_id", ""),
                "markerDate", markerResponse.getOrDefault("marker_date", ""),
                "note", markerResponse.getOrDefault("note", ""),
                "createdAt", markerResponse.getOrDefault("created_at", "")
        );
    }

    @Operation(summary = "관리 마커 목록 조회")
    @GetMapping("/list")
    public Map<String, Object> list(
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
            return Map.of("markers", List.of());
        }

        List<Map<String, Object>> markers = (List<Map<String, Object>>) markerResponse.getOrDefault("markers", List.of());

        List<Map<String, Object>> converted = markers.stream()
                .map(marker -> Map.<String, Object>of(
                        "markerId", marker.getOrDefault("marker_id", ""),
                        "userId", marker.getOrDefault("user_id", ""),
                        "markerDate", marker.getOrDefault("marker_date", ""),
                        "note", marker.getOrDefault("note", ""),
                        "createdAt", marker.getOrDefault("created_at", "")
                ))
                .toList();

        return Map.of("markers", converted);
    }
}