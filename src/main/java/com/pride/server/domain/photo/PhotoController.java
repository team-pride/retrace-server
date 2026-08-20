package com.pride.server.domain.photo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Tag(name = "Photo", description = "사진 판정/정규화 관련 API (Python 서버 연동)")
@RestController
@RequestMapping("/photo")
@RequiredArgsConstructor
public class PhotoController {

    private final WebClient aiServerWebClient;
    private final StoredPhotoRepository storedPhotoRepository;
    // TODO: evaluate-batch 연동 시 아래 두 개 추가 예정
    // private final PhotoEvaluationRepository photoEvaluationRepository;
    // private final ObjectMapper objectMapper;

    @Operation(summary = "사진 판정", description = "각도/블러/얼굴검출 신뢰도로 pass/conditional/exclude 판정")
    @PostMapping(value = "/evaluate", consumes = "multipart/form-data")
    public String evaluate(
            @RequestParam @Parameter(description = "재시도 추적용 키, 예: user123_2024-05_front") String photoKey,
            @RequestParam(required = false) @Parameter(description = "사용자 UUID (선택)") String userId,
            @RequestParam("file") MultipartFile file
    ) throws IOException {

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", file.getResource());

        return aiServerWebClient.post()
                .uri(uriBuilder -> {
                    uriBuilder.path("/api/v1/photo/evaluate")
                            .queryParam("photo_key", photoKey);
                    if (userId != null) {
                        uriBuilder.queryParam("user_id", userId);
                    }
                    return uriBuilder.build();
                })
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();
        // TODO: evaluate-batch 연동 시, 여기서 판정 결과(grade)를 PhotoEvaluation으로 저장하도록 확장 예정
    }

    @Operation(summary = "사진 정규화", description = "눈 수평정렬 + 크기정렬 + 색보정 → 표준 이미지(base64) 반환")
    @PostMapping(value = "/normalize", consumes = "multipart/form-data")
    public String normalize(
            @RequestParam(required = false) @Parameter(description = "사용자 UUID (선택)") String userId,
            @RequestParam("file") MultipartFile file
    ) throws IOException {

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", file.getResource());

        return aiServerWebClient.post()
                .uri(uriBuilder -> {
                    uriBuilder.path("/api/v1/photo/normalize");
                    if (userId != null) {
                        uriBuilder.queryParam("user_id", userId);
                    }
                    return uriBuilder.build();
                })
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    @Operation(
            summary = "업로드 통계 조회 (업로드 기본 화면용)",
            description = "그래프에 반영된 사진 수와 연도별 사진 분포를 반환한다. " +
                    "통과/참고용/제외 개수, 전체 업로드 시도 수는 evaluate-batch 연동 완료 후 추가 예정."
    )
    @GetMapping("/upload-summary")
    public Map<String, Object> uploadSummary(
            @RequestParam @Parameter(description = "사용자 UUID") String userId
    ) {
        List<StoredPhoto> photos = storedPhotoRepository.findByUserIdOrderByCapturedAtAsc(userId);

        // 연도별 사진 개수 (TreeMap으로 연도 오름차순 정렬)
        Map<Integer, Long> yearCounts = photos.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getCapturedAt().getYear(),
                        TreeMap::new,
                        Collectors.counting()
                ));

        Map<String, Object> result = new HashMap<>();
        result.put("succeededCount", photos.size()); // "그래프에 들어간 사진" 수
        result.put("yearCounts", yearCounts);         // {"2019": 14, "2020": 12, ...}

        // TODO: evaluate-batch 연동 완료 후 아래 필드 추가 예정
        // result.put("totalEvaluatedCount", ...);   // "넣은 사진 187장"
        // result.put("gradeCounts", ...);            // {"pass": 81, "conditional": 38, "exclude": 55}

        return result;
    }
}