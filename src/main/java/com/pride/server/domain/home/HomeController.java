package com.pride.server.domain.home;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

        // 최신값 (captured_at이 가장 큰 포인트) — Python이 정렬을 보장한다고 단정하지 않고 직접 max로 탐색
        Map<String, Object> latestPoint = points.stream()
                .max((a, b) -> ((String) a.get("captured_at")).compareTo((String) b.get("captured_at")))
                .orElseThrow();
        double latestValue = ((Number) latestPoint.get("value")).doubleValue();
        String latestDate = (String) latestPoint.get("captured_at");
        LocalDate latestDateParsed = LocalDate.parse(latestDate);

        // N주 전 값 — 기준을 서버의 "오늘"이 아니라 "가장 최근 촬영일" 기준으로 계산
        LocalDate targetPastDate = latestDateParsed.minusWeeks(RECENT_WEEKS);

        // targetPastDate 이전(또는 그 시점) 데이터 중 가장 최근 것을 pastPoint로 선택
        Map<String, Object> pastPoint = points.stream()
                .filter(p -> !LocalDate.parse((String) p.get("captured_at")).isAfter(targetPastDate))
                .max((a, b) -> ((String) a.get("captured_at")).compareTo((String) b.get("captured_at")))
                .orElse(null);

        // 8주 전 시점에 해당하는 데이터가 아예 없으면, 억지로 비교하지 않고 데이터 부족으로 응답
        if (pastPoint == null) {
            return Map.of(
                    "eligible", false,
                    "reasons", List.of(RECENT_WEEKS + "주 이상의 기록이 필요해요")
            );
        }

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

    @Operation(summary = "지금 하고 있는 관리 목록", description = "마커를 note별로 그룹핑해 횟수와 최근 날짜 반환")
    @GetMapping("/current-care")
    public List<Map<String, Object>> currentCare(
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
            return List.of();
        }

        List<Map<String, Object>> markers = (List<Map<String, Object>>) markerResponse.get("markers");
        if (markers == null || markers.isEmpty()) {
            return List.of();
        }

        Map<String, List<Map<String, Object>>> groupedByNote = markers.stream()
                .collect(Collectors.groupingBy(m -> (String) m.get("note")));

        return groupedByNote.entrySet().stream()
                .map(entry -> {
                    String note = entry.getKey();
                    List<Map<String, Object>> group = entry.getValue();

                    String latestDate = group.stream()
                            .map(m -> (String) m.get("marker_date"))
                            .max(String::compareTo)
                            .orElse(null);

                    Map<String, Object> result = new HashMap<>();
                    result.put("note", note);
                    result.put("count", group.size());
                    result.put("latestDate", latestDate);
                    return result;
                })
                .sorted((a, b) -> ((String) b.get("latestDate")).compareTo((String) a.get("latestDate")))
                .toList();
    }
}