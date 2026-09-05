# build-mnn-android.ps1
#
# One-time setup script for the on-device AI recognition feature.
# Builds the MNN runtime (vision-enabled, SEP_BUILD=ON) for Android arm64
# and installs:
#   - shared libs  -> app/src/main/cpp/mnn/prebuilt/arm64-v8a/
#                     (libMNN.so, libMNN_Express.so, libllm.so, libMNNOpenCV.so)
#   - C++ headers  -> app/src/main/cpp/mnn/include/
#
# After this script succeeds, rebuild the app normally; CMake links the real
# mnn_bridge instead of the stub.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File scripts/build-mnn-android.ps1 `
#       [-NdkPath "C:\Android\ndk\30.0.15729638"] [-MnnRef "master"] [-Clean]
#
# Notes:
#   - MnnRef: any MNN git ref with Qwen3-VL support (>= 2025-10-16).
#     Pin a tag for reproducible builds; "master" tracks the newest fixes.
#   - Toolchain isolation: CMake and Ninja are resolved from the Android SDK
#     bundle (cmake.dir in local.properties, or %LOCALAPPDATA%\Android\Sdk\
#     cmake\<ver>\bin). The system PATH is never consulted, so you do NOT need
#     to install cmake/ninja globally. Only git + the Android NDK are required.

param(
    [string]$NdkPath = "",
    [string]$MnnRef = "master",
    [switch]$Clean
)

$ErrorActionPreference = "Stop"

# ---------------------------------------------------------------------------
# Locate the NDK
# ---------------------------------------------------------------------------
if (-not $NdkPath) {
    $candidates = @()
    if ($env:ANDROID_NDK_HOME) { $candidates += $env:ANDROID_NDK_HOME }

    # Collect every SDK root we might know about.
    $sdkRoots = @()
    if ($env:ANDROID_HOME)  { $sdkRoots += $env:ANDROID_HOME }
    if ($env:ANDROID_SDK_ROOT) { $sdkRoots += $env:ANDROID_SDK_ROOT }
    # Default location used by Android Studio on Windows.
    $defaultSdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
    if ((Test-Path $defaultSdk)) { $sdkRoots += $defaultSdk }

    # Read sdk.dir from local.properties (if present). Java properties escape
    # both ':' and '\' with a leading backslash (e.g. "C\:\\Users\\..."), so a
    # single regex pass turns "\X" back into "X" for every escaped char.
    $localProps = Join-Path $PSScriptRoot "..\local.properties"
    if (Test-Path $localProps) {
        $rawLine = Get-Content $localProps | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
        if ($rawLine) {
            $sdkDir = $rawLine -replace '^sdk\.dir=', '' -replace '\\(.)', '$1'
            if ($sdkDir -and (Test-Path $sdkDir)) { $sdkRoots += $sdkDir }
        }
    }

    foreach ($root in $sdkRoots) {
        $ndkRoot = Join-Path $root "ndk"
        if (Test-Path $ndkRoot) {
            # PSIsContainer works on every PowerShell version; -Directory is a
            # FileSystem-provider dynamic switch that some hosts reject.
            $candidates += Get-ChildItem $ndkRoot |
                Where-Object { $_.PSIsContainer } |
                Sort-Object Name -Descending |
                Select-Object -ExpandProperty FullName
        }
    }
    $NdkPath = $candidates | Where-Object { Test-Path "$_\toolchains\llvm\prebuilt" } | Select-Object -First 1
}

if (-not $NdkPath -or -not (Test-Path $NdkPath)) {
    throw "Android NDK not found. Pass -NdkPath explicitly."
}
Write-Host "Using NDK: $NdkPath"

# ---------------------------------------------------------------------------
# Locate CMake + Ninja (prefer the SDK-bundled copy so the system PATH is
# never touched — full isolation from any globally installed cmake/ninja).
# ---------------------------------------------------------------------------
function Resolve-PropertiesKey {
    param([string]$Path, [string]$Key)
    if (-not (Test-Path $Path)) { return $null }
    $line = Get-Content $Path | Where-Object { $_ -match "^$Key=" } | Select-Object -First 1
    if (-not $line) { return $null }
    # Java properties escape ':' and '\' with a leading backslash; unescape both.
    return ($line -replace "^$Key=", '' -replace '\\(.)', '$1')
}

$localProps = Join-Path $PSScriptRoot "..\local.properties"
$cmakeDir = Resolve-PropertiesKey $localProps 'cmake.dir'
if (-not $cmakeDir -or -not (Test-Path $cmakeDir)) {
    # Fall back to the SDK default layout.
    $cmakeDir = Join-Path $env:LOCALAPPDATA "Android\Sdk\cmake\4.1.2"
}
$cmakeExe = Join-Path $cmakeDir "bin\cmake.exe"
$ninjaExe = Join-Path $cmakeDir "bin\ninja.exe"

if (-not (Test-Path $cmakeExe)) {
    throw "CMake not found at '$cmakeExe'. Install it via Android Studio SDK Manager " +
          "(Tools > SDK Manager > SDK Tools > 'NDK (Side by side)' + 'CMake') or pass cmake.dir in local.properties."
}
if (-not (Test-Path $ninjaExe)) {
    throw "Ninja not bundled with CMake at '$cmakeDir\bin'. Reinstall the SDK CMake component " +
          "or drop a ninja.exe next to cmake.exe."
}
Write-Host "Using CMake: $cmakeExe"
Write-Host "Using Ninja: $ninjaExe"

# ---------------------------------------------------------------------------
# Paths
# ---------------------------------------------------------------------------
$repoRoot  = Resolve-Path (Join-Path $PSScriptRoot "..")
$buildRoot = Join-Path $repoRoot ".mnn-build"
$mnnSrc    = Join-Path $buildRoot "mnn"
$buildDir  = Join-Path $buildRoot "build-arm64"
$prebuilt  = Join-Path $repoRoot "app\src\main\cpp\mnn\prebuilt\arm64-v8a"
$include   = Join-Path $repoRoot "app\src\main\cpp\mnn\include"

if ($Clean -and (Test-Path $buildRoot)) { Remove-Item -Recurse -Force $buildRoot }
New-Item -ItemType Directory -Force -Path $buildRoot | Out-Null

# ---------------------------------------------------------------------------
# Fetch MNN sources
# ---------------------------------------------------------------------------
if (-not (Test-Path (Join-Path $mnnSrc "CMakeLists.txt"))) {
    Write-Host "Cloning MNN ($MnnRef)..."
    git clone --depth 1 --branch $MnnRef https://github.com/alibaba/MNN.git $mnnSrc
    if ($LASTEXITCODE -ne 0) { throw "git clone failed" }
} else {
    Write-Host "Reusing existing MNN checkout at $mnnSrc"
}

# ---------------------------------------------------------------------------
# Patch MNN CMakeLists for CMake 4.x compatibility
# ---------------------------------------------------------------------------
# CMake 4.x forbids add_custom_command(TARGET <OBJECT-lib> POST_BUILD ...).
# MNN's tools/cv and transformers/llm/engine both use this pattern to copy
# headers post-build, which breaks when MNN_SEP_BUILD=false (OBJECT libs).
# With MNN_SEP_BUILD=true (our default) the libs are SHARED and POST_BUILD
# is legal, so the patch is a no-op — but it stays as a safety net in case
# someone switches to SEP_BUILD=false.
#
# The patch is idempotent — re-running on already-patched files is safe.
# ---------------------------------------------------------------------------
Function Patch-CMake4Compat {
    param([string]$File, [string]$Marker)
    if (-not (Test-Path $File)) { return }
    $content = Get-Content $File -Raw
    # Only patch when the library is declared as OBJECT (SEP_BUILD=OFF).
    # If it is SHARED/STATIC, POST_BUILD is valid — leave it alone.
    if ($content -notmatch 'add_library\(' + [regex]::Escape($Marker) + '\s+OBJECT') {
        Write-Host "  Skipped: $Marker (not OBJECT lib, POST_BUILD is legal)"
        return
    }
    # Match: add_custom_command( TARGET <name> POST_BUILD ... ) — multiline.
    $pattern = "(?s)add_custom_command\s*\(\s*TARGET\s+\S+\s+POST_BUILD.*?\)"
    if ($content -match $pattern) {
        $patched = $content -replace $pattern, "# [Patched by build-mnn-android.ps1] Removed POST_BUILD on OBJECT lib (CMake 4.x compat)"
        Set-Content $File $patched -NoNewline
        Write-Host "  Patched: $Marker"
    }
}
Write-Host "Checking CMake 4.x compatibility patches..."
Patch-CMake4Compat (Join-Path $mnnSrc "tools\cv\CMakeLists.txt") "MNNOpenCV"
Patch-CMake4Compat (Join-Path $mnnSrc "transformers\llm\engine\CMakeLists.txt") "llm"

# ---------------------------------------------------------------------------
# Configure + build (flags mirror MNN's official Android LLM script,
# project/android/build_64.sh, with vision + imgcodecs enabled)
# ---------------------------------------------------------------------------
$toolchain = Join-Path $NdkPath "build\cmake\android.toolchain.cmake"

# -Wno-deprecated: MNN's top-level CMakeLists declares cmake_minimum_required
#   < 3.10, which triggers a deprecation warning on CMake 4.x. It is an
#   upstream issue we cannot fix, so silence it rather than drown the log.
# -DCMAKE_MAKE_PROGRAM: point CMake at the SDK-bundled ninja so the system
#   PATH is never consulted (full isolation).
& $cmakeExe -S $mnnSrc -B $buildDir -G Ninja -Wno-deprecated `
    -DCMAKE_TOOLCHAIN_FILE="$toolchain" `
    -DCMAKE_MAKE_PROGRAM="$ninjaExe" `
    -DCMAKE_BUILD_TYPE=Release `
    -DANDROID_ABI=arm64-v8a `
    -DANDROID_PLATFORM=android-24 `
    -DCMAKE_INSTALL_PREFIX="$buildDir\install" `
    -DMNN_LOW_MEMORY=true `
    -DMNN_CPU_WEIGHT_DEQUANT_GEMM=true `
    -DMNN_BUILD_LLM=true `
    -DMNN_SUPPORT_TRANSFORMER_FUSE=true `
    -DMNN_ARM82=true `
    -DMNN_OPENCL=true `
    -DLLM_SUPPORT_VISION=true `
    -DMNN_BUILD_OPENCV=true `
    -DMNN_IMGCODECS=true `
    -DMNN_SEP_BUILD=true `
    -DMNN_BUILD_SHARED_LIBS=true `
    -DMNN_USE_SYSTEM_LIBS=false
if ($LASTEXITCODE -ne 0) { throw "cmake configure failed" }

Write-Host "Building MNN (this can take 10-30 minutes)..."
& $cmakeExe --build $buildDir --target MNN MNN_Express llm MNNOpenCV MNN_CL -- -k 0
if ($LASTEXITCODE -ne 0) { throw "cmake build failed" }

# ---------------------------------------------------------------------------
# Install libs + headers into the app tree
# ---------------------------------------------------------------------------
New-Item -ItemType Directory -Force -Path $prebuilt | Out-Null
New-Item -ItemType Directory -Force -Path $include | Out-Null

# With MNN_SEP_BUILD=true each module is a separate .so.
# MNN_CL provides the OpenCL GPU backend; libllm.so depends on it at runtime
# when MNN_OPENCL=true (devices without OpenCL gracefully fall back to CPU).
$libNames = @("libMNN.so", "libMNN_Express.so", "libllm.so", "libMNNOpenCV.so", "libMNN_CL.so")
foreach ($lib in $libNames) {
    $found = Get-ChildItem $buildDir -Recurse -Filter $lib -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($found) {
        Copy-Item $found.FullName -Destination $prebuilt -Force
        Write-Host "Installed $lib"
    }
}

# Headers:
#   1) MNN core headers -> include/MNN/   (e.g. MNN/MNNDefine.h)
#   2) LLM engine public headers -> include/llm/   (e.g. llm/llm.hpp)
Copy-Item -Recurse -Force (Join-Path $mnnSrc "include\MNN") (Join-Path $include "MNN")
$llmIncludeDir = Join-Path $mnnSrc "transformers\llm\engine\include\llm"
if (Test-Path $llmIncludeDir) {
    New-Item -ItemType Directory -Force -Path (Join-Path $include "llm") | Out-Null
    Copy-Item -Force (Join-Path $llmIncludeDir "*.hpp") (Join-Path $include "llm")
    Write-Host "Installed llm headers"
} else {
    Write-Warning "LLM engine include dir not found at $llmIncludeDir"
}
# llm.hpp depends on a JSON library; MNN ships one under 3rd_party.
$jsonDir = Join-Path $mnnSrc "3rd_party\json"
if (Test-Path $jsonDir) {
    New-Item -ItemType Directory -Force -Path (Join-Path $include "json") | Out-Null
    Copy-Item -Force (Join-Path $jsonDir "*") (Join-Path $include "json")
}

Write-Host ""
Write-Host "Done. Prebuilts: $prebuilt"
Write-Host "Rebuild the app now (./gradlew :app:assembleDebug) to enable on-device AI recognition."
