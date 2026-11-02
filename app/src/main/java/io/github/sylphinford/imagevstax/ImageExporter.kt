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
import android.graphics.Canvas
import android.graphics.Color
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 把拼接结果按指定格式编码输出。
 *
 * 与 [StitchEngine] 分开的原因：拼接只产出 Bitmap，格式只影响落盘时的编码，
 * 用户切换格式无需重新拼接。
 */
object ImageExporter {

    /** 生成带时间戳的文件名，例如 `ImageVStax_20261101_183245.webp`。 */
    fun buildFileName(format: OutputFormat): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "ImageVStax_${stamp}.${format.extension}"
    }

    /**
     * 把 Bitmap 编码写入 [out]。
     *
     * JPEG 没有 alpha 通道，透明像素会被编码成黑色，所以先合成到白底上再压缩，
     * 这样拼接间隙才是白色而不是黑色。
     *
     * @param quality 编码质量 0–100；仅对有损格式（JPG / WEBP）生效，PNG 会忽略
     * @return 是否编码成功
     */
    fun encode(
        bitmap: Bitmap,
        format: OutputFormat,
        out: OutputStream,
        quality: Int = format.defaultQuality,
    ): Boolean {
        val source = if (format.supportsAlpha) bitmap else flattenOnWhite(bitmap)
        val safeQuality = quality.coerceIn(0, 100)
        return try {
            source.compress(format.compressFormat, safeQuality, out)
        } finally {
            // 只有为 JPEG 临时创建的白底副本才需要回收，原图归调用方所有
            if (source !== bitmap) source.recycle()
        }
    }

    /** 把带透明通道的图合成到纯白背景上，用于不支持 alpha 的格式。 */
    private fun flattenOnWhite(bitmap: Bitmap): Bitmap {
        val flattened = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(flattened)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        return flattened
    }
}
