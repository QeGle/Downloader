package com.qegle.downloader.extensions

import com.qegle.downloader.TimingListener
import com.qegle.downloader.model.Pack

fun Pack.withTimingListener(timingListener: TimingListener) {
	this.items.forEach { it.timingListener = timingListener }
}

fun Pack.withTempFolder(tempFolderPath: String) {
	this.items.forEach { it.meta.loadingFolder = tempFolderPath }
}