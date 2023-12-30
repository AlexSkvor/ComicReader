package com.lingstr.comicreader

import timber.log.Timber

inline fun <reified T> T.alsoPrintDebug(msg: String = "") = also { Timber.e("alsoPrintDebug $msg .... $this") }