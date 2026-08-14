package com.shopsystem.backend.exception;

import com.shopsystem.backend.dto.ErrorItem;
import lombok.Getter;

import java.util.List;

@Getter
public class BusinessException extends RuntimeException {
    private final List<ErrorItem> errorItems;

    public BusinessException(List<ErrorItem> errorItems) {
        super("業務エラーが発生しました。");
        this.errorItems = errorItems;
    }
}