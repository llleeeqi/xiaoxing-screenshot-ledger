# 小星记账助手-截图录入

一个独立的 Android 截图记账工具：从当前屏幕、系统最近截图或相册分享图片中离线 OCR，提取账单字段后，通过小星记账的 `xxjz://api/dialog` 直接打开自动记账弹窗。

本项目不是小星记账官方组件，不读取或修改小星记账的内部数据库，也不会跳过小星记账自己的编辑与保存流程。

## 三种录入方式

### 实时截图磁贴

1. 在小星记账中进入“侧边栏 → 设置 → 扩展功能”，开启 URL Scheme。
2. 打开本助手，在“设置与授权 → 截图方式”选择无障碍单帧、系统授权或最近截图，并添加“截图记账”磁贴。
3. 停留在支付结果页，下拉并点击磁贴。
4. 助手截图、OCR 后直接打开小星记账自动记账弹窗。

主磁贴和 `CAPTURE_AND_BOOKKEEP` 外部 Action 共用所选模式：

- 无障碍单帧（Android 11+）：首次手动开启系统无障碍服务；每次触发只截一帧，不建立屏幕共享。若系统经常关闭该服务，可手动改选系统授权模式。
- 系统授权截图（Android 8+）：每次触发都显示 Android 自带的屏幕捕获确认框；取得一帧后立即停止 MediaProjection 和前台服务，再在本机 OCR。授权不可复用，系统顶部共享提示可能短暂出现，但不会常驻。
- 识别最近截图：用户先自己用系统截图，再点主磁贴；只需照片读取权限，不请求无障碍或屏幕捕获授权。

独立的“识别最近截图”磁贴始终按第三种方式工作，不受主磁贴模式影响。切换模式不会替用户关闭已启用的无障碍服务，可在系统无障碍设置中手动关闭。

助手不会按截图或订单内容去重；同一页面再次点击会重新识别。只有上一张仍在处理或系统要求的截图间隔太短时，本次触发才会被拒绝。截图完成后会显示识别进度；无法截图、没有读到文字、未匹配到账单或命中黑名单时会明确显示原因。若某个支付页面禁止截图，可改试订单详情或付款记录页。

识别成功会给出短暂提示并直接打开小星记账，不增加确认步骤；需要校正的账单会进入校正页。失败时显示原因和关闭按钮。若磁贴看起来没有响应，可以在“设置与授权 → 导出诊断日志”手动分享日志：仅记录时间、流程阶段、来源 App 包名和系统错误码，不记录图片、OCR 文字或账单字段。日志存放于不会被云备份的应用私有目录，最多约 64 KB；导出副本位于应用缓存，只有主动选择分享目标才会离开本机。

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

真实来源 App 优先作为 `channel` 传递；来源未知时使用 OCR 识别渠道或平台。淘宝、天猫、京东、拼多多、美团等应用会在备注中追加“消费平台：App 名称”；最近截图和相册分享也会从 OCR 文字推断平台。其他来源追加“来源：渠道”。小星的弹窗接口没有 `tag` 参数，但 `channel` 正是其“自动添加交易渠道标签”功能使用的字段。

订单详情页中的“支付方式 / 支付宝支付”按同一横行配对，并把小星账户参数规范为“支付宝”；“支付宝交易号”可在支付方式文字漏识时兜底。`channel` 仍标记购物平台（如淘宝、天猫），不与付款账户混淆。金额优先匹配“实付”所在行，付款时间优先匹配“付款时间”所在行。

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

此 Action 遵循主磁贴选定的模式。选择无障碍单帧时需事先启用服务；选择系统授权时每次弹系统确认框，截到一帧后自动停止共享；选择最近截图时读取相册中的最新系统截图。Android 14+ 不允许复用 MediaProjection 授权令牌，因此系统授权模式无法实现“一次授权永久使用”。

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
- 账单原图及完整 OCR 搜索索引只存于应用私有空间，可能包含订单页上的电话和地址；诊断日志不含这些内容。已关闭 Android 自动备份，并明确排除截图目录的云备份与设备间迁移。删除记录会同时删除图片与 OCR 元数据。
- 最多保留 128 张有效截图，按时间新到旧排列；第 129 张会自动淘汰最旧记录。
- 每张截图保存 OCR 文字索引，可搜索 App、包名、商户、金额及任意 OCR 文字，并可按来源 App 筛选。
- 打开助手可查看截图时间、来源 App、识别渠道和 OCR 摘要；左右滑动或点击删除即可同时移除图片及元数据。
- 某些 Android 系统会限制应用名称查询；对已确认的淘宝、拼多多包名会显示中文名称，旧记录也会在列表中正常展示，包名仍可搜索。
- APK 明确移除了 `INTERNET` 和 `ACCESS_NETWORK_STATE` 权限，无法上传图片。
- 使用 Android `FLAG_SECURE`、DRM 等安全保护的页面可能得到黑屏，不在支持范围内。
- Android 11 及以上使用系统正式提供的无障碍截图 API；服务只订阅窗口切换事件以记录来源 App，不读取无障碍节点或界面控件文字。
- 无障碍截图返回的像素直接进入应用内存，不会生成系统相册文件，因此无需执行可能误删用户照片的清理逻辑。

## 截图黑名单

“设置与授权 → 管理截图黑名单”可按已安装 App 开关拦截。实时截图磁贴或当前屏幕外部 Action 触发后，助手会先判断前台 App；命中黑名单时直接提示并退出，图片像素不会被读取或保存。

Android 11 及以上优先使用无障碍窗口切换事件识别前台 App；兼容模式可选用“使用情况访问权限”。外部 Action 若显式传入 `extra_source_package`，也能直接参与黑名单判断。最近截图和相册分享属于用户明确选择图片的流程，不受当前前台 App 黑名单影响。

## 系统要求

- Android 8.0（API 26）或更高版本。
- 小星记账 3.5.0 或更高版本，并开启 URL Scheme。
- Android 11 及以上可选无障碍单帧截图；Android 8 及以上可选每次授权的 MediaProjection 截图。
- MediaProjection 模式依法显示前台服务通知和系统共享提示，但取得一帧后立即结束，不会常驻。
- “使用情况访问权限”只用于标记真实来源 App，可不授权。

## 构建

项目使用 Java 17、Android Gradle Plugin 9.2.1、compileSdk 36：

```bash
export ANDROID_HOME=/path/to/android-sdk
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```

调试 APK 输出到：

```text
app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
app/build/outputs/apk/debug/app-armeabi-v7a-debug.apk
app/build/outputs/apk/debug/app-x86_64-debug.apk
app/build/outputs/apk/debug/app-universal-debug.apk
```

日常手机优先安装 ARM64 包；x86_64 包供模拟器使用。各架构包都包含完整的离线中文 OCR，万能包仅用于 CPU 架构未知的设备。

`releases/` 提供已签名的安装包，可直接安装；本地签名仅供个人测试，后续公开发行需要固定使用同一把正式签名密钥。ARM64 安装包约 12.5 MB；系统“应用大小”还会计入安装后解压的 OCR 原生库与运行优化文件，数值会高于 APK 文件大小。

## 模拟账单

`test-assets/mock-bills/` 包含支付宝支出、微信收款和银行卡转账三张 1080×1920 测试图：

```bash
python3 tools/generate_mock_bills.py
```

## 开源协议

MIT License。小星记账、微信、支付宝等名称和商标归各自权利人所有。
