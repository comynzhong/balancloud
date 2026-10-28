package com.balancloud.staff.vo;

import lombok.Data;

import java.util.List;

@Data
public class StaffProfileVo {

    private String username;
    private String nickName;
    private List<String> roleKeys;
    /** true 表示按场站授权，false 表示超级管理员（全场站可见）。 */
    private boolean stationLimited;
    /** stationLimited=false 时为 null，前端显示「全部场站」。 */
    private Integer stationCount;
    private boolean canStop;
    private boolean canUnlock;
    private boolean canAckAlert;
    private boolean canResolveAlert;
}
