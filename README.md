# 小星记账助手-截图录入

一个独立的 Android 截图记账工具：从当前屏幕、系统最近截图或相册分享图片中离线 OCR，提取账单字段后，通过小星记账的 `xxjz://api/dialog` 直接打开自动记账弹窗。

本项目不是小星记账官方组件，不读取或修改小星记账的内部数据库，也不会跳过小星记账自己的编辑与保存流程。

## 三种录入方式

### 实时截图磁贴

1. 在小星记账中进入“侧边栏 → 设置 → 扩展功能”，开启 URL Scheme。
2. 打开本助手，在设置中开启持续截图授权并添加“截图记账”磁贴。
3. 停留在支付结果页，下拉并点击磁贴。
4. 助手截图、OCR 后直接打开小星记账自动记账弹窗。

一次屏幕捕获授权可在前台服务存活期间反复使用。手机重启、强制停止、系统回收服务或用户停止屏幕共享后，Android 会要求重新授权。

### 最近截图磁贴

用户先用系统截图，再点击“识别最近截图”磁贴。该入口不使用屏幕捕获授权，只读取系统媒体库中最近一张截图：

- Android 13 及以上：申请图片媒体权限 `READ_MEDIA_IMAGES`。
- Android 14 及以上：支持系统“仅选择的照片”授权；若最新截图未在授权范围内，需要改为允许全部照片或使用相册分享。
- Android 12 及以下：申请 `READ_EXTERNAL_STORAGE`。
- 不申请 `MANAGE_EXTERNAL_STORAGE`，不读取视频或其他普通文件。

### 从相册分享

在系统相册中选择账单图片，点击“分享 → 识别图片并记账”。相册通过 `ACTION_SEND` 临时授予这一张图片的读取权，因此无需授予整个相册权限。

## 自动填入小星记账

金额识别成功后不会停留在助手确认页，而是直接调用小星记账自动记账弹窗。助手尽量传递该接口支持的全部字段：

- `type`
- `amount`
- `shop`
- `account`
- `account2`
- `time`
- `remark`
- `channel`

真实来源 App 优先作为 `channel` 传递；来源未知时使用 OCR 识别到的微信、支付宝、手机银行等渠道。备注中也会追加“来源：渠道”。小星的弹窗接口没有 `tag` 参数，但 `channel` 正是其“自动添加交易渠道标签”功能使用的字段。

跳转前必须匹配到支付、收款、转账、交易、订单、账单等账单关键词，避免把容量、版本号等普通数字误当金额。命中关键词但没有识别出金额时进入校正页兜底；完全没有账单特征时只提示“未识别到账单”，不会打开小星或生成 0 元账单。

接口文档：<https://cxincx.com/support/scheme.html>

## 外部自动化接口

应用公开两个 Activity Action，可由 MacroDroid、Tasker、Automate、NFC/传感器自动化或 ADB 调用。

### 截取当前屏幕并记账

```bash
am start \
  -a com.xingledger.quickcapture.action.CAPTURE_AND_BOOKKEEP \
  -n com.xingledger.quickcapture/.CaptureActivity \
  --es extra_source_app "支付宝" \
  --es extra_source_package "com.eg.android.AlipayGphone"
```

`extra_source_app` 和 `extra_source_package` 均为可选参数。未传来源时，助手会尝试读取前台 App；若未授予可选的“使用情况访问权限”，则使用 OCR 渠道推断。

持续截图服务尚未授权或授权已失效时，这个 Action 会正常显示 Android 系统屏幕捕获确认框。

### 识别系统最近截图并记账

```bash
am start \
  -a com.xingledger.quickcapture.action.RECENT_SCREENSHOT_AND_BOOKKEEP \
  -n com.xingledger.quickcapture/.ImportImageActivity
```

外部应用也可以直接发送显式 Intent，无需通过 Shell。Shell 是否需要 Root、Shizuku 或 ADB 权限取决于所用自动化工具；这不是本助手额外要求的权限。

## 识别与隐私

- 使用打包在 APK 内的 ML Kit 中文文字识别模型，可离线 OCR。
- 直接分析图片像素，不依赖 WebView DOM、无障碍节点或原生控件结构。
- OCR Bitmap 在识别结束后释放。
- 只有匹配到账单关键词的图片才会保存在应用私有目录；未识别到账单的私有副本立即删除。
- 最多保留 128 张有效截图，按时间新到旧排列；第 129 张会自动淘汰最旧记录。
- 每张截图保存 OCR 文字索引，可搜索 App、包名、商户、金额及任意 OCR 文字，并可按来源 App 筛选。
- 打开助手可查看截图时间、来源 App、识别渠道和 OCR 摘要；左右滑动或点击删除即可同时移除图片及元数据。
- APK 明确移除了 `INTERNET` 和 `ACCESS_NETWORK_STATE` 权限，无法上传图片。
- 使用 Android `FLAG_SECURE`、DRM 等安全保护的页面可能得到黑屏，不在支持范围内。
- 无障碍权限不能合法绕过系统屏幕捕获授权，本项目不使用这种方式截图。

## 截图黑名单

“设置与授权 → 管理截图黑名单”可按已安装 App 开关拦截。实时截图磁贴或当前屏幕外部 Action 触发后，助手会先判断前台 App；命中黑名单时直接提示并退出，图片像素不会被读取或保存。

自动识别前台 App 依赖可选的“使用情况访问权限”。外部 Action 若显式传入 `extra_source_package`，也能直接参与黑名单判断。最近截图和相册分享属于用户明确选择图片的流程，不受当前前台 App 黑名单影响。

## 系统要求

- Android 8.0（API 26）或更高版本。
- 小星记账 3.5.0 或更高版本，并开启 URL Scheme。
- 实时截图模式需要 Android MediaProjection 前台服务，因此授权有效期间会显示常驻通知。
- “使用情况访问权限”只用于标记真实来源 App，可不授权。

## 构建

项目使用 Java 17、Android Gradle Plugin 9.2.1、compileSdk 36：

```bash
export ANDROID_HOME=/path/to/android-sdk
./gradlew testDebugUnitTest lintDebug assembleDebug
```

调试 APK 输出到：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 模拟账单

`test-assets/mock-bills/` 包含支付宝支出、微信收款和银行卡转账三张 1080×1920 测试图：

```bash
python3 tools/generate_mock_bills.py
```

## 开源协议

MIT License。小星记账、微信、支付宝等名称和商标归各自权利人所有。
