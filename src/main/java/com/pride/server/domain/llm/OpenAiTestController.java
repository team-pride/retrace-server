package com.pride.server.domain.llm;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Tag(name = "LLM", description = "OpenAI 연동 테스트")
@RestController
@RequestMapping("/llm")
@RequiredArgsConstructor
public class OpenAiTestController {

    private final WebClient openaiWebClient;

    @Operation(summary = "GPT 연결 테스트")
    @GetMapping("/test")
    public String test(
            @RequestParam(defaultValue = "안녕이라고 한국어로 짧게 인사해줘")
            @Parameter(description = "테스트용 질문") String prompt
    ) {
        Map<String, Object> requestBody = Map.of(
                "model", "gpt-4o-mini",
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                )
        );

        Map response = openaiWebClient.post()
                .uri("/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        List<Map> choices = (List<Map>) response.get("choices");
        Map message = (Map) choices.get(0).get("message");
        return (String) message.get("content");
    }
}