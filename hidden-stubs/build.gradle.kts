// Compile-time stand-ins for platform classes that only exist on the device.
plugins { `java-library` }
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
dependencies {
    compileOnly(files("${System.getProperty("user.home")}/Library/Android/sdk/platforms/android-34/android.jar"))
}
