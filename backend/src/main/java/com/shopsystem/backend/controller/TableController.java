package com.shopsystem.backend.controller;

import com.shopsystem.backend.dto.TableRequest;
import com.shopsystem.backend.dto.TableResponse;
import com.shopsystem.backend.service.TableService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 卓（テーブル）マスタの管理（FR-B02。04_architecture.md §6.3）。 */
@RestController
@RequestMapping("/api/v1/stores/{storeId}/tables")
@RequiredArgsConstructor
public class TableController {

    private final TableService tableService;

    @GetMapping
    public List<TableResponse> list(@PathVariable Long storeId) {
        return tableService.list(storeId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TableResponse create(@PathVariable Long storeId, @RequestBody TableRequest body) {
        return tableService.create(storeId, body);
    }

    @PutMapping("/{tableId}")
    public TableResponse update(
            @PathVariable Long storeId, @PathVariable Long tableId, @RequestBody TableRequest body) {
        return tableService.update(storeId, tableId, body);
    }
}
