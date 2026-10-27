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
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
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
    private lateinit var gridControls: LinearLayout
    private lateinit var sliderColumns: Slider
    private lateinit var sliderGap: Slider
    private lateinit var btnStitch: MaterialButton
    private lateinit var ivResult: ImageView
    private lateinit var resultActions: LinearLayout
    private lateinit var btnSave: MaterialButton
    private lateinit var btnShare: MaterialButton

    // 水平拼接自定义高度控件
    private lateinit var heightControls: LinearLayout
    private lateinit var tvHeightValue: TextView
    private lateinit var sliderHeight: Slider
    private lateinit var btnResetHeight: MaterialButton

    // 垂直拼接自定义宽度控件
    private lateinit var widthControls: LinearLayout
    private lateinit var tvWidthValue: TextView
    private lateinit var sliderWidth: Slider
    private lateinit var btnResetWidth: MaterialButton

    private lateinit var adapter: ImageListAdapter

    private val imageUris = mutableListOf<Uri>()
    private var resultBitmap: Bitmap? = null
    private var stitchMode = StitchEngine.Mode.HORIZONTAL

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
        gridControls = findViewById(R.id.gridControls)
        sliderColumns = findViewById(R.id.sliderColumns)
        sliderGap = findViewById(R.id.sliderGap)
        btnStitch = findViewById(R.id.btnStitch)
        ivResult = findViewById(R.id.ivResult)
        resultActions = findViewById(R.id.resultActions)
        btnSave = findViewById(R.id.btnSave)
        btnShare = findViewById(R.id.btnShare)

        // 自定义高度控件
        heightControls = findViewById(R.id.heightControls)
        tvHeightValue = findViewById(R.id.tvHeightValue)
        sliderHeight = findViewById(R.id.sliderHeight)
        btnResetHeight = findViewById(R.id.btnResetHeight)

        // 自定义宽度控件
        widthControls = findViewById(R.id.widthControls)
        tvWidthValue = findViewById(R.id.tvWidthValue)
        sliderWidth = findViewById(R.id.sliderWidth)
        btnResetWidth = findViewById(R.id.btnResetWidth)

        setSupportActionBar(toolbar)
        // Toolbar 背景是 colorPrimary（紫色），溢出菜单三点图标默认取
        // colorControlNormal，在浅色主题下会偏暗，统一染成白色保证可见。
        toolbar.overflowIcon?.setTint(Color.WHITE)
        btnHorizontal.isChecked = true

        updateHeightLabel()
        updateWidthLabel()
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
            pickImagesLauncher.launch(arrayOf("image/*"))
        }

        btnClear.setOnClickListener {
            imageUris.clear()
            adapter.setItems(emptyList())
            updateUI()
        }

        toggleMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            stitchMode = when (checkedId) {
                R.id.btnHorizontal -> StitchEngine.Mode.HORIZONTAL
                R.id.btnVertical -> StitchEngine.Mode.VERTICAL
                R.id.btnGrid -> StitchEngine.Mode.GRID
                else -> StitchEngine.Mode.HORIZONTAL
            }
            // 根据模式显示/隐藏对应控件
            heightControls.visibility = if (stitchMode == StitchEngine.Mode.HORIZONTAL) View.VISIBLE else View.GONE
            widthControls.visibility = if (stitchMode == StitchEngine.Mode.VERTICAL) View.VISIBLE else View.GONE
            gridControls.visibility = if (stitchMode == StitchEngine.Mode.GRID) View.VISIBLE else View.GONE
        }

        // 自定义高度 Slider
        sliderHeight.addOnChangeListener { _, value, _ ->
            updateHeightLabel()
        }
        btnResetHeight.setOnClickListener {
            sliderHeight.value = 0f
            updateHeightLabel()
        }

        // 自定义宽度 Slider
        sliderWidth.addOnChangeListener { _, value, _ ->
            updateWidthLabel()
        }
        btnResetWidth.setOnClickListener {
            sliderWidth.value = 0f
            updateWidthLabel()
        }

        btnStitch.setOnClickListener {
            performStitch()
        }

        btnSave.setOnClickListener {
            resultBitmap?.let { bmp ->
                lifecycleScope.launch {
                    val saved = withContext(Dispatchers.IO) { saveToGallery(bmp) }
                    runOnUiThread {
                        if (saved) Toast.makeText(this@MainActivity, getString(R.string.saved_to), Toast.LENGTH_SHORT).show()
                        else Toast.makeText(this@MainActivity, "保存失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        btnShare.setOnClickListener {
            resultBitmap?.let { bmp ->
                lifecycleScope.launch {
                    val uri = withContext(Dispatchers.IO) { saveToCache(bmp) }
                    if (uri != null) {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(shareIntent, "分享图片"))
                    }
                }
            }
        }
    }

    private fun updateHeightLabel() {
        val h = sliderHeight.value.toInt()
        tvHeightValue.text = if (h == 0) "自动" else "${h}px"
    }

    private fun updateWidthLabel() {
        val w = sliderWidth.value.toInt()
        tvWidthValue.text = if (w == 0) "自动" else "${w}px"
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

        val columns = sliderColumns.value.toInt()
        val gap = sliderGap.value.toInt()
        val customHeight = sliderHeight.value.toInt()  // 0 = 自动
        val customWidth = sliderWidth.value.toInt()     // 0 = 自动

        btnStitch.text = "拼接中..."
        btnStitch.isEnabled = false

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    StitchEngine.stitch(
                        this@MainActivity,
                        imageUris,
                        stitchMode,
                        columns,
                        gap,
                        customHeight,
                        customWidth,
                    )
                } catch (e: Exception) {
                    null
                }
            }

            btnStitch.text = "开始拼接"
            btnStitch.isEnabled = true

            if (result != null) {
                resultBitmap = result.bitmap
                ivResult.setImageBitmap(result.bitmap)
                resultActions.visibility = View.VISIBLE
            } else {
                Toast.makeText(this@MainActivity, "拼接失败，请检查图片", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveToGallery(bitmap: Bitmap): Boolean {
        val resolver = contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "ImageVStax_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ImageVStax")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(collection, contentValues) ?: return false
        return try {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun saveToCache(bitmap: Bitmap): Uri? {
        return try {
            val file = File(cacheDir, "ImageVStax_share.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        } catch (e: Exception) {
            null
        }
    }
}
