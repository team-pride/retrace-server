package com.pride.server.domain.indicator;

import com.pride.server.domain.photo.PhotoStorageService;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Tag(name = "Indicator", description = "지표 추출 및 곡선 관련 API (Python 서버 연동)")
@RestController
@RequestMapping("/indicator")
@RequiredArgsConstructor
public class IndicatorController {

    private final WebClient aiServerWebClient;
    private final PhotoStorageService photoStorageService;

    @Operation(
            summary = "사진 여러 장 지표 일괄 추출 + 원본 저장",
            description = "EXIF 촬영일 자동 인식, 연도별 5~30장 제한 안내 포함. " +
                    "Python 서버로 지표를 추출함과 동시에, 되감기/두 시점 비교 화면에서 쓸 원본 사진도 함께 저장한다."
    )
    @PostMapping(value = "/extract-batch", consumes = "multipart/form-data")
    public Map<String, Object> extractBatch(
            @RequestParam @Parameter(description = "사용자 UUID") String userId,
            @RequestParam(required = false) @Parameter(description = "EXIF 없을 때 대체 촬영일 (YYYY-MM-DD)") String fallbackCapturedAt,
            @RequestParam("files") List<MultipartFile> files
    ) throws Exception {

        // fallbackCapturedAt은 문자열로 한 번만 파싱해서, Python 호출과 사진 저장 양쪽에 동일하게 사용
        LocalDate parsedFallback = fallbackCapturedAt != null ? LocalDate.parse(fallbackCapturedAt) : null;

        // 1. Python 서버로 지표 추출 요청 (기존 로직)
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        for (MultipartFile file : files) {
            body.add("files", file.getResource());
        }

        String indicatorResult = aiServerWebClient.post()
                .uri(uriBuilder -> {
                    uriBuilder.path("/api/v1/indicator/extract-batch")
                            .queryParam("user_id", userId);
                    if (fallbackCapturedAt != null) {
                        uriBuilder.queryParam("fallback_captured_at", fallbackCapturedAt);
                    }
                    return uriBuilder.build();
                })
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        // 2. 같은 사진들을 되감기/비교용으로 서버(RDS)에도 저장
        //    Python과 동일한 fallback 날짜를 넘겨서, EXIF 없는 사진의 처리 결과가 서로 어긋나지 않도록 함
        PhotoStorageService.StoreResult storeResult = photoStorageService.storePhotos(userId, files, parsedFallback);

        // 3. 지표 추출 결과 + 사진 저장 결과를 합쳐서 응답
        return Map.of(
                "indicatorResult", indicatorResult,
                "photoStoreSummary", storeResult.savedCount() + "장 저장 완료"
                        + (storeResult.skipped().isEmpty() ? "" : " (촬영일 없어 스킵: " + storeResult.skipped() + ")")
        );
    }

    @Operation(summary = "지표 시계열 곡선 조회", description = "indicator: face_width_ratio | jaw_angle_deg | eyelid_height_ratio | mouth_corner_angle_deg")
    @GetMapping("/curve")
    public String curve(
            @RequestParam @Parameter(description = "사용자 UUID") String userId,
            @RequestParam @Parameter(description = "지표명") String indicator
    ) {
        return aiServerWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/indicator/curve")
                        .queryParam("user_id", userId)
                        .queryParam("indicator", indicator)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
}