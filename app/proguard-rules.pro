# Navigation type-safe routes are @Serializable objects/classes.
-keepclassmembers @kotlinx.serialization.Serializable class com.buildit.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
