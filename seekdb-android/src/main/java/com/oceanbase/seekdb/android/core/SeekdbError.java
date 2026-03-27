package com.oceanbase.seekdb.android.core;

public final class SeekdbError {
    private final int code;
    private final String message;
    private final String sqlState;

    public SeekdbError(int code, String message, String sqlState) {
        this.code = code;
        this.message = message;
        this.sqlState = sqlState;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }

    public String sqlState() {
        return sqlState;
    }
}
