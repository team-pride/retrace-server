package com.pride.server.domain.effect;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

@Tag(name = "Effect", description = "관리 효과 판정 관련 API (Python 서버 연동)")
@RestController
@RequestMapping("/effect")
@RequiredArgsConstructor
public class EffectController {

    private final WebClient aiServerWebClient;

    @Operation(summary = "관리 효과 판정", description = "observed/not_observed/pending 3가지로 판정")
    @GetMapping("/judge")
    public String judge(
            @RequestParam @Parameter(description = "사용자 UUID") String userId,
            @RequestParam @Parameter(description = "지표명") String indicator,
            @RequestParam @Parameter(description = "마커 ID") String markerId
    ) {
        return aiServerWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/effect/judge")
                        .queryParam("user_id", userId)
                        .queryParam("indicator", indicator)
                        .queryParam("marker_id", markerId)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
}