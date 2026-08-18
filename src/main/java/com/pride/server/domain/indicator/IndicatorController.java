package com.pride.server.domain.indicator;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.List;

@Tag(name = "Indicator", description = "지표 추출 및 곡선 관련 API (Python 서버 연동)")
@RestController
@RequestMapping("/indicator")
@RequiredArgsConstructor
public class IndicatorController {

    private final WebClient aiServerWebClient;

    @Operation(summary = "사진 여러 장 지표 일괄 추출", description = "EXIF 촬영일 자동 인식, 연도별 5~30장 제한 안내 포함")
    @PostMapping(value = "/extract-batch", consumes = "multipart/form-data")
    public String extractBatch(
            @RequestParam @Parameter(description = "사용자 UUID") String userId,
            @RequestParam(required = false) @Parameter(description = "EXIF 없을 때 대체 촬영일 (YYYY-MM-DD)") String fallbackCapturedAt,
            @RequestParam("files") List<MultipartFile> files
    ) throws IOException {

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        for (MultipartFile file : files) {
            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };
            body.add("files", resource);
        }

        return aiServerWebClient.post()
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