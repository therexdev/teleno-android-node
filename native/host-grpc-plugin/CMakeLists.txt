cmake_minimum_required(VERSION 3.19)
project(teleno_host_grpc_plugin LANGUAGES CXX)
if(CMAKE_CROSSCOMPILING)
  message(FATAL_ERROR "This generator must run on the build host")
endif()
include(FetchContent)
find_package(Threads REQUIRED)
set(TELENO_GRPC_ARCHIVE "https://github.com/cpp-pm/grpc/archive/v1.31.0-p0.tar.gz" CACHE STRING "Original pinned gRPC archive")
FetchContent_Declare(teleno_host_grpc
  URL "${TELENO_GRPC_ARCHIVE}"
  URL_HASH SHA256=48f52c566a03b3bea26e8440798d2ff4ce181221935e5983fb9495e13618dd55
  SOURCE_SUBDIR host_population_only)
FetchContent_MakeAvailable(teleno_host_grpc)
foreach(_required HOST_PROTOBUF_INCLUDE HOST_PROTOBUF_LIBRARY HOST_PROTOC_LIBRARY)
  if(NOT DEFINED ${_required} OR NOT EXISTS "${${_required}}")
    message(FATAL_ERROR "Provide ${_required} from the pinned host protobuf build")
  endif()
endforeach()
add_executable(grpc_cpp_plugin
  "${teleno_host_grpc_SOURCE_DIR}/src/compiler/cpp_plugin.cc"
  "${teleno_host_grpc_SOURCE_DIR}/src/compiler/cpp_generator.cc")
target_compile_features(grpc_cpp_plugin PRIVATE cxx_std_17)
target_include_directories(grpc_cpp_plugin PRIVATE
  "${teleno_host_grpc_SOURCE_DIR}" "${teleno_host_grpc_SOURCE_DIR}/include"
  "${HOST_PROTOBUF_INCLUDE}")
target_link_libraries(grpc_cpp_plugin PRIVATE
  "${HOST_PROTOC_LIBRARY}" "${HOST_PROTOBUF_LIBRARY}" Threads::Threads)
