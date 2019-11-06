package(
    default_visibility = ["//visibility:public"],
    licenses = ["notice"],  # Apache 2.0
)

load("@rules_java//java:defs.bzl", "java_plugin")
load("@rules_java//java:defs.bzl", "java_library")

java_plugin(
    name = "jsinterop-externs-generator-plugin",
    srcs = glob(["src/main/java/com/icsusa/jsinterop/*.java"]),
    resources = glob(["src/main/resources/**"]),
    processor_class = "com.icsusa.jsinterop.ExternsProcessor"
)

java_library(
    name = "jsinterop-externs-generator",
    srcs = glob(["src/main/java/com/icsusa/jsinterop/*.java"]),
    resources = glob(["src/main/resources/**"]),
    exported_plugins = ["jsinterop-externs-generator-plugin"],
)


