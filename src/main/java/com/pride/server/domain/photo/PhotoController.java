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

@Tag(name = "Photo", description = "사진 판정/정규화 관련 API (Python 서버 연동)")
@RestController
@RequestMapping("/photo")
@RequiredArgsConstructor
public class PhotoController {

    private final WebClient aiServerWebClient;

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
}