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
