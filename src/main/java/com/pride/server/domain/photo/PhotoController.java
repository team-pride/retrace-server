package com.pride.server.domain.photo;

import com.pride.server.domain.user.User;
import com.pride.server.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/photos")
@RequiredArgsConstructor
public class PhotoController {

    private final PhotoService photoService;
    private final UserRepository userRepository;

    private static final int MIN_PER_YEAR = 5;
    private static final int MAX_PER_YEAR = 30;

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public UploadResult upload(
            @RequestParam("userId") UUID userId,
            @RequestParam("files") List<MultipartFile> files
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