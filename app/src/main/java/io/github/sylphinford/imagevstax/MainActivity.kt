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

import android.content.ContentValues
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RawRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var rvImages: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var btnAdd: MaterialButton
    private lateinit var btnClear: MaterialButton
    private lateinit var toggleMode: MaterialButtonToggleGroup
    private lateinit var btnHorizontal: MaterialButton
    private lateinit var btnVertical: MaterialButton
    private lateinit var btnGrid: MaterialButton
    private lateinit var toggleFormat: MaterialButtonToggleGroup
    private lateinit var btnFormatJpg: MaterialButton
    private lateinit var btnFormatPng: MaterialButton
    private lateinit var btnFormatWebp: MaterialButton

    // 参数行：每个参数一个按钮，按钮内显示「标签 + 当前值」，点击弹出滑条对话框
    private lateinit var btnParamColumns: MaterialButton
    private lateinit var btnParamGap: MaterialButton
    private lateinit var btnParamHeight: MaterialButton
    private lateinit var btnParamWidth: MaterialButton
    private lateinit var btnParamQuality: MaterialButton

    private lateinit var btnStitch: MaterialButton
    private lateinit var ivResult: ImageView
    private lateinit var resultActions: LinearLayout
    private lateinit var btnSave: MaterialButton
    private lateinit var btnShare: MaterialButton

    private lateinit var adapter: ImageListAdapter

    private val imageUris = mutableListOf<Uri>()
    private var resultBitmap: Bitmap? = null

    // 拼接参数、输出格式与质量统一存在 AppSettings 里，改动即落盘，下次启动自动恢复
    private lateinit var settings: AppSettings

    /** 主题主色，用于给按钮里的「当前值」上色；随主题（浅色/深色）解析一次即可。 */
    private val accentColor: Int by lazy {
        val typed = TypedValue()
        if (theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typed, true)) {
            if (typed.resourceId != 0) ContextCompat.getColor(this, typed.resourceId) else typed.data
        } else {
            Color.GRAY
        }
    }

    private val pickImagesLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                try {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) { }
            }
            addUris(uris)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // targetSdk 35 起 Android 15 强制 edge-to-edge，这里在所有版本上统一采用同一
        // 行为，并由 applyWindowInsets() 自行给系统栏留出占位。
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        settings = AppSettings(this)
        bindViews()
        setupAdapter()
        setupListeners()
        applyWindowInsets()
        updateUI()

        handleSendIntent(intent)
    }

    /**
     * 处理 edge-to-edge 下的系统栏占位：
     * - 状态栏高度加到 AppBarLayout 的 top padding，紫色 AppBar 背景因此延伸到状态栏
     *   区域，标题不会再被状态栏的时间、电量等图标压住；
     * - 左右安全区（刘海屏）与底部导航栏高度加到根布局，避免内容被遮挡。
     */
    private fun applyWindowInsets() {
        val root = findViewById<View>(R.id.root)
        val appBar = findViewById<View>(R.id.appBar)

        // 状态栏区域由紫色 AppBar 背景填充，图标固定用白色，
        // 避免 DayNight 浅色模式下深色图标压在紫底上看不清。
        WindowCompat.getInsetsController(window, root).isAppearanceLightStatusBars = false

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val bars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            appBar.updatePadding(top = bars.top)
            view.updatePadding(left = bars.left, right = bars.right, bottom = bars.bottom)
            windowInsets
        }
        ViewCompat.requestApplyInsets(root)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSendIntent(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_about -> {
                showAboutDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * 「关于」对话框：展示版权与许可摘要，并可进一步查看
     * Apache 2.0 许可证全文与 NOTICE 第三方声明。
     *
     * 这样做是为了满足 Apache License 2.0 第 4(a) 条——
     * 分发目标码（APK）时须随附许可证副本。
     */
    private fun showAboutDialog() {
        val versionName = try {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }

        val message = buildString {
            append(getString(R.string.about_version, versionName))
            append("\n")
            append(getString(R.string.about_copyright))
            append("\n\n")
            append(getString(R.string.about_license_summary))
            append("\n\n")
            append(getString(R.string.about_third_party))
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.about_title)
            .setMessage(message)
            .setPositiveButton(R.string.about_view_license) { _, _ ->
                showRawTextDialog(getString(R.string.about_view_license), R.raw.license)
            }
            .setNeutralButton(R.string.about_view_notice) { _, _ ->
                showRawTextDialog(getString(R.string.about_view_notice), R.raw.notice)
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    /** 以可滚动、可选中、等宽字体的对话框展示 res/raw 下的纯文本许可证文件。 */
    private fun showRawTextDialog(title: String, @RawRes resId: Int) {
        val body = try {
            resources.openRawResource(resId).bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            ""
        }

        val padding = (16 * resources.displayMetrics.density).toInt()
        val textView = TextView(this).apply {
            text = body
            setTextIsSelectable(true)
            typeface = Typeface.MONOSPACE
            textSize = 10f
            setPadding(padding, padding, padding, padding)
        }
        val scrollView = ScrollView(this).apply { addView(textView) }

        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(scrollView)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun bindViews() {
        toolbar = findViewById(R.id.toolbar)
        rvImages = findViewById(R.id.rvImages)
        tvEmpty = findViewById(R.id.tvEmpty)
        btnAdd = findViewById(R.id.btnAdd)
        btnClear = findViewById(R.id.btnClear)
        toggleMode = findViewById(R.id.toggleMode)
        btnHorizontal = findViewById(R.id.btnHorizontal)
        btnVertical = findViewById(R.id.btnVertical)
        btnGrid = findViewById(R.id.btnGrid)
        toggleFormat = findViewById(R.id.toggleFormat)
        btnFormatJpg = findViewById(R.id.btnFormatJpg)
        btnFormatPng = findViewById(R.id.btnFormatPng)
        btnFormatWebp = findViewById(R.id.btnFormatWebp)

        btnParamColumns = findViewById(R.id.btnParamColumns)
        btnParamGap = findViewById(R.id.btnParamGap)
        btnParamHeight = findViewById(R.id.btnParamHeight)
        btnParamWidth = findViewById(R.id.btnParamWidth)
        btnParamQuality = findViewById(R.id.btnParamQuality)

        btnStitch = findViewById(R.id.btnStitch)
        ivResult = findViewById(R.id.ivResult)
        resultActions = findViewById(R.id.resultActions)
        btnSave = findViewById(R.id.btnSave)
        btnShare = findViewById(R.id.btnShare)

        setSupportActionBar(toolbar)
        // Toolbar 背景是 colorPrimary（紫色），溢出菜单三点图标默认取
        // colorControlNormal，在浅色主题下会偏暗，统一染成白色保证可见。
        toolbar.overflowIcon?.setTint(Color.WHITE)

        // 恢复上次使用的模式与输出格式。此处监听器尚未挂载，不会触发回调。
        when (settings.stitchMode) {
            StitchEngine.Mode.VERTICAL -> btnVertical.isChecked = true
            StitchEngine.Mode.GRID -> btnGrid.isChecked = true
            StitchEngine.Mode.HORIZONTAL -> btnHorizontal.isChecked = true
        }
        when (settings.outputFormat) {
            OutputFormat.JPG -> btnFormatJpg.isChecked = true
            OutputFormat.WEBP -> btnFormatWebp.isChecked = true
            OutputFormat.PNG -> btnFormatPng.isChecked = true
        }

        updateParamLabels()
        updateParamVisibility()
    }

    private fun setupAdapter() {
        adapter = ImageListAdapter(
            this,
            onMoveUp = { pos -> moveItem(pos, pos - 1) },
            onMoveDown = { pos -> moveItem(pos, pos + 1) },
            onRemove = { pos -> removeItem(pos) },
        )
        rvImages.layoutManager = LinearLayoutManager(this)
        rvImages.adapter = adapter
    }

    private fun setupListeners() {
        btnAdd.setOnClickListener {
            try {
                pickImagesLauncher.launch(arrayOf("image/*"))
            } catch (e: ActivityNotFoundException) {
                // 精简系统或定制 ROM 可能没有系统文档选择器，避免直接崩溃
                Toast.makeText(this, R.string.no_file_picker, Toast.LENGTH_SHORT).show()
            }
        }

        btnClear.setOnClickListener {
            imageUris.clear()
            adapter.setItems(emptyList())
            updateUI()
        }

        toggleMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            settings.stitchMode = when (checkedId) {
                R.id.btnVertical -> StitchEngine.Mode.VERTICAL
                R.id.btnGrid -> StitchEngine.Mode.GRID
                else -> StitchEngine.Mode.HORIZONTAL
            }
            // 参数行里只保留当前模式用得上的按钮
            updateParamVisibility()
        }

        // 输出格式选择
        toggleFormat.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            settings.outputFormat = when (checkedId) {
                R.id.btnFormatJpg -> OutputFormat.JPG
                R.id.btnFormatWebp -> OutputFormat.WEBP
                else -> OutputFormat.PNG
            }
            // PNG 无损，质量参数无意义，隐藏对应按钮避免误解
            updateParamVisibility()
        }

        // 参数按钮：点击弹出带滑条的浮窗，确定后写入 AppSettings 并刷新按钮文案
        btnParamColumns.setOnClickListener {
            showValueDialog(
                title = getString(R.string.columns),
                valueFrom = AppSettings.COLUMNS_MIN,
                valueTo = AppSettings.COLUMNS_MAX,
                initial = settings.gridColumns,
                formatter = { it.toString() },
                onConfirm = { settings.gridColumns = it; updateParamLabels() },
            )
        }

        btnParamGap.setOnClickListener {
            showValueDialog(
                title = getString(R.string.gap),
                valueFrom = AppSettings.GAP_MIN,
                valueTo = AppSettings.GAP_MAX,
                initial = settings.gapPx,
                formatter = { getString(R.string.value_px, it) },
                onConfirm = { settings.gapPx = it; updateParamLabels() },
            )
        }

        btnParamHeight.setOnClickListener {
            showValueDialog(
                title = getString(R.string.height),
                valueFrom = 0,
                valueTo = AppSettings.SIZE_MAX,
                initial = settings.customHeight,
                formatter = { formatAutoOrPx(it) },
                onConfirm = { settings.customHeight = it; updateParamLabels() },
                onReset = { settings.customHeight = 0; updateParamLabels() },
            )
        }

        btnParamWidth.setOnClickListener {
            showValueDialog(
                title = getString(R.string.width),
                valueFrom = 0,
                valueTo = AppSettings.SIZE_MAX,
                initial = settings.customWidth,
                formatter = { formatAutoOrPx(it) },
                onConfirm = { settings.customWidth = it; updateParamLabels() },
                onReset = { settings.customWidth = 0; updateParamLabels() },
            )
        }

        btnParamQuality.setOnClickListener {
            showValueDialog(
                title = getString(R.string.quality),
                valueFrom = AppSettings.QUALITY_MIN,
                valueTo = AppSettings.QUALITY_MAX,
                initial = settings.outputQuality,
                formatter = { it.toString() },
                onConfirm = { settings.outputQuality = it; updateParamLabels() },
            )
        }

        btnStitch.setOnClickListener {
            performStitch()
        }

        btnSave.setOnClickListener {
            resultBitmap?.let { bmp ->
                // 编码在 IO 线程进行，先固化当前选择的格式与质量，避免过程中被改动
                val format = settings.outputFormat
                val quality = settings.outputQuality
                lifecycleScope.launch {
                    val saved = withContext(Dispatchers.IO) { saveToGallery(bmp, format, quality) }
                    runOnUiThread {
                        val msg = if (saved) {
                            getString(R.string.saved_to_format, format.displayName)
                        } else {
                            getString(R.string.save_failed)
                        }
                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        btnShare.setOnClickListener {
            resultBitmap?.let { bmp ->
                val format = settings.outputFormat
                val quality = settings.outputQuality
                lifecycleScope.launch {
                    val uri = withContext(Dispatchers.IO) { saveToCache(bmp, format, quality) }
                    if (uri != null) {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = format.mimeType
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(
                            Intent.createChooser(shareIntent, getString(R.string.share_image))
                        )
                    }
                }
            }
        }
    }

    /** 0 显示为「自动」，其余显示为「Npx」。 */
    private fun formatAutoOrPx(value: Int): String =
        if (value <= 0) getString(R.string.auto) else getString(R.string.value_px, value)

    /**
     * 参数按钮文案：标签用常规字重，当前值加粗并取主题主色，
     * 这样一眼能看出哪个是当前值。
     */
    private fun setParamLabel(button: MaterialButton, labelRes: Int, value: String) {
        val label = getString(labelRes)
        val text = SpannableStringBuilder(label).append(' ').append(value)
        val start = label.length + 1
        text.setSpan(
            StyleSpan(Typeface.BOLD), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        text.setSpan(
            ForegroundColorSpan(accentColor), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        button.text = text
    }

    private fun updateParamLabels() {
        setParamLabel(btnParamColumns, R.string.columns, settings.gridColumns.toString())
        setParamLabel(btnParamGap, R.string.gap, getString(R.string.value_px, settings.gapPx))
        setParamLabel(btnParamHeight, R.string.height, formatAutoOrPx(settings.customHeight))
        setParamLabel(btnParamWidth, R.string.width, formatAutoOrPx(settings.customWidth))
        setParamLabel(btnParamQuality, R.string.quality, settings.outputQuality.toString())
    }

    /** 按当前拼接模式与输出格式决定参数行里哪些按钮可见。 */
    private fun updateParamVisibility() {
        btnParamColumns.visibility =
            if (settings.stitchMode == StitchEngine.Mode.GRID) View.VISIBLE else View.GONE
        btnParamHeight.visibility =
            if (settings.stitchMode == StitchEngine.Mode.HORIZONTAL) View.VISIBLE else View.GONE
        btnParamWidth.visibility =
            if (settings.stitchMode == StitchEngine.Mode.VERTICAL) View.VISIBLE else View.GONE
        btnParamQuality.visibility =
            if (settings.outputFormat.isLossy) View.VISIBLE else View.GONE
        // 间距三种模式都适用
        btnParamGap.visibility = View.VISIBLE
    }

    /**
     * 通用数值设置浮窗：标题 + 当前值预览 + 滑条。
     *
     * 拖动滑条只改浮窗内的预览文字，点「确定」才写回真实值，
     * 点「取消」保持原值不变。[onReset] 非空时额外提供一个重置按钮（如「自动」）。
     */
    private fun showValueDialog(
        title: String,
        valueFrom: Int,
        valueTo: Int,
        initial: Int,
        formatter: (Int) -> String,
        onConfirm: (Int) -> Unit,
        onReset: (() -> Unit)? = null,
    ) {
        val density = resources.displayMetrics.density
        val padH = (24 * density).toInt()
        val padTop = (8 * density).toInt()
        val gap = (8 * density).toInt()

        val preview = TextView(this).apply {
            text = formatter(initial)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(accentColor)
            gravity = Gravity.CENTER
        }

        val slider = Slider(this).apply {
            // 必须先设 valueTo 再设 valueFrom，否则 valueFrom >= 默认 valueTo 会抛异常
            this.valueTo = valueTo.toFloat()
            this.valueFrom = valueFrom.toFloat()
            stepSize = 1f
            value = initial.coerceIn(valueFrom, valueTo).toFloat()
            addOnChangeListener { _, v, _ -> preview.text = formatter(v.toInt()) }
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padH, padTop, padH, 0)
            addView(preview, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            addView(slider, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = gap })
        }

        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(container)
            .setPositiveButton(R.string.ok) { _, _ -> onConfirm(slider.value.toInt()) }
            .setNegativeButton(R.string.cancel, null)

        if (onReset != null) {
            builder.setNeutralButton(R.string.auto) { _, _ -> onReset() }
        }
        builder.show()
    }

    private fun handleSendIntent(intent: Intent?) {
        intent ?: return
        val uris = mutableListOf<Uri>()
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                uri?.let { uris.add(it) }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                list?.let { uris.addAll(it) }
            }
        }
        if (uris.isNotEmpty()) {
            addUris(uris)
            intent.action = null
        }
    }

    private fun addUris(uris: List<Uri>) {
        for (uri in uris) {
            if (!imageUris.contains(uri)) {
                imageUris.add(uri)
            }
        }
        adapter.setItems(imageUris)
        updateUI()
    }

    private fun moveItem(from: Int, to: Int) {
        if (from !in imageUris.indices || to !in imageUris.indices) return
        val item = imageUris.removeAt(from)
        imageUris.add(to, item)
        adapter.setItems(imageUris)
        updateUI()
    }

    private fun removeItem(pos: Int) {
        if (pos !in imageUris.indices) return
        imageUris.removeAt(pos)
        adapter.setItems(imageUris)
        updateUI()
    }

    private fun updateUI() {
        tvEmpty.visibility = if (imageUris.isEmpty()) View.VISIBLE else View.GONE
        rvImages.visibility = if (imageUris.isEmpty()) View.GONE else View.VISIBLE
        btnStitch.isEnabled = imageUris.size >= 2
    }

    private fun performStitch() {
        if (imageUris.size < 2) return

        // 拼接在后台线程跑，先把参数固化成局部变量，避免过程中被改动
        val mode = settings.stitchMode
        val columns = settings.gridColumns
        val gap = settings.gapPx
        val height = settings.customHeight
        val width = settings.customWidth

        btnStitch.text = getString(R.string.stitch_running)
        btnStitch.isEnabled = false

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    StitchEngine.stitch(
                        this@MainActivity,
                        imageUris,
                        mode,
                        columns,
                        gap,
                        height,
                        width,
                    )
                } catch (e: Exception) {
                    null
                }
            }

            btnStitch.text = getString(R.string.stitch_start)
            btnStitch.isEnabled = true

            if (result != null) {
                resultBitmap = result.bitmap
                ivResult.setImageBitmap(result.bitmap)
                resultActions.visibility = View.VISIBLE
            } else {
                Toast.makeText(this@MainActivity, R.string.stitch_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 按指定格式保存到相册 `Pictures/ImageVStax`。
     * 编码失败时删掉刚插入的 MediaStore 记录，避免相册里留下 0 字节的空条目。
     */
    private fun saveToGallery(bitmap: Bitmap, format: OutputFormat, quality: Int): Boolean {
        val resolver = contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, ImageExporter.buildFileName(format))
            put(MediaStore.Images.Media.MIME_TYPE, format.mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ImageVStax")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(collection, contentValues) ?: return false

        val encoded = try {
            resolver.openOutputStream(uri)?.use { out ->
                ImageExporter.encode(bitmap, format, out, quality)
            } ?: false
        } catch (e: Exception) {
            false
        }

        if (!encoded) {
            runCatching { resolver.delete(uri, null, null) }
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }
        return true
    }

    /** 按指定格式写入缓存目录，供分享使用。 */
    private fun saveToCache(bitmap: Bitmap, format: OutputFormat, quality: Int): Uri? {
        return try {
            val file = File(cacheDir, "ImageVStax_share.${format.extension}")
            val encoded = FileOutputStream(file).use { out ->
                ImageExporter.encode(bitmap, format, out, quality)
            }
            if (!encoded) return null
            FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        } catch (e: Exception) {
            null
        }
    }
}
