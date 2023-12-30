package com.lingstr.comicreader

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.view.WindowManager
import android.webkit.MimeTypeMap
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.hannesdorfmann.adapterdelegates4.AsyncListDifferDelegationAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class MainActivity : AppCompatActivity() {

    private val recyclerAdapter by lazy {
        AsyncListDifferDelegationAdapter(
            object : DiffUtil.ItemCallback<RecyclerItem>() {
                override fun areItemsTheSame(oldItem: RecyclerItem, newItem: RecyclerItem): Boolean {
                    return oldItem == newItem
                }

                override fun areContentsTheSame(oldItem: RecyclerItem, newItem: RecyclerItem): Boolean {
                    return oldItem == newItem
                }
            },
            imageAdapterDelegate(),
        ).also { findViewById<RecyclerView>(R.id.recycler).adapter = it }
    }

    private val selectFileView: TextView by lazy { findViewById(R.id.select_file) }

    private val activityLauncher = registerForActivityResult(
        object : ActivityResultContracts.OpenDocument() {
            override fun createIntent(context: Context, input: Array<String>): Intent {
                return super.createIntent(context, input)
                    .putExtra(Intent.EXTRA_LOCAL_ONLY, true)
            }
        }) { readCbrFileFromUri(it) }

    override fun onCreate(savedInstanceState: Bundle?) {

        hideSystemUi()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension("cbr") ?: return

        selectFileView.setOnClickListener {
            activityLauncher.launch(arrayOf(type))
        }
    }

    override fun onResume() {
        hideSystemUi()
        super.onResume()
    }

    private fun readCbrFileFromUri(uri: Uri?) {
        uri ?: return

        lifecycleScope.launch {
            selectFileView.text = "Начинаем распаковку архива"
            val folder = withContext(Dispatchers.IO) { unzip(uri, cacheDir) } ?: return@launch
            renderBook(folder, true)
        }
    }

    private fun renderBook(folder: File, finishedUnzipping: Boolean = false) {
        if (finishedUnzipping) {
            selectFileView.visibility = View.GONE
        }
        recyclerAdapter.items = folder.listFiles()
            .orEmpty()
            .map { RecyclerItem.Image(it.toUri()) }
            .alsoPrintDebug()
    }

    private suspend fun unzip(fromUri: Uri, toFile: File): File? {
        val folder: File?
        ZipInputStream(
            BufferedInputStream(
                contentResolver.openInputStream(fromUri) ?: return null
            )
        ).use { input ->

            var entry = input.nextEntry

            folder = File(toFile, getDisplayFileName(fromUri))

            if (folder.exists()) {
                folder.deleteRecursively()

                if (folder.isDirectory) return folder
                else folder.delete()
            }
            folder.mkdir()
            val buffer = ByteArray(8192)

            var countFiles = 1
            while (entry != null) { // TODO parallel later!
                if (entry.isDirectory) continue // TODO need to use correctly but not now

                unzipEntry(input, entry, folder, buffer)
                withContext(Dispatchers.Main) {
                    selectFileView.text = "Распаковано $countFiles страниц"
                }
                entry = input.nextEntry
                if (countFiles in listOf(1, 10, 50)) {
                    withContext(Dispatchers.Main) { renderBook(folder) }
                }
                countFiles++
            }
        }
        return folder
    }

    private fun unzipEntry(input: ZipInputStream, entry: ZipEntry, folder: File, buffer: ByteArray) {
        val fileName = entry.name
        val file = File(folder, fileName)

        FileOutputStream(file).use { outputStream ->
            var count = input.read(buffer)
            while (count != -1) {
                outputStream.write(buffer, 0, count)
                count = input.read(buffer)
            }
        }
    }

    private fun getDisplayFileName(uri: Uri): String {
        contentResolver.query(uri, null, null, null, null, null)
            .use { cursor ->
                cursor ?: return@use

                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 } ?: return@use
                    return cursor.getString(index)
                }
            }

        return UUID.randomUUID().toString()
    }

    private fun hideSystemUi() {
        window.attributes.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = false
        windowInsetsController.isAppearanceLightNavigationBars = false
        WindowCompat.setDecorFitsSystemWindows(window, false)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }
}