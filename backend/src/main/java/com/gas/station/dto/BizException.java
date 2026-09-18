package com.gas.station.dto;

public class BizException extends RuntimeException {
    public BizException(String message) {
        super(message);
    }
}
