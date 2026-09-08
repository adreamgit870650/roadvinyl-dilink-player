package com.car.mp3player.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.car.mp3player.R
import com.car.mp3player.data.SettingsRepository
import java.io.File
import java.util.Locale

/** App-owned folder browser for Android versions without ACTION_OPEN_DOCUMENT_TREE. */
class LegacyDirectoryPickerDialog(
    private val context: Context,
    private val onSelected: (String) -> Unit
) {
    private val roots = discoverRoots(context)
    private var currentRoot: File? = null
    private var currentDirectory: File? = null
    private var visibleDirectories: List<File> = emptyList()

    fun create(): AlertDialog {
        val palette = AppThemeManager.palette(context, SettingsRepository(context))
        val padding = dp(18)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, dp(8), padding, 0)
            setBackgroundColor(palette.surface)
        }
        val pathView = TextView(context).apply {
            setTextAppearance(context, android.R.style.TextAppearance_Medium)
            setTextColor(palette.textSecondary)
            setPadding(dp(8), dp(4), dp(8), dp(8))
        }
        val adapter = object : ArrayAdapter<String>(
            context,
            android.R.layout.simple_list_item_1,
            mutableListOf()
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return (super.getView(position, convertView, parent) as TextView).apply {
                    setTextColor(palette.textPrimary)
                    setBackgroundColor(Color.TRANSPARENT)
                }
            }
        }
        val listView = ListView(context).apply {
            this.adapter = adapter
            setBackgroundColor(palette.surface)
            divider = ColorDrawable(
                Color.argb(
                    52,
                    Color.red(palette.textSecondary),
                    Color.green(palette.textSecondary),
                    Color.blue(palette.textSecondary)
                )
            )
            dividerHeight = 1
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(320)
            )
        }
        container.addView(pathView)
        container.addView(listView)

        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.folder_browser_title)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.folder_browser_up, null)
            .setPositiveButton(R.string.folder_browser_select, null)
            .create()

        fun render() {
            val directory = currentDirectory
            visibleDirectories = if (directory == null) roots else childDirectories(directory)
            pathView.text = directory?.absolutePath ?: context.getString(R.string.folder_browser_locations)
            val labels = if (directory == null) {
                visibleDirectories.map { rootLabel(context, it, roots.firstOrNull()) }
            } else {
                visibleDirectories.map { it.name.ifBlank { it.absolutePath } }
            }
            adapter.clear()
            adapter.addAll(if (labels.isEmpty()) listOf(context.getString(R.string.folder_browser_empty)) else labels)
            adapter.notifyDataSetChanged()
            if (dialog.isShowing) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = directory != null
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled = directory != null
            }
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val target = visibleDirectories.getOrNull(position) ?: return@setOnItemClickListener
            if (currentDirectory == null) currentRoot = target
            currentDirectory = target
            render()
        }
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(ColorDrawable(palette.surface))
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(palette.primary)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(palette.primary)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(palette.textSecondary)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val directory = currentDirectory ?: return@setOnClickListener
                val root = currentRoot
                if (root == null || sameFile(directory, root)) {
                    currentDirectory = null
                    currentRoot = null
                } else {
                    currentDirectory = directory.parentFile?.takeIf { isWithin(it, root) } ?: root
                }
                render()
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val selected = currentDirectory ?: return@setOnClickListener
                val canonical = runCatching { selected.canonicalFile }.getOrNull()
                if (canonical == null || !canonical.isDirectory || !canonical.canRead()) return@setOnClickListener
                onSelected(canonical.absolutePath)
                dialog.dismiss()
            }
            render()
        }
        return dialog
    }

    private fun childDirectories(directory: File): List<File> = runCatching {
        directory.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isDirectory && it.canRead() }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            .toList()
    }.getOrDefault(emptyList())

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    companion object {
        private val excludedMountNames = setOf("asec", "obb", "secure", "runtime", "self", "emulated", "legacy")

        private fun discoverRoots(context: Context): List<File> {
            val candidates = mutableListOf<File>()
            runCatching { Environment.getExternalStorageDirectory() }.getOrNull()?.let(candidates::add)
            context.getExternalFilesDirs(null).forEach { externalFiles ->
                storageRootFromAppDirectory(externalFiles)?.let(candidates::add)
            }
            listOf(
                "/sdcard", "/mnt/sdcard", "/storage/sdcard0", "/storage/sdcard1",
                "/mnt/extsd", "/mnt/external_sd", "/mnt/usb_storage",
                "/storage/usb_storage", "/storage/udisk"
            ).mapTo(candidates, ::File)
            listOf(File("/storage"), File("/mnt")).forEach { base ->
                base.listFiles().orEmpty()
                    .filterTo(candidates) { child ->
                        child.isDirectory && child.canRead() &&
                            child.name.lowercase(Locale.US) !in excludedMountNames
                    }
            }
            val unique = linkedMapOf<String, File>()
            candidates.forEach { candidate ->
                val canonical = runCatching { candidate.canonicalFile }.getOrNull() ?: return@forEach
                if (canonical.isDirectory && canonical.canRead() && !unique.containsKey(canonical.absolutePath)) {
                    unique[canonical.absolutePath] = canonical
                }
            }
            return unique.values.toList()
        }

        private fun storageRootFromAppDirectory(directory: File?): File? {
            var current = directory ?: return null
            while (current.parentFile != null) {
                if (current.name.equals("Android", ignoreCase = true)) return current.parentFile
                current = current.parentFile
            }
            return null
        }

        private fun rootLabel(context: Context, root: File, primary: File?): String {
            val path = root.absolutePath
            val lower = path.lowercase(Locale.US)
            val label = when {
                primary != null && sameFile(root, primary) -> context.getString(R.string.folder_browser_internal)
                "usb" in lower || "udisk" in lower -> context.getString(R.string.folder_browser_usb)
                "sdcard1" in lower || "extsd" in lower || "external_sd" in lower ->
                    context.getString(R.string.folder_browser_sd)
                else -> context.getString(R.string.folder_browser_storage)
            }
            return "$label  ($path)"
        }

        private fun sameFile(first: File, second: File): Boolean = runCatching {
            first.canonicalPath == second.canonicalPath
        }.getOrDefault(first.absolutePath == second.absolutePath)

        private fun isWithin(candidate: File, root: File): Boolean = runCatching {
            val candidatePath = candidate.canonicalPath
            val rootPath = root.canonicalPath
            candidatePath == rootPath || candidatePath.startsWith(rootPath + File.separator)
        }.getOrDefault(false)
    }
}
