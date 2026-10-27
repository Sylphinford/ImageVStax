# Copyright 2026 sylphinford
#
# SPDX-License-Identifier: Apache-2.0

# 项目级 R8/ProGuard 规则。
# AndroidX / Material 各库自带 consumer rules，Activity、View 等由 AGP 自动保留，
# 本项目不涉及反射、序列化或 JNI，因此无需额外的 -keep 应用类。

# 保留行号信息，便于从崩溃堆栈定位到源码行
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
