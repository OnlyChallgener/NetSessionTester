# Labprobe 固定签名配置

Android 覆盖安装要求 **包名相同 + 签名证书相同 + versionCode 不降低**。
如果 GitHub Actions 每次使用不同 debug key，手机就会提示无法安装或必须卸载重装。

本工程已支持固定签名：`debug` 和 `release` 都可以使用同一把 `labprobeUpload` keystore。

## 重要提醒

如果你手机里已安装的是旧随机签名 APK，第一次切换到固定签名时，Android 仍然不能覆盖旧包，可能需要卸载一次。
卸载并安装固定签名版后，以后只要继续使用同一把 keystore，并递增 `versionCode`，就可以直接覆盖安装。

## 1. 生成 keystore

在电脑本地执行：

```bash
mkdir -p keystore
keytool -genkeypair -v \
  -keystore keystore/labprobe-upload.jks \
  -alias labprobe \
  -keyalg RSA \
  -keysize 2048 \
  -validity 36500
```

建议记住你设置的 keystore 密码和 key 密码。

## 2. 本地打包使用

复制配置模板：

```bash
cp signing.properties.example signing.properties
```

然后修改 `signing.properties`：

```properties
LABPROBE_KEYSTORE_PATH=keystore/labprobe-upload.jks
LABPROBE_KEYSTORE_PASSWORD=你的keystore密码
LABPROBE_KEY_ALIAS=labprobe
LABPROBE_KEY_PASSWORD=你的key密码
```

本地构建：

```bash
gradle :app:assembleDebug :app:assembleRelease --stacktrace
```

## 3. GitHub Actions 使用

把 keystore 转成 Base64。

macOS / Linux：

```bash
base64 -w 0 keystore/labprobe-upload.jks > labprobe-upload-base64.txt
```

如果 macOS 不支持 `-w`：

```bash
base64 keystore/labprobe-upload.jks | tr -d '\n' > labprobe-upload-base64.txt
```

Windows PowerShell：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("keystore\labprobe-upload.jks")) | Out-File -Encoding ascii labprobe-upload-base64.txt
```

进入 GitHub 仓库：

`Settings -> Secrets and variables -> Actions -> New repository secret`

添加这 4 个 Secrets：

```text
LABPROBE_KEYSTORE_BASE64     = labprobe-upload-base64.txt 里面的整段内容
LABPROBE_KEYSTORE_PASSWORD   = 你的keystore密码
LABPROBE_KEY_ALIAS           = labprobe
LABPROBE_KEY_PASSWORD        = 你的key密码
```

配置完成后，Actions 会同时输出：

```text
app-debug.apk
app-release.apk
```

两个 APK 都会使用同一把固定签名。

## 4. 不要提交这些文件

`.gitignore` 已经排除：

```text
signing.properties
*.jks
*.keystore
keystore/
```

keystore 丢失后无法再覆盖安装旧版本，所以请自己备份。
