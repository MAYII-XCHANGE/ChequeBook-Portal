package com.example.chequebookrequestportal.domain;

public enum DebitStatus {
    NOT_INITIATED,
    PENDING,
    SUCCESSFUL,
    FAILED,
    UNKNOWN,
    REVERSAL_PENDING,
    REVERSED,
    REVERSAL_FAILED,
    REVERSAL_UNKNOWN
}
