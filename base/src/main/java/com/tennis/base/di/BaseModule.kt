package com.tennis.base.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * base 公共 DI 入口。
 *
 * 宿主工程依赖 `:base` 并启用 Hilt 后，本 Module 会自动进入 [SingletonComponent]。
 * 业务绑定请放在各自 App / feature 模块；仅把真正跨项目复用的依赖写在这里。
 */
@Module
@InstallIn(SingletonComponent::class)
object BaseModule
