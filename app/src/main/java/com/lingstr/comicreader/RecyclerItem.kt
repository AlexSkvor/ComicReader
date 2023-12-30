package com.lingstr.comicreader

import android.net.Uri

sealed interface RecyclerItem {

    data class Image(val uri: Uri) : RecyclerItem
}