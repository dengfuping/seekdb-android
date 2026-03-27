package com.oceanbase.seekdb.android.nativeapi;

import com.oceanbase.seekdb.android.core.SeekdbNativeBridge;

public final class SeekdbClient {
    private SeekdbClient() {
    }

    public static boolean isNativeAvailable() {
        return SeekdbNativeBridge.nativeIsAvailable();
    }

    public static int abiVersion() {
        return SeekdbNativeBridge.nativeAbiVersion();
    }

    public static int open(String dbDir, int port) {
        ensureNativeAvailable();
        return SeekdbNativeBridge.nativeOpen(dbDir, port);
    }

    public static void close() {
        SeekdbNativeBridge.nativeClose();
    }

    public static SeekdbConnection connect(String database, boolean autocommit) {
        ensureNativeAvailable();
        long ptr = SeekdbNativeBridge.nativeConnect(database, autocommit);
        if (ptr == 0L) {
            throw new IllegalStateException("seekdb_connect returned null connection");
        }
        return new SeekdbConnection(ptr);
    }

    private static void ensureNativeAvailable() {
        if (!isNativeAvailable()) {
            throw new UnsupportedOperationException("libseekdb.so is not available");
        }
    }
}
