# RoadVinyl CloudBase 更新服务

公开仓库不包含真实 CloudBase 环境 ID、存储桶地址或 APK 文件 ID。部署步骤：

1. 将 `cloudbaserc.example.json` 复制为 `cloudbaserc.json`，填写自己的环境 ID。
2. 为云函数设置环境变量 `ROADVINYL_APK_FILE_ID`，值为你自己的 `cloud://...` APK 文件 ID。
3. 在 `cloudfunctions/roadVinylUpdate/index.js` 中填写正式 APK 的 SHA-256 和文件大小。
4. 部署函数和 HTTPS 路由，将得到的 `version.json` 地址通过构建属性
   `ROADVINYL_UPDATE_VERSION_URL` 注入 Android 应用。

正式发布时必须使用与车机已安装版本一致的签名证书。客户端会同时校验大小与
SHA-256，校验失败时不会拉起安装器。不要提交 `cloudbaserc.json`、签名文件或真实
部署地址。
