package com.oceanbase.seekdb.android.core;

public class SeekdbException extends RuntimeException {
    private final SeekdbError error;

    public SeekdbException(SeekdbError error) {
        super(error == null ? "Unknown SeekDB error" : error.message());
        this.error = error;
    }

    public SeekdbError error() {
        return error;
    }
}
