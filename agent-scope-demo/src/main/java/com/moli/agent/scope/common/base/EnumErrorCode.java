package com.moli.agent.scope.common.base;

import lombok.Getter;

public enum EnumErrorCode {

    OK(200, "成功"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FAIL(500, "失败");

    @Getter
    public int code;

    @Getter
    private String desc;

    EnumErrorCode(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }


    public String getDescByCode(int code) {
        for (EnumErrorCode value : EnumErrorCode.values()) {
            if(value.code == code) {
                return value.getDesc();
            }
        }
        return null;
    }

    public EnumErrorCode getEnumErrorCode(int code) {
        for (EnumErrorCode value : EnumErrorCode.values()) {
            if(value.code == code) {
                return value;
            }
        }
        return null;
    }

}
