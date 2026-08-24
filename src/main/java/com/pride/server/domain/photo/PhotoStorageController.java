package com.pride.server.domain.photo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

@Tag(name = "PhotoStorage", description = "저장된 사진 조회 API (되감기, 두 시점 비교용). " +
        "사진 저장 자체는 POST /indicator/extract-batch 에서 지표 추출과 함께 처리된다.")
@RestController
@RequestMapping("/photos")
@RequiredArgsConstructor
public class PhotoStorageController {

    private final StoredPhotoRepository storedPhotoRepository;
    private final WebClient aiServerWebClient;

    @Operation(summary = "저장된 사진 목록 조회 (되감기)", description = "촬영일 순으로 정렬된 사진 목록 반환")
    @GetMapping
    public List<Map<String, Object>> getPhotos(
            @RequestParam @Parameter(description = "사용자 UUID") String userId
    ) {
        List<StoredPhoto> photos = storedPhotoRepository.findByUserIdOrderByCapturedAtAsc(userId);

        return photos.stream()
                .map(p -> Map.<String, Object>of(
                        "id", p.getId(),
                        "capturedAt", p.getCapturedAt(),
                        "imageBase64", p.getImageBase64()
                ))
                .toList();
    }

    @Operation(summary = "두 시점 사진 비교 조회", description = "지정한 두 날짜의 사진과 지표 차이를 반환")
    @GetMapping("/compare")
    public Map<String, Object> comparePhotos(
            @RequestParam @Parameter(description = "사용자 UUID") String userId,
            @RequestParam @Parameter(description = "첫 번째 날짜 (YYYY-MM-DD)") String date1,
            @RequestParam @Parameter(description = "두 번째 날짜 (YYYY-MM-DD)") String date2
    ) {
        LocalDate d1, d2;
        try {
            d1 = LocalDate.parse(date1);
            d2 = LocalDate.parse(date2);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "날짜 형식이 올바르지 않습니다 (YYYY-MM-DD)");
        }

        List<StoredPhoto> photos1 = storedPhotoRepository.findByUserIdAndCapturedAtOrderByCreatedAtDesc(userId, d1);
        List<StoredPhoto> photos2 = storedPhotoRepository.findByUserIdAndCapturedAtOrderByCreatedAtDesc(userId, d2);

        // 4개 지표 각각 두 날짜 값 차이 계산
        String[] indicators = {"jaw_angle_deg", "face_width_ratio", "eyelid_height_ratio", "mouth_corner_angle_deg"};
        Map<String, Object> indicatorDiffs = new HashMap<>();

        for (String indicator : indicators) {
            try {
                Map curveResponse = aiServerWebClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/api/v1/indicator/curve")
                                .queryParam("user_id", userId)
                                .queryParam("indicator", indicator)
                                .build())
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block();

                if (curveResponse == null) continue;

                List<Map<String, Object>> points = (List<Map<String, Object>>) curveResponse.get("points");
                if (points == null) continue;

                Double value1 = findValueByDate(points, date1);
                Double value2 = findValueByDate(points, date2);

                if (value1 != null && value2 != null) {
                    double diff = value2 - value1;
                    indicatorDiffs.put(indicator, diff);
                }
            } catch (Exception e) {

            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("date1Photo", photos1.isEmpty() ? null : photos1.get(0).getImageBase64());
        result.put("date2Photo", photos2.isEmpty() ? null : photos2.get(0).getImageBase64());
        result.put("indicatorDiffs", indicatorDiffs);
        return result;
    }

    private Double findValueByDate(List<Map<String, Object>> points, String date) {
        return points.stream()
                .filter(p -> date.equals(p.get("captured_at")))
                .map(p -> ((Number) p.get("value")).doubleValue())
                .findFirst()
                .orElse(null);
    }
}