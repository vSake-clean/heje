# Minifikacja jest wyłączona (isMinifyEnabled = false). Reguły na wypadek jej włączenia:
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class pl.rytm.app.**$$serializer { *; }
-keepclassmembers class pl.rytm.app.** {
    *** Companion;
}
