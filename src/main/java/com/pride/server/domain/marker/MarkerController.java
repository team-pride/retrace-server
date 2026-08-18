package com.pride.server.domain.marker;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

@Tag(name = "Marker", description = "관리 마커 관련 API (Python 서버 연동)")
@RestController
@RequestMapping("/marker")
@RequiredArgsConstructor
public class MarkerController {

    private final WebClient aiServerWebClient;

    @Operation(summary = "관리 마커 등록", description = "시술/루틴 시작 등, 자유 문장 그대로 저장")
    @PostMapping("/register")
    public String register(
            @RequestParam @Parameter(description = "사용자 UUID") String userId,
            @RequestParam @Parameter(description = "마커 날짜 (YYYY-MM-DD)") String markerDate,
            @RequestParam @Parameter(description = "자유 문장") String note
    ) {
        return aiServerWebClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/marker/register")
                        .queryParam("user_id", userId)
                        .queryParam("marker_date", markerDate)
                        .queryParam("note", note)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    @Operation(summary = "관리 마커 목록 조회")
    @GetMapping("/list")
    public String list(
            @RequestParam @Parameter(description = "사용자 UUID") String userId
    ) {
        return aiServerWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/marker/list")
                        .queryParam("user_id", userId)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
}