package com.shopsystem.backend.controller;

import com.shopsystem.backend.dto.BusinessDayExceptionRequest;
import com.shopsystem.backend.dto.BusinessDayExceptionResponse;
import com.shopsystem.backend.dto.BusinessDaysResponse;
import com.shopsystem.backend.dto.WeeklyBusinessDayItem;
import com.shopsystem.backend.service.BusinessDayService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 営業日・臨時休業の管理（FR-B07。04_architecture.md §6.3）。 */
@RestController
@RequestMapping("/api/v1/stores/{storeId}/business-days")
@RequiredArgsConstructor
public class BusinessDayController {

    private final BusinessDayService businessDayService;

    @GetMapping
    public BusinessDaysResponse get(@PathVariable Long storeId) {
        return businessDayService.get(storeId);
    }

    @PutMapping("/weekly")
    public List<WeeklyBusinessDayItem> replaceWeekly(
            @PathVariable Long storeId, @RequestBody List<WeeklyBusinessDayItem> body) {
        return businessDayService.replaceWeekly(storeId, body);
    }

    @PostMapping("/exceptions")
    @ResponseStatus(HttpStatus.CREATED)
    public BusinessDayExceptionResponse addException(
            @PathVariable Long storeId, @RequestBody BusinessDayExceptionRequest body) {
        return businessDayService.addException(storeId, body);
    }

    @DeleteMapping("/exceptions/{exceptionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteException(@PathVariable Long storeId, @PathVariable Long exceptionId) {
        businessDayService.deleteException(storeId, exceptionId);
    }
}
