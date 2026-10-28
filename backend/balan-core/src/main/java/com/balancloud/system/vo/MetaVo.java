package com.balancloud.system.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MetaVo {

    private String title;

    private String icon;

    private boolean noCache;

    private String link;
}
