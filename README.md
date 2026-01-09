# SimpleModDetect - Minecraft Fabric Mod 检测插件

## 概述

SimpleModDetect 是一个专为 **Paper 1.21.11** 服务器设计的轻量级 Fabric 模组检测插件。它通过监听 Minecraft 网络数据包来检测客户端使用的 Fabric 模组，并根据配置阻止违规玩家或将其传送到备用服务器。

## 兼容性

- **仅支持 Paper 1.21.11** 服务器
- 支持 Fabric 客户端模组检测
- 需要 Java 17 或更高版本

## 功能特性

### 🔍 模组检测
- 实时监听 `minecraft:register` 和 `minecraft:brand` 数据包
- 自动解析 Fabric 客户端的注册频道信息
- 支持黑名单和白名单两种检测模式

### ⚙️ 灵活配置
- **黑名单模式**：阻止特定模组
- **白名单模式**：只允许特定模组
- **调试模式**：详细日志输出，便于排查问题
- **自定义提示消息**：支持变量替换

### 🔄 Fallback 服务器支持
- 检测到违规模组时自动传送到备用服务器
- 支持 BungeeCord 传送
- 避免直接踢出玩家，提供更好的用户体验

### 📊 管理命令
- 查看玩家模组列表
- 实时重载配置
- 动态切换调试模式
- 设置 Fallback 服务器

## 安装方法

1. 确保服务器运行 **Paper 1.21.11**
2. 将 `SimpleModDetect.jar` 放入 `plugins` 文件夹
3. 重启服务器
4. 修改 `plugins/SimpleModDetect/config.yml` 配置文件
5. 使用 `/simplemoddetect reload` 重载配置
