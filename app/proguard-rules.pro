# ProGuard rules for Kresty app

# Keep model classes
-keep class com.kresty.isolation.model.** { *; }

# Keep activities
-keep class com.kresty.isolation.activities.** { *; }

# Keep receivers
-keep class com.kresty.isolation.receivers.** { *; }

# Keep billing classes
-keep class com.kresty.isolation.billing.** { *; }

# Google Play Billing
-keep class com.android.billingclient.** { *; }
-dontwarn com.android.billingclient.**

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep Device Admin Receiver
-keep class android.app.admin.DeviceAdminReceiver { *; }
-keep class android.app.admin.DevicePolicyManager { *; }

# General Android
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod
