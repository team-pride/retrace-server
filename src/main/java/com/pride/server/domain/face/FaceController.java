package com.pride.server.domain.face;

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

@Tag(name = "Face", description = "얼굴 인식 관련 API (Python 서버 연동)")
@RestController
@RequestMapping("/face")
@RequiredArgsConstructor
public class FaceController {

    private final WebClient aiServerWebClient;

    @Operation(summary = "본인 얼굴 기준 벡터 등록")
    @PostMapping(value = "/register", consumes = "multipart/form-data")
    public String register(
            @RequestParam @Parameter(description = "사용자 UUID") String userId,
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
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/face/register")
                        .queryParam("user_id", userId)
                        .build())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    @Operation(summary = "본인 여부 판정", description = "대조 사진 업로드 → 기준 벡터와 비교")
    @PostMapping(value = "/verify", consumes = "multipart/form-data")
    public String verify(
            @RequestParam @Parameter(description = "사용자 UUID") String userId,
            @RequestParam("file") MultipartFile file    // ← files List가 아니라 file 단수로!
    ) throws IOException {

        ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource);   // ← "files"가 아니라 "file"

        return aiServerWebClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/face/verify")
                        .queryParam("user_id", userId)
                        .build())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
}