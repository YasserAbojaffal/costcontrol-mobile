# واجهة JavaScript بين صفحة البرنامج والتطبيق
-keepclassmembers class com.islandeden.costcontrol.AppBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface
