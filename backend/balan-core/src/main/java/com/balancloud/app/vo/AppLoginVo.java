package com.balancloud.app.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AppLoginVo {

    private String token;
    private AppUserVo user;
}
