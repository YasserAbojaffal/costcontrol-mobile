# واجهة JavaScript بين صفحة البرنامج والتطبيق
-keepclassmembers class com.islandeden.costcontrol.AppBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface
# مكتبة التشفير (security-crypto / Tink): تعليقات توضيحية غير مطلوبة وقت التشغيل
-dontwarn javax.annotation.**
-dontwarn javax.annotation.concurrent.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**
-keep class com.google.crypto.tink.** { *; }
