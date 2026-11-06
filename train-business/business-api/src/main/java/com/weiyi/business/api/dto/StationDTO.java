package com.weiyi.business.api.dto;

import lombok.Data;

import java.io.Serializable;

/** 车站 */
@Data
public class StationDTO implements Serializable {

    private Long id;
    private String code;
    private String name;
    private String pinyin;
}
