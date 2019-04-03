package com.qegle.downloader.model

import com.qegle.downloader.*
import io.reactivex.subjects.PublishSubject
import java.io.File

/**
 * Created by Sergey Makhaev on 30.10.2017.
 */

class Item(val url: String, val path: String, val id: String, val namePrefix: String = "",
           private val onNewFolder: Boolean = true,
           private val needClearDestinyFolder: Boolean = false,
           private val needClearUnpackFolder: Boolean = false,
           private val overrideName: Boolean = false) {
	constructor(url: String, path: String, namePrefix: String = "", onNewFolder: Boolean = true) : this(url, path, url.id(), namePrefix = namePrefix, onNewFolder = onNewFolder)

	var tempFolder: String = ""
	var timingListener: TimingListener? = null
	var status: LoadStatus = LoadStatus.PAUSE
	var progressSubject: PublishSubject<Int> = PublishSubject.create()
	var errorSubject: PublishSubject<Pair<ErrorType, String>> = PublishSubject.create()
	private var loader: DownloadFile? = null
	var progress = 0

	internal fun download(onSuccess: () -> Unit) {
		if (status == LoadStatus.IN_PROGRESS) stop()
		loader = DownloadFile(path, tempFolder, url, onNewFolder, filename = if (overrideName) id else null, namePrefix = namePrefix,
			needClearDestinyFolder = needClearDestinyFolder, needClearUnpackFolder = needClearUnpackFolder,
			onSuccess = { url: String, loading: Long, fileSize: Long ->
				timingListener?.onLoading(url, loading, fileSize)
				status = LoadStatus.COMPLETE
				onSuccess.invoke()
			},
			onProgress = { progress ->
				this.progress = progress
				progressSubject.onNext(progress)
			},
			onError = { type, message ->
				status = LoadStatus.ERROR
				errorSubject.onNext(Pair(type, message))
			})
		status = LoadStatus.IN_PROGRESS
		loader?.start()
	}

	fun pause() {
		if (status != LoadStatus.IN_PROGRESS) return
		status = LoadStatus.PAUSE
		loader?.isPaused = true
	}

	fun resume() {
		if (status != LoadStatus.PAUSE) return
		status = LoadStatus.IN_PROGRESS
		loader?.isPaused = false
	}

	fun stop() {
		loader?.stopLoading()
		loader = null
		status = LoadStatus.CANCEL
	}

	override fun toString(): String {
		return "Item(url=$url, path=$path, id=$id)"
	}

	fun isExist(): Boolean {
		val folder = File("$path/")
		if (folder.exists() && folder.isDirectory) {
			val x = folder.listFiles()?.find { it.nameWithoutExtension == id }

			return x != null
		}
		return false
	}
}

fun String.id(): String {
	return this.split("/").lastOrNull() ?: ""
}