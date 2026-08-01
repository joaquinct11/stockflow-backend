package com.stockflow.dto.superadmin;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SuperAdminTokenDTO {
    private String token;
    private String username;
}
