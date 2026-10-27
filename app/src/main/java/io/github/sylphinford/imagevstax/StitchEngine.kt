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
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.content.Context

/**
 * 图片拼接引擎：支持水平、垂直、网格三种模式。
 */
object StitchEngine {

    enum class Mode { HORIZONTAL, VERTICAL, GRID }

    data class StitchResult(
        val bitmap: Bitmap,
        val width: Int,
        val height: Int,
    )

    /**
     * @param context      用于读 Uri
     * @param uris         已排序的图片 Uri 列表（至少 2 张）
     * @param mode         拼接模式
     * @param columns      网格模式列数（HORIZONTAL/VERTICAL 模式忽略）
     * @param gap          图片间距（px）
     * @param customHeight 水平拼接自定义高度（px），<=0 表示自动取最大高度
     * @param customWidth  垂直拼接自定义宽度（px），<=0 表示自动取最大宽度
     * @return              拼接后的 Bitmap
     */
    fun stitch(
        context: Context,
        uris: List<Uri>,
        mode: Mode,
        columns: Int = 2,
        gap: Int = 0,
        customHeight: Int = 0,
        customWidth: Int = 0,
    ): StitchResult {
        val bitmaps = uris.map { decodeUri(context, it) }.filter { it != null }.map { it!! }
        if (bitmaps.size < 2) {
            throw IllegalArgumentException("至少需要 2 张图片")
        }

        val result: Bitmap = when (mode) {
            Mode.HORIZONTAL -> stitchHorizontal(bitmaps, gap, customHeight)
            Mode.VERTICAL -> stitchVertical(bitmaps, gap, customWidth)
            Mode.GRID -> stitchGrid(bitmaps, columns.coerceAtLeast(2), gap)
        }

        return StitchResult(result, result.width, result.height)
    }

    /**
     * 水平拼接：所有图片左右排列，统一高度。
     * @param customHeight 自定义高度（px），<=0 时自动取所有图片中最大高度
     */
    private fun stitchHorizontal(bitmaps: List<Bitmap>, gap: Int, customHeight: Int): Bitmap {
        val targetHeight = if (customHeight > 0) customHeight else bitmaps.maxOf { it.height }
        val scaled = bitmaps.map { scaleToHeight(it, targetHeight) }

        val totalWidth = scaled.sumOf { it.width } + gap * (scaled.size - 1)
        val result = Bitmap.createBitmap(totalWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.TRANSPARENT)

        var x = 0
        for (bmp in scaled) {
            canvas.drawBitmap(bmp, x.toFloat(), 0f, null)
            x += bmp.width + gap
        }
        return result
    }

    /**
     * 垂直拼接：所有图片上下排列，统一宽度。
     * @param customWidth 自定义宽度（px），<=0 时自动取所有图片中最大宽度
     */
    private fun stitchVertical(bitmaps: List<Bitmap>, gap: Int, customWidth: Int): Bitmap {
        val targetWidth = if (customWidth > 0) customWidth else bitmaps.maxOf { it.width }
        val scaled = bitmaps.map { scaleToWidth(it, targetWidth) }

        val totalHeight = scaled.sumOf { it.height } + gap * (scaled.size - 1)
        val result = Bitmap.createBitmap(targetWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.TRANSPARENT)

        var y = 0
        for (bmp in scaled) {
            canvas.drawBitmap(bmp, 0f, y.toFloat(), null)
            y += bmp.height + gap
        }
        return result
    }

    /**
     * 网格拼接：按列数分行排列，每格缩放到统一单元格大小。
     */
    private fun stitchGrid(bitmaps: List<Bitmap>, columns: Int, gap: Int): Bitmap {
        val rows = kotlin.math.ceil(bitmaps.size.toDouble() / columns).toInt()

        // 计算单元格尺寸：取所有图片中最大的宽和高的较小值，保证不裁切
        val maxCellW = bitmaps.maxOf { it.width }
        val maxCellH = bitmaps.maxOf { it.height }
        val cellSize = minOf(maxCellW, maxCellH)

        // 将每张图等比缩放到 cellSize x cellSize（居中放置）
        val cellBmps = bitmaps.map { scaleToSquare(it, cellSize) }

        val totalWidth = cellSize * columns + gap * (columns - 1)
        val totalHeight = cellSize * rows + gap * (rows - 1)
        val result = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.TRANSPARENT)

        for ((index, bmp) in cellBmps.withIndex()) {
            val col = index % columns
            val row = index / columns
            val x = col * (cellSize + gap)
            val y = row * (cellSize + gap)
            canvas.drawBitmap(bmp, x.toFloat(), y.toFloat(), null)
        }
        return result
    }

    private fun scaleToHeight(bmp: Bitmap, targetHeight: Int): Bitmap {
        if (bmp.height == targetHeight) return bmp
        val ratio = targetHeight.toFloat() / bmp.height
        val newWidth = (bmp.width * ratio).toInt()
        return Bitmap.createScaledBitmap(bmp, newWidth, targetHeight, true)
    }

    private fun scaleToWidth(bmp: Bitmap, targetWidth: Int): Bitmap {
        if (bmp.width == targetWidth) return bmp
        val ratio = targetWidth.toFloat() / bmp.width
        val newHeight = (bmp.height * ratio).toInt()
        return Bitmap.createScaledBitmap(bmp, targetWidth, newHeight, true)
    }

    private fun scaleToSquare(bmp: Bitmap, size: Int): Bitmap {
        if (bmp.width == size && bmp.height == size) return bmp
        // 等比缩放后居中绘制到 size×size 画布
        val ratio = minOf(size.toFloat() / bmp.width, size.toFloat() / bmp.height)
        val scaledW = (bmp.width * ratio).toInt()
        val scaledH = (bmp.height * ratio).toInt()
        val scaled = Bitmap.createScaledBitmap(bmp, scaledW, scaledH, true)
        val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.TRANSPARENT)
        val left = (size - scaledW) / 2f
        val top = (size - scaledH) / 2f
        canvas.drawBitmap(scaled, left, top, null)
        return result
    }

    private fun decodeUri(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            }
        } catch (e: Exception) {
            null
        }
    }
}
