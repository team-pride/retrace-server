package com.pride.server.domain.photo;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 사진을 되감기/비교용으로 서버(RDS)에 저장하는 공통 로직.
 * IndicatorController(extract-batch)에서 지표 추출과 함께 호출된다.
 */
@Service
@RequiredArgsConstructor
public class PhotoStorageService {

    private final StoredPhotoRepository storedPhotoRepository;

    /**
     * 사진 여러 장을 저장한다. EXIF 촬영일이 없는 파일은 스킵한다.
     *
     * @return 저장 결과 요약 (저장 개수, 스킵된 파일명 목록)
     */
    public StoreResult storePhotos(String userId, List<MultipartFile> files) throws Exception {
        int savedCount = 0;
        List<String> skipped = new ArrayList<>();

        for (MultipartFile file : files) {
            byte[] bytes = file.getBytes();
            LocalDate capturedAt = extractCapturedDate(bytes);

            if (capturedAt == null) {
                skipped.add(file.getOriginalFilename());
                continue;
            }

            StoredPhoto photo = new StoredPhoto();
            photo.setUserId(userId);
            photo.setCapturedAt(capturedAt);
            photo.setImageBase64(Base64.getEncoder().encodeToString(bytes));

            storedPhotoRepository.save(photo);
            savedCount++;
        }

        return new StoreResult(savedCount, skipped);
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
            }
        } catch (Exception e) {
            // 촬영일 못 뽑으면 null (스킵 처리)
        }
        return null;
    }

    public record StoreResult(int savedCount, List<String> skipped) {
    }
}