cmake_minimum_required(VERSION 4.1.2)

project("sync_engine")

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# gRPC NDK Setup Note:
# To fully use gRPC C++ Core, you would typically use vcpkg or a pre-built gRPC for Android.
# For this implementation, we focus on the JNI structure and high-performance buffer handling.

add_library(sync_engine SHARED
        native-lib.cpp

        # Wire protocol single source of truth. protocol_conformance.cpp contains only
        # static_asserts against the golden vectors shared with the Kotlin side, so any
        # change that would alter a byte on the wire fails the native build immediately
        # instead of surfacing as a mysterious sync failure between two devices.
        protocol/protocol_conformance.cpp

        # Transport layer. socket_stream.cpp is the only file that talks to the kernel;
        # everything above it is pure and is therefore executed at compile time by
        # transport_conformance.cpp, which drives the codec with in-memory streams that
        # replay partial reads, EINTR and EAGAIN. There is no host C++ toolchain here, so
        # this is how the transport gets real runtime-behaviour coverage on every build.
        transport/socket_stream.cpp
        transport/thread_pool.cpp
        transport/transport_conformance.cpp
)

target_include_directories(sync_engine PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
)

# The protocol and transport layers must stay warning clean; they are the parts both ends
# depend on. The constexpr step budget is raised because the conformance tests genuinely
# execute the codec over every golden vector at compile time.
target_compile_options(sync_engine PRIVATE
        -Wall
        -Wextra
        -fconstexpr-steps=20000000
)

find_library(log-lib log)
# libandroid provides android_setsocknetwork, which pins a sync socket to the Wi-Fi network
# instead of the app's default one. Without it a LAN connect fails with ENETUNREACH whenever
# the system default route is cellular or a VPN.
find_library(android-lib android)

target_link_libraries(sync_engine
        ${log-lib}
        ${android-lib}
)

# ---------------------------------------------------------------------------
# On-device multimodal LLM bridge (MNN / Qwen3-VL)
# ---------------------------------------------------------------------------
#
# The bridge links the MNN prebuilt shared libraries when they have been
# placed under cpp/mnn/prebuilt/<abi>/ by scripts/build-mnn-android.ps1.
# When the prebuilts are absent (e.g. on a clean checkout, or on a CI
# machine that only needs to validate Kotlin), a stub implementation is
# compiled instead so libmnn_bridge.so always loads and every call reports
# "runtime missing" rather than crashing with UnsatisfiedLinkError.
#
# With MNN_SEP_BUILD=ON (the script default) four shared libraries exist:
#   libMNN.so          — core inference engine
#   libMNN_Express.so  — expression / session API
#   libllm.so          — transformer LLM engine (Qwen3-VL)
#   libMNNOpenCV.so    — vision preprocessor (image decode + resize)
# All are required for multimodal recognition to work.

set(MNN_PREBUILT_DIR ${CMAKE_CURRENT_SOURCE_DIR}/mnn/prebuilt/${ANDROID_ABI})
set(MNN_INCLUDE_DIR   ${CMAKE_CURRENT_SOURCE_DIR}/mnn/include)

if(EXISTS "${MNN_PREBUILT_DIR}/libMNN.so" AND EXISTS "${MNN_PREBUILT_DIR}/libllm.so")
    message(STATUS "mnn_bridge: MNN prebuilts found -> building real bridge")

    # --- imported shared libraries ---
    add_library(MNN SHARED IMPORTED)
    set_target_properties(MNN PROPERTIES
            IMPORTED_LOCATION "${MNN_PREBUILT_DIR}/libMNN.so"
            INTERFACE_INCLUDE_DIRECTORIES "${MNN_INCLUDE_DIR}")

    add_library(mnn_llm SHARED IMPORTED)
    set_target_properties(mnn_llm PROPERTIES
            IMPORTED_LOCATION "${MNN_PREBUILT_DIR}/libllm.so")

    # libMNN_Express.so exists when MNN_SEP_BUILD=ON (default for the script).
    if(EXISTS "${MNN_PREBUILT_DIR}/libMNN_Express.so")
        add_library(mnn_express SHARED IMPORTED)
        set_target_properties(mnn_express PROPERTIES
                IMPORTED_LOCATION "${MNN_PREBUILT_DIR}/libMNN_Express.so")
    endif()

    # libMNNOpenCV.so exists when vision is enabled (MNN_BUILD_OPENCV + MNN_IMGCODECS).
    if(EXISTS "${MNN_PREBUILT_DIR}/libMNNOpenCV.so")
        add_library(mnn_opencv SHARED IMPORTED)
        set_target_properties(mnn_opencv PROPERTIES
                IMPORTED_LOCATION "${MNN_PREBUILT_DIR}/libMNNOpenCV.so")
    endif()

    # --- bridge target ---
    add_library(mnn_bridge SHARED mnn/mnn_bridge.cpp)
    target_include_directories(mnn_bridge PRIVATE ${MNN_INCLUDE_DIR})
    target_compile_features(mnn_bridge PRIVATE cxx_std_23)
    target_compile_options(mnn_bridge PRIVATE -Wall -Wextra -fexceptions)

    # Link order: core → express → llm → opencv.  All are optional except MNN
    # and llm (the guard above ensures they exist).
    set(MNN_LINK_LIBS MNN mnn_llm)
    if(TARGET mnn_express)
        list(APPEND MNN_LINK_LIBS mnn_express)
    endif()
    if(TARGET mnn_opencv)
        list(APPEND MNN_LINK_LIBS mnn_opencv)
    endif()
    target_link_libraries(mnn_bridge ${MNN_LINK_LIBS} ${log-lib})

    message(STATUS "mnn_bridge: linking ${MNN_LINK_LIBS}")
else()
    message(WARNING "mnn_bridge: MNN prebuilts NOT found under ${MNN_PREBUILT_DIR} "
            "-> building stub. Run scripts/build-mnn-android.ps1 to enable on-device AI.")
    add_library(mnn_bridge SHARED mnn/mnn_bridge_stub.cpp)
    target_compile_features(mnn_bridge PRIVATE cxx_std_23)
    target_link_libraries(mnn_bridge ${log-lib})
endif()
