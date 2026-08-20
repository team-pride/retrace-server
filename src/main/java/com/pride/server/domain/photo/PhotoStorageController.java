package com.pride.server.domain.photo;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;

@Tag(name = "PhotoStorage", description = "과거 사진 저장/조회 API (되감기, 두 시점 비교용)")
@RestController
@RequestMapping("/photos")
@RequiredArgsConstructor
public class PhotoStorageController {

    private final StoredPhotoRepository storedPhotoRepository;
    private final WebClient aiServerWebClient;

    @Operation(summary = "사진 저장", description = "지표 추출과 별개로, 되감기/비교용으로 사진을 서버에 저장")
    @PostMapping(value = "/store", consumes = "multipart/form-data")
    public String storePhotos(
            @RequestParam @Parameter(description = "사용자 UUID") String userId,
            @RequestParam("files") List<MultipartFile> files
    ) throws Exception {
        int savedCount = 0;
        List<String> skipped = new ArrayList<>();   // ① 여기서 선언

        for (MultipartFile file : files) {
            byte[] bytes = file.getBytes();
            LocalDate capturedAt = extractCapturedDate(bytes);

            if (capturedAt == null) {
                skipped.add(file.getOriginalFilename());   // ② continue 직전에 기록
                continue;
            }

            StoredPhoto photo = new StoredPhoto();
            photo.setUserId(userId);
            photo.setCapturedAt(capturedAt);
            photo.setImageBase64(Base64.getEncoder().encodeToString(bytes));

            storedPhotoRepository.save(photo);
            savedCount++;
        }

        return savedCount + "장 저장 완료"
                + (skipped.isEmpty() ? "" : " (촬영일 없어 스킵: " + skipped + ")");   // ③ 리턴문 교체
    }

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
                indicatorDiffs.put(indicator, String.format("%+.1f", diff));
            }
            } catch (Exception e) {
                // 이 지표 하나 실패해도 나머지 지표/사진은 계속 진행
                // 이 지표 수치 추출 중 에러 발생: 하나 실패해도 나머지 지표/사진은 계속 진행
                indicatorDiffs.put(indicator, "계산 실패");
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

    private LocalDate extractCapturedDate(byte[] imageBytes) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(
                    new java.io.ByteArrayInputStream(imageBytes));
            ExifSubIFDDirectory directory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);

            if (directory != null && directory.getDateOriginal() != null) {
                return directory.getDateOriginal().toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();
            } else {
                System.out.println("EXIF 없음 or DateOriginal 없음"); // 임시 로그
            }
        } catch (Exception e) {
            System.out.println("EXIF 파싱 실패: " + e.getMessage()); // 임시 로그
        }
        return null;
    }
}