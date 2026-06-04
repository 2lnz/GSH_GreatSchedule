# GSH课程表 (Great Schedule Helper)

[![Version](https://img.shields.io/badge/version-1.0.1_beta-blue)](https://blog.2lnz.top/GSH_GreatSchedule/)

基于 [WakeUp课程表](https://github.com/YZune/WakeUpSchedule) 开源项目二次开发。

## 项目主页

[https://github.com/2lnz/GSH_GreatSchedule](https://github.com/2lnz/GSH_GreatSchedule)

## 主要特性

- **Material 3 / Jetpack Compose** — 现代化 UI，支持深色模式
- **自动导入课表** — 支持银杏教务系统，一键导入
- **周数显示** — 正确显示单双周课程
- **桌面小部件** — 美观的今日课程桌面组件
- **导出功能** — 支持导出为日历格式
- **公告系统** — 远程公告推送
- **轻量省电** — 无广告、无后台、启动快

## 变更说明

本项目基于 WakeupSchedule_BUPT改编，主要修改：

- 适配银杏学院新版教务系统
- 全面迁移至 Jetpack Compose + Material 3
- 替换默认课程时间为银杏学院作息时间（15 节/天，20 周/学期）
- 新增公告推送功能
- 优化桌面小部件
- 默认关闭自动更新

## 下载

最新 APK 见 [Release](https://github.com/2lnz/GSH_GreatSchedule/releases)

## 技术栈

- **语言**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **架构**: MVVM (ViewModel + LiveData + Room)
- **网络**: Retrofit 2 + OkHttp 3 + jsoup
- **DI**: 手动依赖注入
- **构建**: Gradle (Kotlin DSL)

### 集成的开源库

- [AndroidX](https://developer.android.com/jetpack/androidx)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Material Design 3](https://m3.material.io/)
- [Room](https://developer.android.com/training/data-storage/room)
- [Navigation](https://developer.android.com/guide/navigation)
- [Retrofit 2](https://github.com/square/retrofit)
- [OkHttp 3](https://github.com/square/okhttp)
- [Glide](https://github.com/bumptech/glide)
- [Gson](https://github.com/google/gson)
- [jsoup](https://github.com/jhy/jsoup)
- [Toasty](https://github.com/GrenderG/Toasty)
- [NumberPickerView](https://github.com/Carbs0126/NumberPickerView)
- [BaseRecyclerViewAdapterHelper](https://github.com/CymChad/BaseRecyclerViewAdapterHelper)
- [TextDrawable](https://github.com/jahirfiquitiva/TextDrawable)
- [Android-QuickSideBar](https://github.com/saiwu-bigkoo/Android-QuickSideBar/)
- [sticky-headers-recyclerview](https://github.com/timehop/sticky-headers-recyclerview)
- [biweekly](https://github.com/mangstadt/biweekly)
- [kotlin-csv](https://github.com/doyaaaaaken/kotlin-csv)

## 致谢

- 北邮适配 [王衔飞](https://github.com/wangxianfei2002)

## License

```
Copyright 2019 YZune. https://github.com/YZune
Copyright 2020 xianfei. https://github.com/xianfei
Copyright 2026 2LNZ. https://github.com/2lnz/GSH_GreatSchedule

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
