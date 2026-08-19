package com.pride.server.domain.interpretation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Tag(name = "Interpretation", description = "해석 카드 생성 API")
@RestController
@RequestMapping("/interpretation")
@RequiredArgsConstructor
public class InterpretationController {

    private final WebClient aiServerWebClient;
    private final WebClient openaiWebClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Operation(summary = "해석 카드 생성", description = "곡선+마커+효과판정 데이터를 종합해 사용자 언어로 설명")
    @GetMapping("/card")
    public Map<String, Object> generateCard(
            @RequestParam @Parameter(description = "사용자 UUID") String userId,
            @RequestParam @Parameter(description = "지표명") String indicator,
            @RequestParam @Parameter(description = "마커 ID") String markerId
    ) throws Exception {
        // 1. Python한테서 3가지 데이터 가져오기
        String curveData = aiServerWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/indicator/curve")
                        .queryParam("user_id", userId)
                        .queryParam("indicator", indicator)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();

        String markerData = aiServerWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/marker/list")
                        .queryParam("user_id", userId)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();

        String judgeData = aiServerWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/effect/judge")
                        .queryParam("user_id", userId)
                        .queryParam("indicator", indicator)
                        .queryParam("marker_id", markerId)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();

        // 2. 프롬프트 구성 (3개 섹션 json 구조로 요청)
        String prompt = """
                당신은 사용자의 얼굴 변화 데이터를 해석해주는 AI입니다.
                아래 규칙을 반드시 지켜서 설명해주세요.

                [금지 규칙]
                - "피부 나이", "노화 점수" 같은 절대 평가 표현을 쓰지 마세요.
                - 주름, 색소, 모공, 탄력 등 우리가 측정하지 않는 항목은 언급하지 마세요.
                - 인과관계를 단정하지 말고, "시점이 맞아떨어진다"는 정도로만 표현하세요.
                - "진단", "치료" 같은 의료적 표현을 쓰지 마세요.
                - 데이터가 부족하면 억지로 확정하지 말고 "판단하기 이른 시기"라고 정직하게 표현하세요.

                [데이터]
                곡선 데이터: %s
                마커 목록: %s
                효과 판정 결과: %s

                아래 3개 섹션으로 나눠서, 반드시 순수 json 형식으로만 응답하세요. (Respond strictly in json format)
                마크다운 코드블록(```)이나 설명 문구 없이 json 객체만 반환하세요.

                {
                  "noticedChange": {
                    "title": "눈에 띄는 변화를 한 문장으로 요약한 제목",
                    "description": "변화 시점과 수치를 포함한 1~2문장 설명"
                  },
                  "timingReason": {
                    "title": "시점 대조를 한 문장으로 요약한 제목",
                    "description": "마커(이벤트)와 변화 시점이 맞아떨어지는지에 대한 1~2문장 설명"
                  },
                  "nextStep": {
                    "title": "다음 행동 제안을 한 문장으로 요약한 제목",
                    "description": "사용자가 다음에 할 수 있는 행동에 대한 1~2문장 설명"
                  }
                }
                """.formatted(curveData, markerData, judgeData);

        // 3. GPT 호출
        Map<String, Object> requestBody = Map.of(
                "model", "gpt-4o-mini",
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                ),
                "response_format", Map.of("type", "json_object")
        );

        Map response = openaiWebClient.post()
                .uri("/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        List<Map> choices = (List<Map>) response.get("choices");
        Map message = (Map) choices.get(0).get("message");
        String content = (String) message.get("content");

        // 4. GPT가 반환한 JSON 문자열을 Map으로 파싱
        Map<String, Object> finalResponse = objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {});

        // 🔥 [안전장치 1] GPT가 빈 객체({})를 주거나 필수 키를 빼먹었을 경우 차단 (리뷰 봇 지적 사항 반영)
        if (!finalResponse.containsKey("noticedChange") ||
                !finalResponse.containsKey("timingReason") ||
                !finalResponse.containsKey("nextStep")) {
            throw new RuntimeException("GPT 응답 구조 누락 (필수 필드 없음)");
        }

        // 5. curveData에서 수치 직접 꺼내서 finalResponse에 예쁘게 주입하기
        try {
            Map<String, Object> curveMap = objectMapper.readValue(curveData, new TypeReference<Map<String, Object>>() {});
            Boolean eligible = (Boolean) curveMap.getOrDefault("eligible", false);

            if (eligible) {
                List<Map<String, Object>> points = (List<Map<String, Object>>) curveMap.get("points");

                if (points != null && !points.isEmpty()) {
                    // 제일 마지막 데이터가 '현재 수치'
                    double currentValue = ((Number) points.get(points.size() - 1).get("value")).doubleValue();

                    // 전체 평균 수치 계산
                    double averageValue = points.stream()
                            .mapToDouble(p -> ((Number) p.get("value")).doubleValue())
                            .average()
                            .orElse(0.0);

                    // 소수점 첫째 자리까지만 깔끔하게 포맷팅
                    String currentStr = String.format("%.1f", currentValue);
                    String averageStr = String.format("%.1f", averageValue);

                    // 🔥 [안전장치 2] noticedChange가 정상적인 객체(Map)인지 한 번 더 확인하여 ClassCastException 방어
                    Object noticedChangeObj = finalResponse.get("noticedChange");
                    if (noticedChangeObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> noticedChange = (Map<String, Object>) noticedChangeObj;
                        noticedChange.put("currentValue", currentStr);
                        noticedChange.put("averageValue", averageStr);
                    }
                }
            }
        } catch (Exception e) {
            // 파이썬 데이터 파싱 중 에러가 나더라도 GPT 텍스트 응답 자체는 정상적으로 나가도록 예외 처리
            System.out.println("수치 추출 중 에러 발생: " + e.getMessage());
        }

        return finalResponse;
    }
}