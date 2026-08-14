package com.shopsystem.backend.dto;

import lombok.Data;

@Data
public class UserRegisterRequest {
    private String companyCode;
    private String name;
    private String email;
    private String password;
    private String telnumber;
}