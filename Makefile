# 本地开发门禁；不安装工具或增加检查框架。
# uv run 默认可同步本地环境，Gradle 默认可解析并下载依赖。
# 可覆盖 FLUTTER、UV、JAVA_HOME、ANDROID_HOME；严格使用本地缓存需传
# GRADLE_FLAGS='--offline --console=plain' UV_FLAGS=--offline。
# FLUTTER 只覆盖 Flutter 命令；Gradle 的 Flutter SDK 仍由 local.properties 指定。
SHELL := /bin/sh
.DEFAULT_GOAL := build

LOCAL_PROPERTIES := app_shell/android/local.properties
FLUTTER ?= $(shell command -v flutter 2>/dev/null || { if [ -f "$(LOCAL_PROPERTIES)" ]; then awk -F= '$$1 == "flutter.sdk" { sub(/\r$$/, "", $$2); print $$2 "/bin/flutter"; exit }' "$(LOCAL_PROPERTIES)"; fi; })
# 未配置本地 SDK 时保留命令名，让 shell 给出真实的工具缺失错误。
ifeq ($(strip $(FLUTTER)),)
FLUTTER := flutter
endif
UV ?= uv
UV_FLAGS ?=
GRADLE_FLAGS ?= --console=plain
ANDROID_HOME ?= $(if $(ANDROID_SDK_ROOT),$(ANDROID_SDK_ROOT),$(shell if [ -f "$(LOCAL_PROPERTIES)" ]; then awk -F= '$$1 == "sdk.dir" { sub(/\r$$/, "", $$2); print $$2; exit }' "$(LOCAL_PROPERTIES)"; fi))

ifneq ($(strip $(ANDROID_HOME)),)
export ANDROID_HOME
endif
ifneq ($(strip $(JAVA_HOME)),)
export JAVA_HOME
export PATH := $(JAVA_HOME)/bin:$(PATH)
endif

.PHONY: build build-app build-capture build-asr \
	test test-flutter test-core test-benchmark \
	lint lint-flutter lint-android

# 独立子目标便于 make -k 收集全部结果，任一失败仍会令总目标失败。
# app 依赖采集库；ASR 尚未接入应用，显式构建全部模块以覆盖工程编译。
build: build-app build-capture build-asr

build-app:
	@printf '\n==> Android 应用 debug 构建\n'
	cd app_shell/android && ./gradlew $(GRADLE_FLAGS) :app:assembleDebug

build-capture:
	@printf '\n==> Android 采集模块 debug 构建\n'
	cd app_shell/android && ./gradlew $(GRADLE_FLAGS) :capture_android:assembleDebug

build-asr:
	@printf '\n==> Android ASR 模块 debug 构建\n'
	cd app_shell/android && ./gradlew $(GRADLE_FLAGS) :asr_android:assembleDebug

test: test-flutter test-core test-benchmark

test-flutter:
	@printf '\n==> Flutter 单元测试\n'
	cd app_shell && "$(FLUTTER)" test --no-pub

test-core:
	@printf '\n==> core JVM 单元测试\n'
	cd app_shell/android && ./gradlew $(GRADLE_FLAGS) :core:test

test-benchmark:
	@printf '\n==> Python 评测工具测试\n'
	"$(UV)" run $(UV_FLAGS) pytest

# 复用 Flutter analyzer 与 Android Gradle 插件自带 lint，不新增依赖。
lint: lint-flutter lint-android

lint-flutter:
	@printf '\n==> Flutter 静态分析\n'
	cd app_shell && "$(FLUTTER)" analyze --no-pub

lint-android:
	@printf '\n==> Android debug lint\n'
	cd app_shell/android && ./gradlew $(GRADLE_FLAGS) --continue :app:lintDebug :capture_android:lintDebug :asr_android:lintDebug
