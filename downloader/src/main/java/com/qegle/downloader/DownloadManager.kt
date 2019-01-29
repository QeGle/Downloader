package com.qegle.downloader

import android.content.SharedPreferences
import androidx.core.content.edit
import com.qegle.downloader.model.Pack
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.locks.ReentrantLock

class DownloadManager(private val sharedPreferences: SharedPreferences, private val tempFolder: String) {

	private val loadingArray = arrayListOf<Pack>()
	private var listener: DownloadListener? = null
	private var onProgressUpdate: OnProgressUpdate? = null
	private val arrayLock = ReentrantLock()

	fun setOnProgressUpdate(listener: (id: String, value: Int) -> Unit) {
		this.onProgressUpdate = object : OnProgressUpdate {
			override fun progressUpdate(id: String, value: Int) {
				listener.invoke(id, value)
			}
		}
	}

	fun setDownloadListener(
		onLoadSuccess: (id: String) -> Unit = {},
		onLoadError: (id: String, message: String) -> Unit = { _, _ -> },
		onUnzipError: (id: String, message: String) -> Unit = { _, _ -> }
	) {
		this.listener = object : DownloadListener {
			override fun downloadSuccess(id: String) = onLoadSuccess.invoke(id)
			override fun downloadError(id: String, message: String) = onLoadError.invoke(id, message)
			override fun unzipError(id: String, message: String) = onUnzipError.invoke(id, message)
		}
	}

	fun download(pack: Pack) {
		if (isDownloaded(pack))
			loadSuccess(pack)
		else
			downloadWithoutCheck(pack)
	}

	fun downloadWithoutCheck(newPack: Pack) {
		arrayLock.lock()
		sharedPreferences.edit(commit = true) { remove(newPack.id) }

		val pack = loadingArray.firstOrNull { it.id == newPack.id }

		loadingArray.forEach { it.pause() }
		if (pack == null) {
			newPack.progressSubject
				.subscribeOn(Schedulers.io())
				.observeOn(Schedulers.io())
				.doOnNext { onProgressUpdate?.progressUpdate(newPack.id, it) }
				.subscribe()

			newPack.errorSubject
				.subscribeOn(Schedulers.io())
				.observeOn(Schedulers.io())
				.doOnNext { error ->
					when (error.first) {
						ErrorType.LOAD -> listener?.downloadError(newPack.id, error.second)
						ErrorType.ZIP -> listener?.unzipError(newPack.id, error.second)
					}
					loadError(newPack)
				}
				.subscribe()
			loadingArray.add(newPack)
			newPack.tempFolder = tempFolder
			newPack.download { loadSuccess(newPack) }
		} else {
			pack.resume()
		}
		arrayLock.unlock()
	}

	private fun loadSuccess(pack: Pack) {
		arrayLock.lock()
		sharedPreferences.edit(commit = true) { putString(pack.id, UPLOADED) }
		removePack(pack)
		listener?.downloadSuccess(pack.id)
		loadingArray.firstOrNull()?.let { it.download { loadSuccess(it) } }
		arrayLock.unlock()
	}

	private fun loadError(pack: Pack) {
		arrayLock.lock()
		sharedPreferences.edit(commit = true) { remove(pack.id) }
		removePack(pack)
		loadingArray.firstOrNull()?.let { it.download { loadSuccess(it) } }
		arrayLock.unlock()
	}

	private fun removePack(pack: Pack) {
		loadingArray.remove(pack)
	}

	fun isDownloaded(pack: Pack): Boolean {
		return isLoadedSuccess(pack.id) && pack.isFilesExist()
	}

	fun isLoadedSuccess(id: String): Boolean {
		return sharedPreferences.getString(id, "") == UPLOADED
	}

	fun destroy() {
		arrayLock.lock()
		loadingArray.forEach { it.stop() }
		arrayLock.unlock()
	}

}

const val DOWNLOADED_FILES = "DOWNLOADED_FILES"
const val UPLOADED = "UPLOADED"

enum class LoadStatus { IN_PROGRESS, PAUSE, ERROR, COMPLETE, CANCEL }

enum class ErrorType { LOAD, ZIP }

interface DownloadListener {

	fun downloadSuccess(id: String)
	fun downloadError(id: String, message: String)
	fun unzipError(id: String, message: String)
}

interface OnProgressUpdate {

	fun progressUpdate(id: String, value: Int)
}