package com.pride.server.domain.aiserver;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

@Tag(name = "AI Server", description = "Python AI 서버(retrace-ai-server) 연동 관련 API")
@RestController
@RequestMapping("/ai-server")
@RequiredArgsConstructor
public class AiServerTestController {

    private final WebClient aiServerWebClient;

    @Operation(
            summary = "AI 서버 연결 확인",
            description = "테스트/개발용 API입니다. Python AI 서버(retrace-ai-server)의 /health를 호출해 정상 연결 여부를 확인합니다."
    )
    @GetMapping("/health-check")
    public String checkHealth() {
        return aiServerWebClient.get()
                .uri("/health")
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
}