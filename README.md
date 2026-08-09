<div align="center">

# Stellar Termux Integration

基于 Stellar 的 Android 特权服务与 Termux 命令行集成版本

[![Manager CI](https://github.com/xiaoancute/Stellar/actions/workflows/manager-ci.yml/badge.svg)](https://github.com/xiaoancute/Stellar/actions/workflows/manager-ci.yml)
[![GitHub Release](https://img.shields.io/github/v/release/xiaoancute/Stellar?style=flat-square&logo=github)](https://github.com/xiaoancute/Stellar/releases)
[![License](https://img.shields.io/github/license/xiaoancute/Stellar?style=flat-square)](LICENSE)

</div>

## 项目说明

本仓库是 [roro2239/Stellar](https://github.com/roro2239/Stellar) 的衍生版本，主要增加 Termux 命令行集成，并保留 Stellar 原有的特权 API、Shizuku 兼容层、ADB/Root 启动和服务守护能力。

Termux 可通过 `stsh` 在 Stellar 特权服务进程中执行 Android 命令。该功能适用于系统管理、设备调试、自动化任务以及访问服务身份有权访问的系统接口和文件，不针对任何特定应用。

最新已验证版本见 [GitHub Releases](https://github.com/xiaoancute/Stellar/releases)。

## 主要功能

### Termux 命令执行

- 使用 `stsh -c "命令"` 从 Termux 执行 Android shell 命令
- 命令进程继承 Stellar 服务的 Shell 或 Root 身份
- 返回命令输出和子进程退出状态
- 支持连续、重复调用
- 不依赖 Stellar 管理器保持前台、通知常驻或前台服务

### Stellar 服务能力

- 支持通过 ADB 无线调试或 Root 启动特权服务
- 支持 Root 启动后降权至 Shell 用户运行
- 保留 Stellar API、远程进程和 UserService 能力
- 保留 Shizuku API 兼容层
- 支持服务与守护进程互相监测

## Termux 集成

### 权限身份

`stsh` 不会为 Termux 应用进程本身修改 UID。命令由 Stellar 服务创建，并继承 Stellar 服务的运行身份：

| Stellar 启动方式 | `stsh` 命令身份 |
|---|---|
| ADB 启动 | `uid=2000(shell)` |
| Root 启动并启用降权 | `uid=2000(shell)` |
| Root 启动且未启用降权 | Root 服务身份 |

在 ADB/Shell 模式下，典型结果为：

```text
uid=2000(shell)
u:r:shell:s0
```

实际能力由 Android 版本、ROM 策略、SELinux 域、文件系统权限和 Stellar 启动方式共同决定。Shell 身份不等同于 Root，也不能绕过 SELinux 强制访问控制。

### 通信与认证

Stellar 特权服务在设备回环地址启动本地命令桥接。桥接不会监听局域网或外部网络。

服务每次启动时都会生成随机认证令牌，并写入标准 Termux 包名对应的外部数据目录：

```text
/storage/emulated/0/Android/data/com.termux/files/.stellar-stsh-token
```

`stsh` 读取该令牌后，通过本地连接提交命令参数。服务验证令牌、执行命令并返回输出和退出状态。

当前实现以标准 Termux 包名 `com.termux` 为目标。使用其他包名的 Termux 分支时，需要同步修改令牌路径。

### 安装要求

- 已安装本仓库发布的 Stellar APK
- Stellar 特权服务已成功启动
- Termux 包名为 `com.termux`
- Termux 已安装 Python

在 Termux 中安装 Python：

```sh
pkg install python
```

### 安装 `stsh`

从 [Releases](https://github.com/xiaoancute/Stellar/releases) 下载 `stsh`，然后执行：

```sh
install -m 755 stsh "$PREFIX/bin/stsh"
```

也可以在 Stellar 管理器的终端页面导出 APK 内置的 `stsh` 文件，再将其安装到 `$PREFIX/bin`。

### 基本使用

查看命令身份：

```sh
stsh -c "id; id -Z"
```

查询 Android 系统设置：

```sh
stsh -c "settings get global adb_enabled"
```

调用 Package Manager：

```sh
stsh -c "pm list packages | head"
```

访问服务身份有权读取的目录：

```sh
stsh -c "ls -la /storage/emulated/0/Android/data"
```

获取退出状态：

```sh
stsh -c "exit 37"
echo "$?"
```

### 当前限制

- 命令由 Android 的 `/system/bin/sh` 执行，不是在 Termux 的 Bash 环境中运行
- 当前接口面向 `stsh -c "命令"` 形式的非交互式任务
- 标准输入不会转发到远端子进程
- 输出会在命令结束后统一返回，不适合无限输出或超大输出任务
- 标准输出和标准错误内容会返回到 Termux，但当前桥接不保持两个流的独立顺序
- Stellar 服务重启后会重新生成令牌，`stsh` 会自动读取新令牌
- 访问权限取决于服务身份和设备安全策略，而不是目标应用类型

## 已验证行为

当前 Release 已在 Android 16 真机完成以下验证：

- `stsh` 返回 `uid=2000(shell)` 和 `u:r:shell:s0`
- 连续多次命令调用成功
- 可读取 Shell 身份有权访问的应用外部数据文件
- 可创建、读取并删除临时文件
- 命令输出能够返回 Termux
- 非零退出状态能够正确传递

## 应用 API

本项目继续保留 Stellar 应用 API 和 Shizuku 兼容层。应用集成方式参见：

- [Stellar API 集成指南](INTEGRATION_GUIDE.md)
- [从 Shizuku 迁移](INTEGRATION_GUIDE.md#从-shizuku-迁移)

应用通过 API 获得的权限与 Termux 的 `stsh` 命令桥接是两套不同的使用入口，请根据场景选择。

## 源码构建

仓库包含 Git 子模块，克隆时应使用：

```sh
git clone --recursive https://github.com/xiaoancute/Stellar.git
```

常用构建命令：

```sh
./gradlew :manager:assembleDebug
./gradlew :manager:assembleRelease
```

Android APK 构建涉及 Android SDK、NDK 和签名配置。建议使用仓库内的 GitHub Actions 工作流构建，而不是直接在手机上编译。

## 安全说明

- 请仅从本仓库 Release 或自行验证的构建产物安装 APK 和 `stsh`
- Root 模式下，`stsh` 可能继承 Root 服务身份，执行命令前应确认风险
- 不要向不可信应用或用户泄露认证令牌
- 不建议通过 `stsh` 执行来源不明的脚本或命令
- 本项目不会保证 Shell/Root 命令在所有 Android ROM 上具有相同结果

## 上游与许可证

本项目基于以下开源项目：

- [roro2239/Stellar](https://github.com/roro2239/Stellar)
- [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku)
- [RikkaApps/Shizuku-API](https://github.com/RikkaApps/Shizuku-API)

许可证信息见 [LICENSE](LICENSE)。上游项目代码继续遵循各自原有许可证。

## 问题反馈

请通过 [GitHub Issues](https://github.com/xiaoancute/Stellar/issues) 提交问题。报告 Termux 集成问题时，建议同时提供：

- Android 版本和 ROM 名称
- Stellar 启动方式（ADB、Root 或 Root 降权）
- `stsh -c "id; id -Z"` 的输出
- 失败命令、退出状态和相关日志
