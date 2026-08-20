package com.pride.server.domain.face;

import com.pride.server.domain.user.User;
import com.pride.server.domain.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Tag(name = "Face", description = "얼굴 인식 관련 API (Python 서버 연동)")
@RestController
@RequestMapping("/face")
@RequiredArgsConstructor
public class FaceController {

    private final WebClient aiServerWebClient;
    private final UserRepository userRepository;

    @Operation(summary = "본인 얼굴 기준 벡터 등록")
    @PostMapping(value = "/register", consumes = "multipart/form-data")
    public String register(
            @RequestParam @Parameter(description = "사용자 UUID") String userId,
            @RequestParam("files") List<MultipartFile> files
    ) throws IOException {

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        for (MultipartFile file : files) {
            body.add("files", file.getResource());
        }

        String result = aiServerWebClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/face/register")
                        .queryParam("user_id", userId)
                        .build())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        // Python 등록이 성공적으로 끝났으므로(예외 없이 여기까지 왔으므로),
        // Java User 테이블에도 등록 완료 여부를 기록해둔다.
        userRepository.findById(UUID.fromString(userId)).ifPresent(user -> {
            user.setFaceRegistered(true);
            userRepository.save(user);
        });

        return result;
    }

    @Operation(summary = "본인 여부 판정", description = "대조 사진 업로드 → 기준 벡터와 비교")
    @PostMapping(value = "/verify", consumes = "multipart/form-data")
    public String verify(
            @RequestParam @Parameter(description = "사용자 UUID") String userId,
            @RequestParam("file") MultipartFile file
    ) throws IOException {

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", file.getResource());

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

    @Operation(
            summary = "얼굴 등록 완료 여부 조회",
            description = "프론트가 앱 시작 시 이 userId가 이미 기준 얼굴을 등록했는지 확인하여, " +
                    "온보딩(얼굴 등록) 화면으로 보낼지 홈 화면으로 바로 보낼지 판단하는 데 사용한다."
    )
    @GetMapping("/status")
    public FaceStatusResponse checkFaceStatus(
            @RequestParam @Parameter(description = "사용자 UUID") String userId
    ) {
        UUID uuid;
        try {
            uuid = UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 사용자 UUID 형식입니다.");
        }

        User user = userRepository.findById(uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."));

        return new FaceStatusResponse(user.isFaceRegistered());
    }
}