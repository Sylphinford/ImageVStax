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
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers

/**
 * 图片列表 Adapter：缩略图展示 + 拖拽排序/上下移/删除。
 */
class ImageListAdapter(
    private val context: Context,
    private val onMoveUp: (Int) -> Unit,
    private val onMoveDown: (Int) -> Unit,
    private val onRemove: (Int) -> Unit,
) : RecyclerView.Adapter<ImageListAdapter.VH>() {

    private val items = mutableListOf<Uri>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun setItems(uris: List<Uri>) {
        items.clear()
        items.addAll(uris)
        notifyDataSetChanged()
    }

    fun getItems(): List<Uri> = items.toList()

    fun moveItem(from: Int, to: Int) {
        if (from !in items.indices || to !in items.indices) return
        val item = items.removeAt(from)
        items.add(to, item)
        notifyItemMoved(from, to)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val uri = items[position]
        holder.tvName.text = uri.lastPathSegment ?: uri.toString()
        holder.btnUp.isEnabled = position > 0
        holder.btnDown.isEnabled = position < items.size - 1

        scope.launch {
            val thumb = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val opts = android.graphics.BitmapFactory.Options().apply {
                            inSampleSize = 4
                        }
                        android.graphics.BitmapFactory.decodeStream(input, null, opts)
                    }
                } catch (e: Exception) { null }
            }
            thumb?.let { holder.ivThumb.setImageBitmap(it) }
        }

        holder.btnUp.setOnClickListener { onMoveUp(holder.bindingAdapterPosition) }
        holder.btnDown.setOnClickListener { onMoveDown(holder.bindingAdapterPosition) }
        holder.btnRemove.setOnClickListener { onRemove(holder.bindingAdapterPosition) }
    }

    override fun getItemCount(): Int = items.size

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val ivThumb: ImageView = view.findViewById(R.id.ivThumb)
        val tvName: TextView = view.findViewById(R.id.tvName)
        val btnUp: ImageButton = view.findViewById(R.id.btnUp)
        val btnDown: ImageButton = view.findViewById(R.id.btnDown)
        val btnRemove: ImageButton = view.findViewById(R.id.btnRemove)
    }
}
