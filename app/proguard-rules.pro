# POI 相关类在混淆时可能被误删，这里保守保留
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-dontwarn org.apache.**
-dontwarn com.fasterxml.**
-dontwarn javax.**
