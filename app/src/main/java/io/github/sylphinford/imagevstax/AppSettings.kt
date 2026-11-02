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

import android.content.Context

/**
 * 上次使用的设置，落盘到 SharedPreferences。
 *
 * 属性直接读写 prefs，赋值即持久化，不需要额外的 save 调用。
 * 读取时统一做范围收敛：即使外部改坏了 prefs（或将来参数范围变了），
 * 也不会把越界值喂给滑条或拼接引擎。
 */
class AppSettings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var stitchMode: StitchEngine.Mode
        get() = prefs.getString(KEY_MODE, null)
            ?.let { name -> StitchEngine.Mode.entries.firstOrNull { it.name == name } }
            ?: StitchEngine.Mode.HORIZONTAL
        set(value) = prefs.edit().putString(KEY_MODE, value.name).apply()

    var outputFormat: OutputFormat
        get() = prefs.getString(KEY_FORMAT, null)
            ?.let { name -> OutputFormat.entries.firstOrNull { it.name == name } }
            ?: OutputFormat.PNG
        set(value) = prefs.edit().putString(KEY_FORMAT, value.name).apply()

    var gridColumns: Int
        get() = prefs.getInt(KEY_COLUMNS, DEFAULT_COLUMNS).coerceIn(COLUMNS_MIN, COLUMNS_MAX)
        set(value) = prefs.edit()
            .putInt(KEY_COLUMNS, value.coerceIn(COLUMNS_MIN, COLUMNS_MAX)).apply()

    var gapPx: Int
        get() = prefs.getInt(KEY_GAP, DEFAULT_GAP).coerceIn(GAP_MIN, GAP_MAX)
        set(value) = prefs.edit().putInt(KEY_GAP, value.coerceIn(GAP_MIN, GAP_MAX)).apply()

    /** 0 表示自动取原图尺寸。 */
    var customHeight: Int
        get() = prefs.getInt(KEY_HEIGHT, 0).coerceIn(0, SIZE_MAX)
        set(value) = prefs.edit().putInt(KEY_HEIGHT, value.coerceIn(0, SIZE_MAX)).apply()

    /** 0 表示自动取原图尺寸。 */
    var customWidth: Int
        get() = prefs.getInt(KEY_WIDTH, 0).coerceIn(0, SIZE_MAX)
        set(value) = prefs.edit().putInt(KEY_WIDTH, value.coerceIn(0, SIZE_MAX)).apply()

    var outputQuality: Int
        get() = prefs.getInt(KEY_QUALITY, DEFAULT_QUALITY).coerceIn(QUALITY_MIN, QUALITY_MAX)
        set(value) = prefs.edit()
            .putInt(KEY_QUALITY, value.coerceIn(QUALITY_MIN, QUALITY_MAX)).apply()

    /** 清空全部已保存设置，回到默认值。 */
    fun reset() = prefs.edit().clear().apply()

    companion object {
        private const val FILE_NAME = "imagevstax_settings"

        private const val KEY_MODE = "stitch_mode"
        private const val KEY_FORMAT = "output_format"
        private const val KEY_COLUMNS = "grid_columns"
        private const val KEY_GAP = "gap_px"
        private const val KEY_HEIGHT = "custom_height"
        private const val KEY_WIDTH = "custom_width"
        private const val KEY_QUALITY = "output_quality"

        // 参数范围集中在这里，滑条与存储共用，避免两处各写一份
        const val COLUMNS_MIN = 2
        const val COLUMNS_MAX = 6
        const val GAP_MIN = 0
        const val GAP_MAX = 50
        const val SIZE_MAX = 4096
        const val QUALITY_MIN = 60
        const val QUALITY_MAX = 100

        private const val DEFAULT_COLUMNS = 2
        private const val DEFAULT_GAP = 0
        private const val DEFAULT_QUALITY = 95
    }
}
