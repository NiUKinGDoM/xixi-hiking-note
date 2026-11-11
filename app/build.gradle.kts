plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // ★Kotlin 2.x：Compose 编译器改为独立插件（替换已废弃的 composeOptions）
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.xixi.hiking.nativeapp"   // ★与现有 App（com.xixi.hiking）区分，两个可同时安装
    compileSdk = 36

    defaultConfig {
        applicationId = "com.xixi.hiking.nativeapp"
        minSdk = 24            // Android 7.0 —— Compose 模糊/动效完整支持
        targetSdk = 36

        // ★★版本号规则（2026-11-02 用户定死）：`1.x.x.x`，只看 versionCode 推
        //   索引   = vc - 1
        //   第四段 = 索引 % 11
        //   第三段 = (索引 / 11) % 11
        //   第二段 = (索引 / 121) % 11
        //   每段 0~10 共 11 个值，满 10 进位
        // ⇒ 以后**只改 versionCode 这一行**，版本名自动算出来，不用手抄。
        val vc = 131
        val idx = vc - 1
        versionCode = vc
        versionName = "1.${(idx / 121) % 11}.${(idx / 11) % 11}.${idx % 11}"
    }

    buildTypes {
        release {
            // ★★★2026-11-05 开启 R8（混淆 + 删未用代码 + 删未用资源）
            //
            //   之前一直关着，理由是"Compose + Room 的规则要单独调"。现在功能稳定了，
            //   而且新加了 `tools/obfcheck.py` 来盯"混淆有没有把关键类/资源删掉"，
            //   才敢开。
            //
            //   收益：体积（大头是 material-icons-extended 里没用到的图标、
            //        未使用的 Compose 代码、字符串常量池）
            //   代价：崩溃堆栈变成 a.a.a —— 靠 proguard-rules.pro 里的
            //        `-keepattributes SourceFile,LineNumberTable` + mapping.txt 还原，
            //        所以 **每次发版必须把 mapping.txt 存下来**（obfcheck 会检查）。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // ★用 debug 签名，方便直接安装体验（正式上架再换 release keystore）
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }

    buildFeatures {
        compose = true
        buildConfig = true   // ★AGP 8 默认关闭，SettingsScreen 要用 BuildConfig.VERSION_NAME
    }
    // ★Kotlin 2.x 起不再需要 composeOptions（编译器由 kotlin.plugin.compose 提供）

    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    // ★BOM 2025.11.01 → Compose UI 1.9.5（backdrop 库的 runtime 依赖版本，必须对齐）
    val composeBom = platform("androidx.compose:compose-bom:2025.11.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Room：替代 IndexedDB + localStorage
    // ★升到 2.8.5：KSP 2 下 Room 2.6.x 的编译器不工作（KSP2 改了 processor API）
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    // 图片加载（替代 <img> + dataURL）
    implementation("io.coil-kt:coil-compose:2.5.0")

    // ★2026-11-10 读照片的拍摄时间（选完照片自动填日期）。
    //   版本锁 1.3.6：本机 Gradle 缓存里就有这一份（Coil 的传递依赖），
    //   离线构建能直接用，不用联网拉包。
    implementation("androidx.exifinterface:exifinterface:1.3.6")

    // WebDAV 同步（对齐 Web 版的坚果云同步）
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // ★★液态玻璃：Kyant0/backdrop（Compose 版 Liquid Glass 事实标准）
    //   原理：把「背景的副本」画到前景并叠效果 —— blur / lens(折射) / 色散 / 高光 / 阴影。
    //   比自己手搓 AGSL 强在：效果链由库统一编排、自动能力降级、坐标对齐不用自己算。
    //   minSdk 21（库自带），API 33+ 才有实时折射。
    implementation("io.github.kyant0:backdrop:1.0.1")

    // ★Haze 仅剩 HazeState 这个空壳类型（glassSource 是空实现，早已不参与渲染）。
    //   各页面的 hazeState 参数正在清理，清完这行一起删。
    implementation("dev.chrisbanes.haze:haze:0.7.3")
    implementation("dev.chrisbanes.haze:haze-materials:0.7.3")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // ★仅用于「无设备自测」：Robolectric 在 JVM 上跑真实 Compose 树 + 渲染
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
