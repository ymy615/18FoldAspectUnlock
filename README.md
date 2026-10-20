# 18FoldAspectUnlock · 画幅切换解锁

为 **Xiaomi 18 Fold** 制作的 LSPosed 模块，扩展控制中心原有“画幅切换”的应用支持范围。当前版本 **0.2.0 测试版**。

[版本发布页](https://github.com/ymy615/18FoldAspectUnlock/releases/tag/v0.2.0) · [全部发布记录](https://github.com/ymy615/18FoldAspectUnlock/releases)

在仓库首页右侧 **Releases** 点击 **v0.2.0**，进入版本页面后，在 **Assets** 中下载 `FoldAspectUnlock-0.2.0.apk`。

## 0.2.0 新功能

- **展开恢复默认全屏**：内屏展开期间暂停普通应用的画幅模式。外屏画幅选择保留，折回后自动恢复；不写入“永久关闭画幅”的设置。
- **画幅模式保留状态栏**：移除画幅模式额外施加的顶部状态栏隐藏。视频、游戏等应用自身请求的沉浸式全屏保持原有行为。
- **更浅底色和白色图标**：外屏普通画幅模式使用 20% 不透明黑色状态栏底色，改善黑边上的文字对比度；退出画幅模式恢复应用原有样式。
- **紧凑状态栏**：外屏正向竖屏画幅模式将实际状态栏目标高度设为 **90 像素**，保留图标大小及大字体所需的最小空间。
- **减少多余顶部留白**：修正此固件对状态栏边距的重复缩放，保留图标大小和系统提供的摄像头安全区信息。

## 支持范围

| 项目 | 当前要求 |
| --- | --- |
| 手机 | Xiaomi 18 Fold（代号 `lhasa`） |
| 系统 | HyperOS **OS4.0.21.0.XPNCNXM** |
| Android | **17 / API 37** |
| 框架 | 已正常运行的 LSPosed，兼容 legacy Xposed API |
| 作用域 | **系统框架（android）＋系统界面（com.android.systemui）** |

模块核对设备代号、Android API 和完整系统版本；不匹配时跳过 Hook。其他系统版本需要重新分析适配。

## 安装与使用

1. 从仓库右侧 **Releases → v0.2.0 → Assets** 下载并安装 APK；已安装旧版时可以直接覆盖升级。
2. 打开 LSPosed，在模块列表中启用 **画幅切换解锁**。
3. 作用域勾选 **系统框架（android）** 和 **系统界面（com.android.systemui）**，不需要逐个勾选普通应用。旧版升级也需补勾选系统界面。
4. **完整重启手机**。
5. 在外屏打开应用，保持竖屏全屏，从控制中心点击原有的 **画幅切换**。展开内屏会恢复默认全屏，折回会恢复之前的外屏选择。

模块启动页提供版本检查与使用说明。“系统版本匹配”不代表 Hook 已激活；可在 LSPosed 日志中检查：

```text
[FoldAspectUnlock] ACTIVE 9/9 hooks; inner=default; outer=preserved; statusBar=semi-transparent; insets=corrected; build=OS4.0.21.0.XPNCNXM
[FoldAspectUnlock] UI ACTIVE 9/9 hooks; height=90px; scrim=20%
```

## 实际作用

当前适配固件包含 61 条应用禁用规则和 21 条页面强制全屏规则。模块解除这些限制，并放行普通应用 UID 的预装应用。实际切换仍由系统原有功能完成。

| Hook 方法 | 作用 |
| --- | --- |
| `MiuiSizeCompatService.isWideFoldDisabledPackage(String)` | 解除应用包名禁用限制 |
| `MiuiSizeCompatService.shouldUseFullscreenBypassInternal(String)` | 解除普通应用页面的强制全屏规则 |
| `BoundsCompatUtils.isWideFoldSystemApp(ApplicationInfo)` | 放行普通预装应用，保留关键系统壳与特权 UID |
| `MiuiSizeCompatService.isInnerCompatDisplay(ActivityTaskManagerService)` | 内屏使用默认画幅，沿用系统折叠配置更新与旧缩放状态清理 |
| `BoundsCompatController.hideStatusBarInSizeCompat()` | 去除画幅模式单独施加的状态栏强制隐藏 |
| `WindowState.getCompatInsetsState()` | 仅补偿符合条件的主窗口状态栏边距，保留摄像头及其他系统栏数据 |
| `DisplayPolicy.configureStatusBarOpacity(int)` | 外屏画幅状态栏选择原生半透明模式 |
| `DisplayPolicy.updateLightNavigationBarLw(int, WindowState)` | 在外观合并时保留上述模式，保留导航栏颜色标志 |
| `AppCompatLetterboxPolicy.getLetterboxDetails()` | 避免前台画幅应用的状态栏被黑边配色再次覆盖，不改真实黑边布局 |

九处方法及相关成员的签名全部匹配后才安装 Hook；安装失败会撤回本次已经安装的 Hook。内屏策略不修改每个应用的已保存比例及任务的全屏标记，因此可恢复外屏选择。

系统界面另外预检并安装九个绘制相关 Hook，只响应系统框架发出的当前画幅场景标记，调整状态栏窗口、图标容器和背景色；不进入普通应用进程。退出场景恢复原高度和底色。未勾选系统界面时，画幅及半透明状态栏基础功能仍可工作，但没有 90 像素高度和 20% 底色调整。

## 限制

- 保留系统权限、锁屏、桌面/最近任务、助手/梦境、主显示屏、竖屏和全屏窗口条件。
- 固定横屏、分屏及已有独立游戏比例配置仍受系统原有规则约束。
- 内屏展开期间不提供普通画幅切换；若需在内屏手动开启画幅，此版本不适用。应用自有固定比例与独立游戏比例不在本次自动恢复范围内。
- **解除限制不代表所有应用都能正确显示。** 相机预览、扫码和其他特殊页面需要实际测试。
- 顶部留白修正限定外屏竖屏全屏的普通应用主窗口；分屏、游戏独立缩放、复杂系统栏边界数据和应用自己绘制的留白不强行修改。
- 紧凑模式保留真实摄像头安全区及水平方向的挖孔避让。部分应用同时使用摄像头安全边距，因此仍可能预留约 126 像素；不会通过伪造挖孔尺寸强行挤入这些区域。
- 不修改系统文件、屏幕全局分辨率或其他应用 APK。

## 验证情况

- 最终安装包已在目标手机确认 **系统框架 9/9、系统界面 9/9 Hook 激活**。
- 小红书与模块说明页的外屏画幅模式实测状态栏高度为 **90 像素**。小红书上时间、电量及网络图标为白色且完整；白色背景上的底色像素为 RGB(204,204,204)，与 **20% 不透明黑色**一致。
- 返回桌面、打开控制中心均恢复原生 **126 像素**高度；回到小红书画幅后恢复 **90 像素**。桌面状态栏恢复原生透明模式。
- 顶部重复缩放修正在加入紧凑样式前实测，将 126 像素状态栏的多余预留从约 153 收回约 127 物理像素；紧凑样式继续沿用该补偿逻辑。
- 折展策略在加入紧凑样式前，使用模块自身页面实测一次 **外屏 16:9 → 内屏默认全屏 → 折回恢复 16:9**；内屏应用范围为完整的 1672×2364，折回范围与展开前一致，该轮画幅配置文件校验值未变化。相关折展逻辑之后未改动；最终集成版内外屏样式切换仍需更多实测。
- APK 签名、对齐、入口和作用域检查通过，APK 中不包含 Xposed API 实现。
- 34 项可独立运行的策略检查通过。结合本地提取的固件 XML，总计 117 项检查通过，覆盖上述 61 条应用规则与 21 条页面规则。
- 22 个独立主机回归场景通过，覆盖方法注册、版本限制、目标缺失、失败撤回与重试、折展设置保留、边距补偿及状态栏外观策略。主机模型不能替代手机实测，详见 [测试说明](tests/README.md)。
- **尚未完成全部应用、相机扫码、游戏或多轮复杂切换的系统性验证。** 本版本仍为测试版本；设置为 GitHub Latest 是为了提供首页 Releases 入口，不代表所有场景已经验证。

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

- APK 输出到 `dist/FoldAspectUnlock-0.2.0.apk`，同时生成 `.sha256` 文件。
- 首次构建在 `.local/` 生成本地签名密钥。请保留密钥以便后续更新，**不要提交或分享**。
- 自行构建的签名与 Releases 中的签名通常不同，不能直接覆盖另一签名的安装包。
- Xposed API 仅用于编译，不会打包进 APK；来源和许可证见 [libs/SOURCE.txt](libs/SOURCE.txt)。

有合法取得的匹配固件策略 XML 时，可额外运行设备规则检查：

```powershell
.\test.ps1 -FirmwarePolicy 'C:\path\to\wide_fold_size_compat.xml'
```

仓库不包含厂商 APK/JAR、反编译源码、手机日志、账户资料或签名密钥。
