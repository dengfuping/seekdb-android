package com.oceanbase.seekdb.android.compat;

import com.oceanbase.seekdb.android.core.SeekdbNativeBridge;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Lets Database Inspector discover open {@link SeekdbCompatDatabase} instances without relying on
 * JVMTI heap enumeration ({@code ArtTooling.findInstances}), which can return empty when hidden-API
 * restrictions block {@code VMDebug} on newer Android releases.
 *
 * <p>Registration is mirrored into JNI global refs ({@link SeekdbNativeBridge#nativeInspectionRegisterOpenDatabase})
 * so the inspector and the app agree on the same instances even if Java static fields were loaded
 * twice (instrumentation / multiple ClassLoaders).
 */
public final class SeekdbInspectionBridge {
    private static final Set<SeekdbCompatDatabase> OPEN =
            Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

    private SeekdbInspectionBridge() {}

    /** Called when a connection is bound (after {@link SeekdbCompatDatabase#bindAbsoluteDatabasePath}). */
    public static void registerOpenDatabase(SeekdbCompatDatabase database) {
        if (database == null) {
            return;
        }
        OPEN.add(database);
        try {
            SeekdbNativeBridge.nativeInspectionRegisterOpenDatabase(database);
        } catch (UnsatisfiedLinkError ignored) {
            // Host JVM unit tests: no libseekdb_android_jni.
        }
    }

    public static void unregisterOpenDatabase(SeekdbCompatDatabase database) {
        if (database == null) {
            return;
        }
        OPEN.remove(database);
        try {
            SeekdbNativeBridge.nativeInspectionUnregisterOpenDatabase(database);
        } catch (UnsatisfiedLinkError ignored) {
        }
    }

    /** Snapshot for {@code androidx.sqlite.inspection.SqliteInspector} when tracking databases. */
    public static List<SeekdbCompatDatabase> snapshotOpenDatabases() {
        try {
            Object[] fromNative = SeekdbNativeBridge.nativeInspectionSnapshotOpenDatabases();
            if (fromNative != null) {
                ArrayList<SeekdbCompatDatabase> out = new ArrayList<>(fromNative.length);
                for (Object o : fromNative) {
                    if (o instanceof SeekdbCompatDatabase) {
                        out.add((SeekdbCompatDatabase) o);
                    }
                }
                if (!out.isEmpty()) {
                    return out;
                }
            }
        } catch (UnsatisfiedLinkError ignored) {
        }
        synchronized (OPEN) {
            return new ArrayList<>(OPEN);
        }
    }
}
