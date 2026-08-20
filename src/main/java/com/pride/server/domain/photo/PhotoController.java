package com.pride.server.domain.photo;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Tag(name = "Photo", description = "사진 판정/정규화 관련 API (Python 서버 연동)")
@RestController
@RequestMapping("/photo")
@RequiredArgsConstructor
public class PhotoController {

    private final WebClient aiServerWebClient;
    private final StoredPhotoRepository storedPhotoRepository;
    private final PhotoEvaluationRepository photoEvaluationRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Operation(summary = "사진 판정", description = "각도/블러/얼굴검출 신뢰도로 pass/conditional/exclude 판정")
    @PostMapping(value = "/evaluate", consumes = "multipart/form-data")
    public String evaluate(
            @RequestParam @Parameter(description = "재시도 추적용 키, 예: user123_2024-05_front") String photoKey,
            @RequestParam(required = false) @Parameter(description = "사용자 UUID (선택)") String userId,
            @RequestParam("file") MultipartFile file
    ) throws IOException {

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", file.getResource());

        String result = aiServerWebClient.post()
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

        if (userId != null) {
            saveEvaluation(userId, result);
        }

        return result;
    }

    @Operation(
            summary = "사진 여러 장 일괄 판정",
            description = "여러 장을 한 번에 pass/conditional/exclude로 판정한다. " +
                    "판정 결과는 서버에 저장되어 업로드 통계(GET /photo/upload-summary)에 반영된다."
    )
    @PostMapping(value = "/evaluate-batch", consumes = "multipart/form-data")
    public String evaluateBatch(
            @RequestParam @Parameter(description = "사용자 UUID") String userId,
            @RequestParam("files") List<MultipartFile> files
    ) throws IOException {

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        for (MultipartFile file : files) {
            body.add("files", file.getResource());
        }

        String result = aiServerWebClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/photo/evaluate-batch")
                        .queryParam("user_id", userId)
                        .build())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        saveEvaluationBatch(userId, result);

        return result;
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
            description = "그래프에 반영된 사진 수, 연도별 사진 분포, " +
                    "판정 등급별(통과/참고용/제외) 개수, 전체 업로드 시도 수를 반환한다."
    )
    @GetMapping("/upload-summary")
    public Map<String, Object> uploadSummary(
            @RequestParam @Parameter(description = "사용자 UUID") String userId
    ) {
        // 1. 그래프에 들어간 사진 수 + 연도별 분포 (StoredPhoto 기반)
        List<StoredPhoto> photos = storedPhotoRepository.findByUserIdOrderByCapturedAtAsc(userId);

        Map<Integer, Long> yearCounts = photos.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getCapturedAt().getYear(),
                        TreeMap::new,
                        Collectors.counting()
                ));

        // 2. 판정 등급별 개수 + 전체 업로드 수 (PhotoEvaluation 기반)
        List<PhotoEvaluation> evaluations = photoEvaluationRepository.findByUserId(userId);

        Map<String, Long> gradeCounts = evaluations.stream()
                .filter(e -> e.getGrade() != null)
                .collect(Collectors.groupingBy(PhotoEvaluation::getGrade, Collectors.counting()));

        Map<String, Object> result = new HashMap<>();
        result.put("succeededCount", photos.size());       // "그래프에 들어간 사진" 수
        result.put("yearCounts", yearCounts);               // {"2019": 14, ...}
        result.put("totalEvaluatedCount", evaluations.size()); // "넣은 사진 187장"
        result.put("gradeCounts", gradeCounts);             // {"pass": 81, "conditional": 38, "exclude": 55}

        return result;
    }

    // ---- 내부 헬퍼 ----

    /**
     * POST /photo/evaluate(단건) 결과를 파싱해서 저장한다.
     * 저장에 실패해도 evaluate 자체의 응답은 정상 반환하되,
     * 원인 추적이 가능하도록 반드시 로그를 남긴다 (조용히 무시하지 않음).
     */
    private void saveEvaluation(String userId, String rawResult) {
        try {
            Map<String, Object> parsed = objectMapper.readValue(rawResult, Map.class);
            PhotoEvaluation eval = new PhotoEvaluation();
            eval.setUserId(userId);
            eval.setPhotoKey((String) parsed.get("photo_key"));
            eval.setGrade((String) parsed.get("grade"));
            eval.setReasons(objectMapper.writeValueAsString(parsed.get("reasons")));
            photoEvaluationRepository.save(eval);
        } catch (Exception e) {
            log.error("PhotoEvaluation 저장 실패 - userId: {}, error: {}", userId, e.getMessage(), e);
        }
    }

    /**
     * POST /photo/evaluate-batch 결과(results 배열)를 파싱해서 각 항목을 저장한다.
     * 항목 하나가 저장에 실패해도 나머지 항목은 계속 저장을 시도하며,
     * 실패한 항목은 로그로 남겨 추후 원인 파악이 가능하게 한다.
     */
    private void saveEvaluationBatch(String userId, String rawResult) {
        Map<String, Object> parsed;
        try {
            parsed = objectMapper.readValue(rawResult, Map.class);
        } catch (Exception e) {
            log.error("evaluate-batch 응답 파싱 실패 - userId: {}, error: {}", userId, e.getMessage(), e);
            return;
        }

        List<Map<String, Object>> results = (List<Map<String, Object>>) parsed.get("results");
        if (results == null) {
            log.warn("evaluate-batch 응답에 results 필드가 없음 - userId: {}", userId);
            return;
        }

        for (Map<String, Object> item : results) {
            try {
                PhotoEvaluation eval = new PhotoEvaluation();
                eval.setUserId(userId);
                eval.setPhotoKey((String) item.get("photo_key"));
                eval.setGrade((String) item.get("grade")); // status가 skipped/failed면 grade가 null일 수 있음
                Object reasons = item.get("reasons");
                eval.setReasons(reasons != null ? objectMapper.writeValueAsString(reasons) : null);
                photoEvaluationRepository.save(eval);
            } catch (Exception e) {
                // 한 항목이 실패해도 나머지 항목 저장은 계속 진행
                log.error("PhotoEvaluation 저장 실패 - userId: {}, photoKey: {}, error: {}",
                        userId, item.get("photo_key"), e.getMessage(), e);
            }
        }
    }
}