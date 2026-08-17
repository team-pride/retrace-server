package com.pride.server.domain.photo;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.pride.server.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PhotoService {

    private final PhotoRepository photoRepository;
    private static final String UPLOAD_DIR = System.getProperty("user.dir") + "/uploads/";

    public Photo save(User user, MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !(contentType.equals("image/jpeg") || contentType.equals("image/png"))) {
            Photo photo = new Photo();
            photo.setUser(user);
            photo.setGrade(PhotoGrade.EXCLUDED);
            photo.setReason("지원하지 않는 파일 형식: " + contentType);
            return photoRepository.save(photo);
        }
        try {
            String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
            Path savePath = Path.of(UPLOAD_DIR + fileName);
            Files.createDirectories(savePath.getParent());
            file.transferTo(savePath.toFile());

            LocalDate takenAt = extractTakenDate(savePath.toFile());

            Photo photo = new Photo();
            photo.setUser(user);
            photo.setFilePath(savePath.toString());
            photo.setTakenAt(takenAt);

            if (takenAt == null) {
                photo.setGrade(PhotoGrade.EXCLUDED);
                photo.setReason("촬영일 정보를 찾을 수 없음");
            }

            return photoRepository.save(photo);

        } catch (IOException e) {
            throw new RuntimeException("사진 저장 실패: " + file.getOriginalFilename(), e);
        }
    }

    private LocalDate extractTakenDate(File file) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(file);
            ExifSubIFDDirectory directory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);

            if (directory != null && directory.getDateOriginal() != null) {
                return directory.getDateOriginal().toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();
            }
        } catch (Exception e) {
            // 촬영일 못 뽑으면 null
        }
        return null;
    }
}