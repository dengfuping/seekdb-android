# App Inspection / Database Inspector (fork of sqlite-android-inspection for SeekDB types)
-keep class androidx.sqlite.inspection.** { *; }
-keep class androidx.inspection.** { *; }
-keepclassmembers class com.oceanbase.seekdb.android.compat.SeekdbCompatDatabase {
    public *** query(...);
    void endTransaction();
    protected void onAllReferencesReleased();
}
-keep class com.oceanbase.seekdb.android.compat.SeekdbCompatStatement { *; }
-keep class com.oceanbase.seekdb.android.compat.SeekdbInspectionBridge { *; }
-keep class android.database.sqlite.SQLiteClosable { *; }
