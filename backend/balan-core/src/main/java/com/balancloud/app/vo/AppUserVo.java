package com.balancloud.app.vo;

import com.balancloud.app.domain.AppUser;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AppUserVo {

    private Long id;
    private String nickname;
    private String phone;
    private String avatar;
    private LocalDateTime lastLoginAt;

    public static AppUserVo of(AppUser user) {
        AppUserVo vo = new AppUserVo();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setPhone(user.getPhone());
        vo.setAvatar(user.getAvatar());
        vo.setLastLoginAt(user.getLastLoginAt());
        return vo;
    }
}
