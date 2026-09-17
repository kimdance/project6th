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

    /**
     * メニュー写真を保存し、{@code /uploads/**} 配下の公開URLパスを返す。
     * 申告された {@code Content-Type} だけでなく、ファイル先頭のマジックバイトも検証する
     * （{@code Content-Type} はクライアントが自由に詐称できるヘッダのため、それだけを信用すると
     * 拡張子と実体が食い違ったファイルを認証不要のURLで公開してしまう）。
     */
    public String storeMenuItemPhoto(String companyCode, Long storeId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalid("menu.error.photo.required");
        }
        String contentType = file.getContentType();
        String extension = contentType == null ? null : ALLOWED_IMAGE_TYPES.get(contentType);
        if (extension == null) {
            throw invalid("menu.error.photo.invalid-type");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("写真の読み込みに失敗しました。", e);
        }
        if (!hasValidSignature(bytes, extension)) {
            throw invalid("menu.error.photo.invalid-type");
        }

        String filename = UUID.randomUUID() + "." + extension;
        Path targetDir = Path.of(uploadDir, "menu-photos", companyCode, String.valueOf(storeId));
        Path target = targetDir.resolve(filename);
        try {
            Files.createDirectories(targetDir);
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("写真の保存に失敗しました。", e);
        }

        return "/uploads/menu-photos/" + companyCode + "/" + storeId + "/" + filename;
    }

    private static boolean hasValidSignature(byte[] b, String extension) {
        return switch (extension) {
            case "jpg" -> b.length >= 3
                    && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
            case "png" -> b.length >= 8
                    && (b[0] & 0xFF) == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47
                    && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A;
            case "webp" -> b.length >= 12
                    && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                    && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
            default -> false;
        };
    }

    private BusinessException invalid(String code) {
        String message = messageSource.getMessage(code, null, LocaleContextHolder.getLocale());
        return new BusinessException(List.of(new ErrorItem(message, List.of("file"))));
    }
}
