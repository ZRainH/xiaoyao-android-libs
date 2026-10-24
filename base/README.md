# base — Activity / Fragment / ViewModel 基类 + Hilt 公共能力

依赖本模块的宿主工程若使用 Hilt，需自行完成：

1. 根工程 / 宿主 `build.gradle` 引入插件（版本与工程 catalog 对齐）：
   - `com.google.dagger.hilt.android`
   - `com.google.devtools.ksp`

2. 宿主 application 模块：
```kotlin
plugins {
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}
dependencies {
    implementation(project(":base")) // 已 api 传递 hilt-android
    ksp("com.google.dagger:hilt-compiler:<version>") // 宿主自己的 @Inject / @HiltViewModel 仍需 ksp
}
```

3. Application：
```kotlin
@HiltAndroidApp
class App : Application()
```
Manifest：`android:name=".App"`

4. 页面 / ViewModel：
```kotlin
@AndroidEntryPoint
class XxxActivity : BaseActivity() { ... }

@HiltViewModel
class XxxViewModel @Inject constructor(...) : BaseViewModel<...>()
```

说明：
- `@HiltAndroidApp` 只能写在宿主 Application，不要写在 base。
- `BaseActivity` / `BaseFragment` 不加 `@AndroidEntryPoint`，由具体子类标注。
- `com.tennis.base.di.BaseModule` 为跨项目公共绑定预留；业务 Module 放在各自工程。
