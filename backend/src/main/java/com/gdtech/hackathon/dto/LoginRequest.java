package com.gdtech.hackathon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 登录请求
 */
@Data
public class LoginRequest {

    /**
     * 账号（4位数字）
     */
    @NotBlank(message = "账号不能为空")
    @Pattern(regexp = "^[0-9]{4}$", message = "账号必须是4位数字")
    private String username;

    /**
     * 密码（6位数字+小写字母）
     */
    @NotBlank(message = "密码不能为空")
    @Pattern(regexp = "^[0-9a-z]{6}$", message = "密码必须是6位数字和小写字母")
    private String password;
}
