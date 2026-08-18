package com.pride.server.domain.onboarding;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

@Tag(name = "Onboarding", description = "측정 범위 고지 온보딩 관련 API (Python 서버 연동)")
@RestController
@RequestMapping("/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final WebClient aiServerWebClient;

    @Operation(summary = "온보딩 고지 확인 처리")
    @PostMapping("/acknowledge")
    public String acknowledge(
            @RequestParam @Parameter(description = "사용자 UUID") String userId
    ) {
        return aiServerWebClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/onboarding/acknowledge")
                        .queryParam("user_id", userId)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    @Operation(summary = "온보딩 고지 확인 여부 조회")
    @GetMapping("/status")
    public String status(
            @RequestParam @Parameter(description = "사용자 UUID") String userId
    ) {
        return aiServerWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/onboarding/status")
                        .queryParam("user_id", userId)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
}