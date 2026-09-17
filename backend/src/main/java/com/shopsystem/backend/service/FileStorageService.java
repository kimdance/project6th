package com.shopsystem.backend.service;

import com.shopsystem.backend.dto.ErrorItem;
import com.shopsystem.backend.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * メニュー写真等のアップロードファイルの保存（FR-D01。04_architecture.md §6.5のレシートPDF保存と
 * 同じ方針）。本番のオブジェクトストレージは本番ホスティング確定後に選定し、フェーズ1開発中は
 * ローカルディスク（{@code app.upload.dir}）へ保存する。保存先は {@code /uploads/**} として
 * {@link com.shopsystem.backend.web.WebConfig} で静的公開する（注文画面等での表示に認証を求めないため）。
 */
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private static final Map<String, String> ALLOWED_IMAGE_TYPES =
            Map.of("image/jpeg", "jpg", "image/png", "png", "image/webp", "webp");

    private final MessageSource messageSource;

    @Value("${app.upload.dir}")
    private String uploadDir;

    /** メニュー写真を保存し、{@code /uploads/**} 配下の公開URLパスを返す。 */
    public String storeMenuItemPhoto(String companyCode, Long storeId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalid("menu.error.photo.required");
        }
        String extension = ALLOWED_IMAGE_TYPES.get(file.getContentType());
        if (extension == null) {
            throw invalid("menu.error.photo.invalid-type");
        }

        String filename = UUID.randomUUID() + "." + extension;
        Path targetDir = Path.of(uploadDir, "menu-photos", companyCode, String.valueOf(storeId));
        Path target = targetDir.resolve(filename);
        try {
            Files.createDirectories(targetDir);
            file.transferTo(target);
        } catch (IOException e) {
            throw new UncheckedIOException("写真の保存に失敗しました。", e);
        }

        return "/uploads/menu-photos/" + companyCode + "/" + storeId + "/" + filename;
    }

    private BusinessException invalid(String code) {
        String message = messageSource.getMessage(code, null, LocaleContextHolder.getLocale());
        return new BusinessException(List.of(new ErrorItem(message, List.of("file"))));
    }
}
