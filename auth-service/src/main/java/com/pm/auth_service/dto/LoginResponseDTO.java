package com.pm.auth_service.dto;

public class LoginResponseDTO {

    private final String accessToken;

    public String getAccessToken() {
        return accessToken;
    }

    public LoginResponseDTO(String accessToken) {
        this.accessToken = accessToken;
    }

}
