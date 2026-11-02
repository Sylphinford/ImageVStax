/*
 * Copyright 2026 sylphinford
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.sylphinford.imagevstax

import android.graphics.Bitmap
import android.os.Build

/**
 * 拼接结果的输出编码格式。
 *
 * 三者的取舍：
 * - [JPG]  有损压缩，体积最小；不支持 alpha 通道，拼接间隙会被填成白色
 * - [PNG]  无损压缩，支持透明；体积最大，适合保留画质
 * - [WEBP] 有损压缩，支持透明；通常比同画质 JPG 更小
 */
enum class OutputFormat(
    /** 界面上展示的短名称 */
    val displayName: String,
    /** 文件扩展名（不含点） */
    val extension: String,
    /** MIME 类型，用于 MediaStore 与分享 Intent */
    val mimeType: String,
    /** 默认编码质量，PNG 为无损压缩该值无意义，固定 100 */
    val defaultQuality: Int,
) {
    JPG("JPG", "jpg", "image/jpeg", 95),
    PNG("PNG", "png", "image/png", 100),
    WEBP("WEBP", "webp", "image/webp", 95);

    /** JPEG 不支持 alpha 通道，编码前必须先把透明区域合成为白色底。 */
    val supportsAlpha: Boolean
        get() = this != JPG

    /**
     * 是否为有损压缩。
     * 只有有损格式的质量参数才影响输出，PNG 无损，调质量没有意义。
     * 注意与 [supportsAlpha] 区分：WEBP 有损但支持透明。
     */
    val isLossy: Boolean
        get() = this != PNG

    /**
     * 对应的 [Bitmap.CompressFormat]。
     * API 30 起 `WEBP` 被拆分为 `WEBP_LOSSY` / `WEBP_LOSSLESS`，低版本仍需用旧常量。
     */
    @get:Suppress("DEPRECATION")
    val compressFormat: Bitmap.CompressFormat
        get() = when (this) {
            JPG -> Bitmap.CompressFormat.JPEG
            PNG -> Bitmap.CompressFormat.PNG
            WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                Bitmap.CompressFormat.WEBP
            }
        }
}
