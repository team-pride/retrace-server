package com.pride.server.domain.photo;

import com.pride.server.domain.user.User;
import com.pride.server.domain.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Tag(name = "Photo", description = "사진 업로드 및 분석 관련 API")
@RestController
@RequestMapping("/photos")
@RequiredArgsConstructor
public class PhotoController {

    private final PhotoService photoService;
    private final UserRepository userRepository;

    private static final int MIN_PER_YEAR = 5;
    private static final int MAX_PER_YEAR = 30;

    @Operation(
            summary = "사진 업로드",
            description = "사용자 사진을 여러 장 업로드합니다. EXIF 촬영일을 추출하고, "
                    + "촬영일이 없거나 지원하지 않는 형식이면 EXCLUDED로 처리됩니다. "
                    + "한 번에 최대 150장, 연도별 최소 5장 권장됩니다."
    )
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public UploadResult upload(
            @RequestParam("userId")
            @Parameter(description = "사진을 업로드할 사용자의 UUID (POST /users로 발급받은 값)")
            UUID userId,

            @RequestParam("files")
            @Parameter(description = "업로드할 사진 파일들. 한 번에 최대 150장까지 가능")
            List<MultipartFile> files
    ) {
        if (files.size() > 150) {
            throw new RuntimeException("한 번에 최대 150장까지 업로드 가능합니다. 나머지는 나눠서 업로드해주세요.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없음"));

        List<Photo> savedPhotos = files.stream()
                .map(file -> photoService.save(user, file))
                .toList();

        Map<Integer, Long> countByYear = savedPhotos.stream()
                .filter(p -> p.getTakenAt() != null)
                .collect(Collectors.groupingBy(
                        p -> p.getTakenAt().getYear(),
                        Collectors.counting()
                ));

        List<String> exceededYears = countByYear.entrySet().stream()
                .filter(e -> e.getValue() > MAX_PER_YEAR)
                .map(e -> e.getKey() + "년 (" + e.getValue() + "장, 최대 " + MAX_PER_YEAR + "장)")
                .toList();

        List<String> insufficientYears = countByYear.entrySet().stream()
                .filter(e -> e.getValue() < MIN_PER_YEAR)
                .map(e -> e.getKey() + "년 (" + e.getValue() + "장, 최소 " + MIN_PER_YEAR + "장 필요)")
                .toList();

        return new UploadResult(savedPhotos, countByYear, exceededYears, insufficientYears);
    }

    public record UploadResult(
            List<Photo> photos,
            Map<Integer, Long> countByYear,
            List<String> exceededYears,
            List<String> insufficientYears
    ) {}
}