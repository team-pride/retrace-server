package com.pride.server.domain.home;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "Home", description = "홈 화면 요약 API")
@RestController
@RequestMapping("/home")
@RequiredArgsConstructor
public class HomeController {

    private final WebClient aiServerWebClient;

    private static final int RECENT_WEEKS = 8; // "8주 전 대비" 기준, 필요시 조정

    @Operation(summary = "홈 화면 요약", description = "최근 지표 변화 요약 (최신값, N주 전 대비, 트렌드 방향)")
    @GetMapping("/summary")
    public Map<String, Object> summary(
            @RequestParam @Parameter(description = "사용자 UUID") String userId,
            @RequestParam @Parameter(description = "지표명") String indicator
    ) {
        Map<String, Object> curveResponse;
        try {
            curveResponse = aiServerWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/indicator/curve")
                            .queryParam("user_id", userId)
                            .queryParam("indicator", indicator)
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        } catch (Exception e) {
            return Map.of("eligible", false, "message", "데이터를 불러오지 못했습니다.");
        }

        if (curveResponse == null) {
            return Map.of("eligible", false, "message", "AI 서버 응답이 비어있습니다.");
        }

        Boolean eligible = (Boolean) curveResponse.getOrDefault("eligible", false);
        if (!Boolean.TRUE.equals(eligible)) {
            return Map.of(
                    "eligible", false,
                    "reasons", curveResponse.getOrDefault("reasons", List.of())
            );
        }

        List<Map<String, Object>> points = (List<Map<String, Object>>) curveResponse.get("points");
        if (points == null || points.isEmpty()) {
            return Map.of("eligible", false, "message", "표시할 데이터가 없습니다.");
        }

        // 최신값 (가장 마지막 포인트)
        Map<String, Object> latestPoint = points.get(points.size() - 1);
        double latestValue = ((Number) latestPoint.get("value")).doubleValue();
        String latestDate = (String) latestPoint.get("captured_at");

        // N주 전 값 (오늘 - N*7일에 가장 가까운 포인트)
        LocalDate targetPastDate = LocalDate.now().minusWeeks(RECENT_WEEKS);
        Map<String, Object> pastPoint = points.stream()
                .min((a, b) -> {
                    long diffA = Math.abs(ChronoUnit.DAYS.between(
                            targetPastDate, LocalDate.parse((String) a.get("captured_at"))));
                    long diffB = Math.abs(ChronoUnit.DAYS.between(
                            targetPastDate, LocalDate.parse((String) b.get("captured_at"))));
                    return Long.compare(diffA, diffB);
                })
                .orElse(points.get(0));

        double pastValue = ((Number) pastPoint.get("value")).doubleValue();
        String pastDate = (String) pastPoint.get("captured_at");

        double diff = latestValue - pastValue;
        String trend = diff > 0 ? "increasing" : (diff < 0 ? "decreasing" : "stable");

        Map<String, Object> result = new HashMap<>();
        result.put("eligible", true);
        result.put("latestValue", String.format("%.1f", latestValue));
        result.put("latestDate", latestDate);
        result.put("pastValue", String.format("%.1f", pastValue));
        result.put("pastDate", pastDate);
        result.put("diff", String.format("%+.1f", diff));
        result.put("trend", trend);

        return result;
    }
}