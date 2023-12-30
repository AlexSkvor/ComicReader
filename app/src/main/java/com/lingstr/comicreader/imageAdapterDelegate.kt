package com.lingstr.comicreader

import androidx.appcompat.widget.AppCompatImageView
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegate

fun imageAdapterDelegate() = adapterDelegate<RecyclerItem.Image, RecyclerItem>(R.layout.item_image) {

    val imageView = findViewById<AppCompatImageView>(R.id.image)

    bind {
        imageView.setImageURI(item.uri)
    }
}