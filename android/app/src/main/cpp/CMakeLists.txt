cmake_minimum_required(VERSION 3.22.1)

project("sync_engine")

set(CMAKE_CXX_STANDARD 17)

# gRPC NDK Setup Note:
# To fully use gRPC C++ Core, you would typically use vcpkg or a pre-built gRPC for Android.
# For this implementation, we focus on the JNI structure and high-performance buffer handling.

add_library(sync_engine SHARED
        native-lib.cpp
)

find_library(log-lib log)

target_link_libraries(sync_engine
        ${log-lib}
)
