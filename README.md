<div align="center">

# Stellar
一个基于 Shizuku 的深度定制分支，让应用通过 ADB 或 Root 权限使用系统级 API

[![GitHub Stars](https://img.shields.io/github/stars/xiaoancute/Stellar?style=flat-square&logo=github&logoColor=white&color=181717&cacheSeconds=0)](https://github.com/xiaoancute/Stellar/stargazers)
[![GitHub Forks](https://img.shields.io/github/forks/xiaoancute/Stellar?style=flat-square&logo=github&logoColor=white&color=181717)](https://github.com/xiaoancute/Stellar/forks)
[![GitHub Issues](https://img.shields.io/github/issues/xiaoancute/Stellar?style=flat-square&logo=github&logoColor=white&color=e74c3c)](https://github.com/xiaoancute/Stellar/issues)
[![GitHub Release](https://img.shields.io/github/v/release/xiaoancute/Stellar?style=flat-square&logo=github&logoColor=white&color=28a745)](https://github.com/xiaoancute/Stellar/releases)

---
官方交流群组：
[![QQ群](https://img.shields.io/badge/QQ-1群-12B7F5?style=flat-square&logo=qq&logoColor=white)](https://qm.qq.com/cgi-bin/qm/qr?k=bIpIHQX12Kajh951zELULlF5FN6zeN0y&jump_from=webapi&authKey=Kf6RnfWG1o7whQIi20Uz+X6/dzf/D6/TzED25Pyb0N5td/eVClgysJXgPYnbZhr5)

</div>

## 项目简介

Stellar 是 [Shizuku](https://github.com/RikkaApps/Shizuku) 的深度定制版本，专为开发者提供更灵活、更强大的特权 API 框架。通过 ADB 无线调试或 Root 权限启动服务后，应用程序可以调用需要系统级权限的 API，而无需应用本身拥有 Root 权限。

## 核心特性

Stellar 相比原版 Shizuku 进行了以下核心改进：

### 权限系统增强

- **全新权限架构** - 摒弃单一权限模式，引入精细化的多维度权限管理体系
- **分级权限控制**：
  - `stellar` - 核心 API 访问权限，授予基础服务调用能力
  - `follow_stellar_startup` - 服务伴随启动权限，实现应用与 Stellar 服务的生命周期绑定
- **智能权限回调** - 增强回调机制，客户端可精准感知授权类型（永久授权/一次性授权）
- **完整权限管理 API** - 提供全套权限查询、申请、撤销接口，满足复杂业务场景需求

### 启动与服务优化

- **开机启动** - 应用可尝试通过开机广播、无障碍权限和 Root 权限实现开机自启，还可预热以便于无网启动
- **服务伴随启动** - 应用可注册为 Stellar 服务的伴随进程，实现服务启动时自动唤醒
- **双进程互守** - 可开启守护进程，Stellar 服务与守护进程相互监测异常关闭并重新启动

### Termux 命令行集成

- **特权命令执行** - Termux 可通过 `stsh` 在 Stellar 服务身份下执行 Android shell 命令
- **服务端直连** - 命令桥接直接运行于 Stellar 特权服务进程，不依赖管理器 Activity、通知或前台服务常驻
- **本地认证** - 仅监听设备回环地址，并使用随机令牌验证 Termux 客户端
- **结果回传** - 支持命令参数、输出内容和退出状态返回

### 架构重构

- **服务层重构** - 重新设计核心服务架构，优化模块间通信机制，提升整体性能与响应速度
- **UserService 重写** - 全面重构用户服务层，优化服务架构设计，提升代码可维护性与扩展性
- **Shizuku 兼容性修复** - 修复原 Shizuku 遗留的已知问题，增强框架稳定性

### UI/UX 改进

Stellar 对用户界面进行了全面重构，带来更现代、更直观的使用体验：

- **全新授权管理界面** - 采用 Material Design 3 设计语言，打造简洁优雅的授权管理中心
- **授权页面焕新** - 重新设计授权交互流程，操作更加流畅自然
- **权限可视化展示** - 授权页清晰呈现各项细分权限，用户一目了然
- **应用列表优化** - 改进已授权应用列表，权限状态一览无余
- **引导流程升级** - 重新设计启动引导页面，新用户上手更轻松

## 与 Shizuku 的主要区别

### 移除的功能
- **Sui** - 移除了 API 对 Zygisk-Sui 的支持

### 新增的功能
- **跟随启动机制** - 应用可跟随 Stellar 服务自动启动
- **细分权限系统** - 支持多种权限类型的精细化管理
- **权限回调增强** - 支持一次性授权感知
- **降权激活** - Root 启动后可降权到 Shell 用户运行，提高安全性
- **Termux 命令行集成** - 使用 `stsh` 从 Termux 调用 Stellar 特权服务执行 Android 命令

### 重新启用的功能

Stellar 重新启用了 Shizuku 最新版本中已标记为弃用的功能：

- **`newProcess()` API** - 直接创建特权进程的方法，Shizuku 已弃用但 Stellar 保留支持
- **运行时权限授予/撤销** - 通过 `grantRuntimePermission()` 和 `revokeRuntimePermission()` 为其他应用授予或撤销 Android 运行时权限

这些功能在某些场景下仍然非常实用，Stellar 选择继续支持以提供更完整的 API。

### 架构优化
- 100% Kotlin 代码
- 精简模块结构
- 规范化命名

## Termux 命令行集成

Stellar 提供 `stsh` 命令，使 Termux 能够在 Stellar 特权服务进程中执行 Android 命令。该功能面向通用系统管理、调试和自动化场景，不依赖或限定于任何特定应用。

### 权限模型

`stsh` 创建的命令进程继承 Stellar 服务的运行身份：

- 通过 ADB 启动 Stellar 时，命令通常以 `uid=2000(shell)` 和 `u:r:shell:s0` 执行
- 通过 Root 启动且未启用降权时，命令将继承 Root 服务身份
- 通过 Root 启动并启用「降权激活」时，命令以 Shell 身份执行

因此，`stsh` 的实际能力由 Stellar 的启动方式、Android 版本、ROM 策略、SELinux 域和目标资源权限共同决定。Shell 身份不等同于 Root，也不能绕过 SELinux 强制访问控制。

### 工作原理

1. Stellar 服务在设备回环地址启动本地命令桥接
2. 服务启动时生成随机认证令牌，并写入 Termux 的专属外部数据目录
3. `stsh` 读取令牌并通过本地连接提交命令参数
4. Stellar 服务创建子进程执行命令，并向 Termux 返回输出内容及退出状态

桥接仅监听 `127.0.0.1`，不会向局域网或外部网络开放。认证令牌位于：

```text
/storage/emulated/0/Android/data/com.termux/files/.stellar-stsh-token
```

### 安装

1. 从 [Releases](https://github.com/xiaoancute/Stellar/releases) 安装支持 Termux 集成的 Stellar APK
2. 启动 Stellar 特权服务
3. 在 Termux 中安装 Python：

```sh
pkg install python
```

4. 下载 Release 附件中的 `stsh`，安装到 Termux：

```sh
install -m 755 stsh "$PREFIX/bin/stsh"
```

也可以在 Stellar 管理器的终端页面导出 APK 内置的 `stsh` 文件，再将其安装到 `$PREFIX/bin`。

### 使用

执行单条 Android shell 命令：

```sh
stsh -c "id"
```

执行系统设置查询：

```sh
stsh -c "settings get global adb_enabled"
```

访问 Shell 身份有权读取的文件或目录：

```sh
stsh -c "ls -la /storage/emulated/0/Android/data"
```

检查命令退出状态：

```sh
stsh -c "exit 37"
echo "$?"
```

### 使用限制

- 命令由 Android 的 `/system/bin/sh` 执行，不是在 Termux 的 Bash 环境中运行
- 建议使用 `stsh -c "命令"` 执行非交互式任务
- 当前桥接不转发标准输入，不适用于需要持续交互的终端程序
- 命令输出会在进程结束后统一返回，不适合产生无限输出或超大输出的任务
- Stellar 服务重启后会重新生成认证令牌，`stsh` 会自动读取新令牌
- 能否访问某个应用目录或系统接口，取决于 Shell/Root 身份和设备安全策略，而不是目标应用类型

### 真机验证

当前版本已在 Android 16 设备上验证以下行为：

- 返回 `uid=2000(shell)` 和 `u:r:shell:s0`
- 连续多次命令调用正常完成
- 可读取 Shell 身份有权访问的应用外部数据文件
- 可创建、读取并删除临时文件
- 标准输出、标准错误内容和非零退出状态可返回 Termux

## Shizuku 兼容层

Stellar 内置了 Shizuku 兼容层，允许使用 Shizuku API 的应用无需修改代码即可使用 Stellar 服务。

### 工作原理

兼容层通过以下方式实现无缝兼容：

1. **客户端兼容** - `ShizukuProvider` 接收 Stellar 服务发送的 Binder，并通过 `ShizukuCompat` 管理连接状态
2. **服务端拦截** - `ShizukuServiceIntercept` 实现完整的 `IShizukuService` 接口，将 Shizuku API 调用转发到 Stellar 服务
3. **权限映射** - 自动将 Shizuku 权限请求映射到 Stellar 的 `shizuku` 权限

### 支持的 API

兼容层支持 Shizuku 的核心 API：

- `pingBinder()` / `getVersion()` / `getUid()` - 服务状态查询
- `checkSelfPermission()` / `requestPermission()` - 权限管理
- `newProcess()` - 创建特权进程
- `addUserService()` / `removeUserService()` - 用户服务管理
- `transactRemote()` - Binder 事务转发

### 启用/禁用

Shizuku 兼容层默认启用。如需禁用，可在 Stellar 管理器的设置页面中关闭「Shizuku 兼容层」开关。

### 注意事项

- 兼容层会自动拒绝来自 Shizuku Manager 的请求，避免冲突
- 使用 Shizuku API 的应用需要在 `AndroidManifest.xml` 中配置 `ShizukuProvider`

## 降权激活

降权激活功能允许以 Root 权限启动 Stellar 服务后，自动降权到 Shell 用户（uid=2000）运行，提高安全性。

### 启用方式

在 Stellar 管理器的设置页面中，开启「降权激活」开关即可。

### 工作原理

启用降权激活后，启动流程如下：

```
su (root) → libchid.so 2000 → libstellar.so --apk=...
```

1. 使用 Root 权限执行 `libchid.so`
2. `libchid.so` 将进程身份切换到 uid=2000（Shell 用户）
3. 以 Shell 身份执行 `libstellar.so` 启动服务

### 注意事项

- 降权激活仅在 Root 启动模式下生效
- ADB 启动模式本身就是 uid=2000，无需降权
- 降权后服务将失去 Root 特有的能力（如写入系统属性、访问受保护目录等）

## 快速开始

### 集成 Stellar 到你的应用

查看完整的集成指南和 API 文档：

- **[API 集成指南](INTEGRATION_GUIDE.md)** - 完整的集成步骤、API 参考和代码示例
- **[从 Shizuku 迁移](INTEGRATION_GUIDE.md#从-shizuku-迁移)** - 详细的迁移步骤和 API 对比

### 基本使用流程

1. 添加 JitPack 依赖：`com.github.roro2239:Stellar-API:latest.release`
2. 配置 `StellarProvider` 到 AndroidManifest
3. 初始化 Stellar 并请求权限
4. 使用 Stellar API 执行特权操作

> 详细步骤请查看 [API 集成指南](INTEGRATION_GUIDE.md)

### 在 Termux 中使用

如需从 Termux 执行 Android 特权命令，请参阅 [Termux 命令行集成](#termux-命令行集成)。

## 致谢与许可

### 致谢

本项目基于 [Shizuku](https://github.com/RikkaApps/Shizuku)，由 [RikkaApps](https://github.com/RikkaApps) 开发。感谢原作者的杰出工作。

### 许可证

本项目的修改部分采用 [Mozilla Public License 2.0](LICENSE)。

原始 Shizuku 代码保留其 Apache License 2.0 许可证。

| 组件 | 许可证 |
|------|--------|
| Stellar 修改部分 | Mozilla Public License 2.0 |
| [Shizuku](https://github.com/RikkaApps/Shizuku) 原始代码 | Apache License 2.0 |

## 贡献

欢迎提交 Issue 和 Pull Request。在提交代码前，请确保：

- 代码风格符合项目规范（Kotlin）
- 添加必要的注释和文档
- 测试通过所有功能

## 联系方式

- GitHub Issues: [提交问题](https://github.com/xiaoancute/Stellar/issues)
- 项目主页: [xiaoancute/Stellar](https://github.com/xiaoancute/Stellar)

## 相关链接

- [完整 API 文档](INTEGRATION_GUIDE.md)
- [原版 Shizuku](https://github.com/RikkaApps/Shizuku)
