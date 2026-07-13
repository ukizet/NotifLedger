{
  inputs,
  pkgs,
  config,
  lib,
  ...
}: {
  # Android SDK & tools
  android = {
    enable = true;
    platforms.version = ["34"]; # Match your compileSdk/targetSdk
    buildTools.version = ["34.0.0" "33.0.1"]; # Match your buildToolsVersion
    platformTools.version = "36.0.0"; # adb, fastboot, etc.
    emulator.enable = false; # Keep disabled unless you need emulators
  };

  # Java & Kotlin toolchains
  languages.java = {
    enable = true;
    jdk.package = pkgs.jdk17; # ← The full OpenJDK 17 you asked about
  };
  languages.kotlin.enable = true;

  packages = with pkgs; [
    git
    gradle # Only needed once to generate ./gradlew
  ];

  # 2. Fix: Use builtins.getEnv "HOME" instead of config.home
  # 3. Use lib.mkForce to override devenv's default Nix store path
  # env.ANDROID_SDK_ROOT = lib.mkForce "${builtins.getEnv "HOME"}/.android/sdk";
  # env.ANDROID_HOME = lib.mkForce "${builtins.getEnv "HOME"}/.android/sdk";

  # Update scripts for Kotlin
  scripts.build.exec = "./gradlew assembleDebug";
  scripts.install.exec = "./gradlew installDebug";
  scripts.clear.exec = ": > local.properties";

  enterShell = ''
    export NIXPKGS_ALLOW_UNFREE=1
    export PATH="$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/build-tools/34.0.0:$PATH"


  '';
}
