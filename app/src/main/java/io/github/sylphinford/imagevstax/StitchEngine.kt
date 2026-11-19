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
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.content.Context
import kotlin.math.roundToInt

/**
 * 图片拼接引擎：支持水平、垂直、网格、字幕四种模式。
 */
object StitchEngine {

    enum class Mode { HORIZONTAL, VERTICAL, GRID, SUBTITLE }

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
     * @param customWidth  垂直 / 字幕模式自定义宽度（px），<=0 表示自动
     * @param cropStart    字幕模式：从第 2 张起的裁切起点（占图片高度的百分比）
     * @param cropHeight   字幕模式：从第 2 张起的裁切高度（占图片高度的百分比）
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
        cropStart: Int = 65,
        cropHeight: Int = 30,
    ): StitchResult {
        val bitmaps = uris.map { decodeUri(context, it) }.filter { it != null }.map { it!! }
        if (bitmaps.size < 2) {
            throw IllegalArgumentException("至少需要 2 张图片")
        }

        val result: Bitmap = when (mode) {
            Mode.HORIZONTAL -> stitchHorizontal(bitmaps, gap, customHeight)
            Mode.VERTICAL -> stitchVertical(bitmaps, gap, customWidth)
            Mode.GRID -> stitchGrid(bitmaps, columns.coerceAtLeast(2), gap)
            Mode.SUBTITLE -> stitchSubtitle(bitmaps, gap, customWidth, cropStart, cropHeight)
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

    /** 字幕模式下的一张输出片段：源图上的裁切区域 + 缩放后的目标高度。 */
    private data class SubtitlePiece(
        val source: Bitmap,
        val srcTop: Int,
        val srcHeight: Int,
        val dstHeight: Int,
    )

    /**
     * 电影台词拼接（字幕模式）：
     * 第 1 张完整保留作为画面，第 2 张起只裁取同一段字幕区域
     * （从图片高度的 [cropStart]% 开始、高 [cropHeight]%），
     * 全部统一缩放到 [targetWidth] 后自上而下排列。
     *
     * 之所以按百分比而不是固定像素裁切，是因为不同截图的分辨率可能不一致，
     * 用百分比才能让每条字幕落在相对一致的位置。
     *
     * @param customWidth 输出宽度（px），<=0 时取第 1 张的宽度
     */
    private fun stitchSubtitle(
        bitmaps: List<Bitmap>,
        gap: Int,
        customWidth: Int,
        cropStart: Int,
        cropHeight: Int,
    ): Bitmap {
        val targetWidth = if (customWidth > 0) customWidth else bitmaps.first().width

        // 百分比收敛：起点最多 99%，高度至少 1%，且两者之和不超过 100%
        val startPct = cropStart.coerceIn(0, 99)
        val heightPct = cropHeight.coerceIn(1, 100 - startPct)

        val pieces = bitmaps.mapIndexed { index, bmp ->
            val srcTop: Int
            val srcHeight: Int
            if (index == 0) {
                // 首张是完整画面，不裁切
                srcTop = 0
                srcHeight = bmp.height
            } else {
                srcTop = bmp.height * startPct / 100
                // 取整可能得到 0，至少保留 1px，否则目标高度会变成 0
                srcHeight = (bmp.height * heightPct / 100).coerceAtLeast(1)
            }
            val dstHeight = (srcHeight.toFloat() * targetWidth / bmp.width).roundToInt()
            SubtitlePiece(bmp, srcTop, srcHeight, dstHeight.coerceAtLeast(1))
        }

        val totalHeight = pieces.sumOf { it.dstHeight } + gap * (pieces.size - 1)
        val result = Bitmap.createBitmap(targetWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.TRANSPARENT)

        // 裁剪 + 缩放一次画完，不额外生成中间位图；开启双线性过滤避免细字发虚
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        var y = 0
        for (piece in pieces) {
            val src = Rect(0, piece.srcTop, piece.source.width, piece.srcTop + piece.srcHeight)
            val dst = Rect(0, y, targetWidth, y + piece.dstHeight)
            canvas.drawBitmap(piece.source, src, dst, paint)
            y += piece.dstHeight + gap
        }
        return result
    }

    /**
     * 用已经解码好的位图拼一张字幕模式预览（间距固定 0）。
     *
     * 与正式拼接共用 [stitchSubtitle]，所以预览里的裁切比例、缩放关系与最终结果一致；
     * 拖动滑条时只需重新组合这张小图，不必重新解码原图。
     *
     * @return 少于 2 张时返回 null
     */
    fun composeSubtitlePreview(
        bitmaps: List<Bitmap>,
        targetWidth: Int,
        cropStart: Int,
        cropHeight: Int,
    ): Bitmap? = if (bitmaps.size < 2) {
        null
    } else {
        stitchSubtitle(bitmaps, 0, targetWidth, cropStart, cropHeight)
    }

    /**
     * 按目标宽度解码：用 `inSampleSize` 降采样到接近 [targetWidth]，
     * 避免只为一张小预览就把整张原图读进内存。
     */
    fun decodeSampled(context: Context, uri: Uri, targetWidth: Int): Bitmap? {
        return try {
            // 只读尺寸：这一趟 decodeStream 按设计返回 null，不能拿返回值判断成败
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            if (bounds.outWidth <= 0) return null

            var sample = 1
            while (bounds.outWidth / (sample * 2) >= targetWidth) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
        } catch (e: Exception) {
            null
        }
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
