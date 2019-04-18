package com.qegle.downloader.extensions

import com.qegle.downloader.TimingListener
import com.qegle.downloader.model.Pack


/**
 * Добавляет слушателя метрики в каждый item в этом pack'e
 */
fun Pack.withTimingListener(timingListener: TimingListener) {
	this.items.forEach { it.timingListener = timingListener }
}
/**
 * Добавляет директорию для времменого хранения файлов в каждый item в этом pack'e
 */
fun Pack.withTempFolder(tempFolderPath: String) {
	this.items.forEach { it.meta.loadingFolder = tempFolderPath }
}