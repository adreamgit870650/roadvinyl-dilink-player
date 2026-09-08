# 音焰：Fan AI Studio 正式签名构建

三个 Release 使用当前同一份源码和同一包名 `com.car.mp3player`，均包含最新歌词和兼容性修复；老车机专版使用独立的兼容签名。

| 安装包 | 最低 Android | API |
| --- | --- | --- |
| standard-android7-release | 7.0 | 24 |
| compat-android5.1-release | 5.1 | 22 |
| legacy-car-api19-release | 4.4 | 19 |

常规证书主体：`CN=Fan AI Studio, OU=Yinyan Release, O=Fan AI Studio`。
老车机证书主体：`CN=Fan AI Studio, OU=Yinyan Legacy Car, O=Fan AI Studio`。
签名描述在证书里，不会修改桌面上的应用名称“音焰”。所有包都不是 Debug 构建。

## 本机打包

在项目根目录运行（Gradle 8.2+、JDK 17+，本机已配置 Gradle 8.10.2）：

```powershell
.\scripts\Build-Releases.ps1 -Gradle '<Gradle安装目录>\bin\gradle.bat' -BuildToolsPath '<Android SDK>\build-tools\35.0.0'
```

如需使用本机 Gradle 镜像，可额外传入 `-InitScript '<镜像脚本路径>'`。脚本依次运行两个安装门槛的 Release 单元测试、Lint、编译、签名、校验。
输出在 `app/build/outputs/fan-ai-studio/`。

直接执行 `assembleRelease` 仍只产生未签名 APK，不会偷偷使用 Debug 密钥。
参数 `-PyinyanMinSdk=24` 选择正式版门槛，`-PyinyanMinSdk=22` 选择兼容版门槛；未指定时维持原有 API 22 调试配置。
现有 GitHub 工作流继续生成未签名包，不会取得本机正式密钥。

老车机专版使用 `Build-Legacy-Car-Release.ps1` 单独构建：最低 API 19、目标 API 23、开启 R8，并采用仅含 v1/v2 的旧式签名结构。其 v1 内容摘要和证书签名使用 SHA-1，只为兼容封闭的旧车机系统，不应作为应用商店或现代 Android 设备的首选版本。它使用独立的 `fan-ai-studio-legacy-car.p12`，不能与另外两个正式包互相覆盖。

## 密钥保护与备份（重要）

- `.signing/fan-ai-studio-release.p12` 是正式签名密钥，alias 为 `yinyan-release`。
- `.signing/fan-ai-studio-legacy-car.p12` 是老车机专用签名密钥，alias 为 `yinyan-legacy-car`。
- `.signing/password.dpapi` 是密钥密码的 Windows DPAPI 加密保存，只能由当前电脑上的当前 Windows 账户解密。
- `.signing/` 已加入 Git 忽略，不得上传到 GitHub、云构建或与 APK 一起公开分发。
- 请备份密钥文件，并在换电脑、重装系统前将密码保存到自己的密码管理器。只复制 DPAPI 文件到另一台电脑不足以恢复签名能力。
- 可在本机 Windows PowerShell 中手动执行下列命令，将密码复制到剪贴板后存入密码管理器；不要把终端或剪贴板内容发到聊天、截图或 Git。保存后清空剪贴板。

```powershell
$secure = (Get-Content -LiteralPath '.signing/password.dpapi' -Raw).Trim() | ConvertTo-SecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
    Set-Clipboard -Value ([Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer))
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    $secure.Dispose()
}
# 存入密码管理器后：
# Set-Clipboard -Value ''
```

## 覆盖安装限制

此前本地 Debug 包和旧 `app-release-signed.apk` 均使用 Android Debug 证书。
本次 Fan AI Studio 证书是新的正式签名，不能直接覆盖那些旧签名包；必须先自行备份需要的数据，再处理旧包安装冲突。
老车机专版也有自己的独立证书，后续升级必须继续使用同一个老车机密钥。
脚本只生成 APK，不会卸载应用、不清空设备数据。
这两个新 Release 同签名、同版本号，在满足各自最低系统版本的设备上可互相替换，不能并排安装。
