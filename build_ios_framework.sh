#!/bin/bash

# iOS Framework Build Script
# This script builds the shared module as an iOS framework

echo "Building iOS Framework..."

# Clean previous builds
rm -rf shared/build/xcode-frameworks

# Create build directory
mkdir -p shared/build/xcode-frameworks

# Build for all iOS architectures
echo "Building for iOS Simulator (x64)..."
./gradlew :shared:compileKotlinIosX64

echo "Building for iOS Simulator (arm64)..."
./gradlew :shared:compileKotlinIosSimulatorArm64

echo "Building for iOS Device (arm64)..."
./gradlew :shared:compileKotlinIosArm64

# Create universal framework
echo "Creating universal framework..."

# Framework directories
FRAMEWORK_DIR="shared/build/xcode-frameworks/Shared.framework"
mkdir -p "$FRAMEWORK_DIR/Headers"

# Copy simulator binaries
cp -R shared/build/kotlin-iosX64/kotlin/shared/shared.klib "$FRAMEWORK_DIR/"
cp -R shared/build/kotlin-iosSimulatorArm64/kotlin/shared/shared.klib "$FRAMEWORK_DIR/"

# Copy device binary
cp shared/build/kotlin-iosArm64/kotlin/shared/shared.klib "$FRAMEWORK_DIR/shared_arm64.klib"

# Create Info.plist
cat > "$FRAMEWORK_DIR/Info.plist" << EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleDevelopmentRegion</key>
    <string>en</string>
    <key>CFBundleExecutable</key>
    <string>Shared</string>
    <key>CFBundleIdentifier</key>
    <string>com.icpeek.shared</string>
    <key>CFBundleInfoDictionaryVersion</key>
    <string>6.0</string>
    <key>CFBundleName</key>
    <string>Shared</string>
    <key>CFBundlePackageType</key>
    <string>FMWK</string>
    <key>CFBundleShortVersionString</key>
    <string>1.0</string>
    <key>CFBundleVersion</key>
    <string>1</string>
    <key>MinimumOSVersion</key>
    <string>16.0</string>
    <key>CFBundleSupportedPlatforms</key>
    <array>
        <string>iPhoneOS</string>
        <string>iPhoneSimulator</string>
    </array>
</dict>
</plist>
EOF

echo "iOS Framework build completed!"
echo "Framework location: shared/build/xcode-frameworks/Shared.framework"
