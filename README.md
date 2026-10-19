# 18FoldAspectUnlock · 画幅切换解锁

为 **Xiaomi 18 Fold** 制作的 LSPosed 模块，扩展控制中心原有“画幅切换”的应用支持范围。当前版本 **0.1.0 测试版**。

[下载 APK](https://github.com/ymy615/18FoldAspectUnlock/releases/tag/v0.1.0) · [发布记录](https://github.com/ymy615/18FoldAspectUnlock/releases)

## 支持范围

| 项目 | 当前要求 |
| --- | --- |
| 手机 | Xiaomi 18 Fold（代号 `lhasa`） |
| 系统 | HyperOS **OS4.0.21.0.XPNCNXM** |
| Android | **17 / API 37** |
| 框架 | 已正常运行的 LSPosed，兼容 legacy Xposed API |
| 作用域 | **系统框架（android）** |

模块核对设备代号、Android API 和完整系统版本；不匹配时跳过 Hook。其他系统版本需要重新分析适配。

## 安装与使用

1. 从 [Releases](https://github.com/ymy615/18FoldAspectUnlock/releases) 下载并安装 APK。
2. 打开 LSPosed，在模块列表中启用 **画幅切换解锁**。
3. 作用域只勾选 **系统框架（android）**，不需要逐个勾选应用。
4. **完整重启手机**。
5. 打开应用，保持竖屏全屏，从控制中心点击原有的 **画幅切换**。

模块启动页提供版本检查与使用说明。“系统版本匹配”不代表 Hook 已激活；可在 LSPosed 日志中检查：

```text
[FoldAspectUnlock] ACTIVE 3/3 policy hooks; build=OS4.0.21.0.XPNCNXM
```

## 实际作用

当前适配固件包含 61 条应用禁用规则和 21 条页面强制全屏规则。模块解除这些限制，并放行普通应用 UID 的预装应用。实际切换仍由系统原有功能完成。

| Hook 方法 | 作用 |
| --- | --- |
| `MiuiSizeCompatService.isWideFoldDisabledPackage(String)` | 解除应用包名禁用限制 |
| `MiuiSizeCompatService.shouldUseFullscreenBypassInternal(String)` | 解除普通应用页面的强制全屏规则 |
| `BoundsCompatUtils.isWideFoldSystemApp(ApplicationInfo)` | 放行普通预装应用，保留关键系统壳与特权 UID |

三处方法的签名全部匹配后才安装 Hook；安装失败会撤回本次已经安装的 Hook。

## 限制

- 保留系统权限、锁屏、桌面/最近任务、助手/梦境、主显示屏、竖屏和全屏窗口条件。
- 固定横屏、分屏及已有独立游戏比例配置仍受系统原有规则约束。
- **解除限制不代表所有应用都能正确显示。** 相机预览、扫码和其他特殊页面需要实际测试。
- 不修改系统文件、屏幕全局分辨率或其他应用 APK。

## 验证情况

- 在目标固件上完成安装、启动页检查，并从系统框架日志确认 **3/3 Hook 已激活**。
- APK 签名、对齐、入口和作用域检查通过，APK 中不包含 Xposed API 实现。
- 34 项可独立运行的策略检查通过。结合本地提取的固件 XML，总计 117 项检查通过，覆盖上述 61 条应用规则与 21 条页面规则。
- **尚未完成内外屏实际切换、全部应用画面、相机扫码或游戏的系统性验证。** 本版本按测试版发布。

## 恢复

先将试用应用切回全屏，再在 LSPosed 停用模块并完整重启；也可卸载模块后完整重启。系统保存的每个应用画幅选择不会因停用模块自动清除。

## 从源码构建

环境：Windows、PowerShell 5.1 或更新版本、JDK 17+（本项目用 JDK 21 验证）、Android SDK Platform 37、build-tools 36.0.0。

```powershell
git clone https://github.com/ymy615/18FoldAspectUnlock.git
cd 18FoldAspectUnlock
powershell -NoProfile -ExecutionPolicy Bypass -File .\test.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1
```

构建脚本默认从 `JAVA_HOME` 及 `ANDROID_SDK_ROOT` / `ANDROID_HOME` 查找工具；也可显式指定路径：

```powershell
.\build.ps1 -JavaRoot 'C:\path\to\jdk' -SdkRoot 'C:\path\to\Android\Sdk' -Platform 'android-37.0'
```

如果 SDK 平台目录名为 `android-37`，将 `-Platform` 改为该名称。首次构建会从发布者站点下载编译用 Xposed API 82，并校验固定 SHA-256。无需安装 Gradle。

- APK 输出到 `dist/FoldAspectUnlock-0.1.0.apk`，同时生成 `.sha256` 文件。
- 首次构建在 `.local/` 生成本地签名密钥。请保留密钥以便后续更新，**不要提交或分享**。
- 自行构建的签名与 Releases 中的签名通常不同，不能直接覆盖另一签名的安装包。
- Xposed API 仅用于编译，不会打包进 APK；来源和许可证见 [libs/SOURCE.txt](libs/SOURCE.txt)。

有合法取得的匹配固件策略 XML 时，可额外运行设备规则检查：

```powershell
.\test.ps1 -FirmwarePolicy 'C:\path\to\wide_fold_size_compat.xml'
```

仓库不包含厂商 APK/JAR、反编译源码、手机日志、账户资料或签名密钥。
