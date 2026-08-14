package com.shopsystem.backend.controller;

import com.shopsystem.backend.dto.ErrorItem;
import com.shopsystem.backend.dto.ErrorResponse;
import com.shopsystem.backend.entity.User;
import com.shopsystem.backend.exception.BusinessException;
import com.shopsystem.backend.service.UserService;

import lombok.RequiredArgsConstructor;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final MessageSource messageSource;

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody User user) {
        try {
            userService.registerUser(user);

            String successMsg = messageSource.getMessage(
                "user.register.success",
                null,
                LocaleContextHolder.getLocale()
            );
            return ResponseEntity.ok(Collections.singletonMap("message", successMsg));

        } catch (BusinessException e) {
            // 蓄積された複数エラーリストをそのままレスポンスへセット
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(e.getErrorItems()));

        } catch (Exception e) {
            String systemErrorMsg = messageSource.getMessage(
                "user.register.error.system",
                null,
                LocaleContextHolder.getLocale()
            );
            ErrorResponse error = new ErrorResponse(List.of(new ErrorItem(systemErrorMsg, Collections.emptyList())));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}