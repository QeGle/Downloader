package com.qegle.downloader

import android.content.SharedPreferences
import androidx.core.content.edit
import com.qegle.downloader.extensions.withTempFolder
import com.qegle.downloader.extensions.withTimingListener
import com.qegle.downloader.model.Pack
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.observers.DisposableObserver
import io.reactivex.rxkotlin.addTo
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.locks.ReentrantLock

class DownloadManager(private val sharedPreferences: SharedPreferences, private val tempFolder: String,
                      private val timingListener: TimingListener? = null) {

	private val loadingArray = mutableListOf<Pack>()

	var onLoadSuccess: (id: String) -> Unit = {}
	var onLoadError: (id: String, message: String) -> Unit = { _, _ -> }
	var onUnzipError: (id: String, message: String) -> Unit = { _, _ -> }
	var onUnknownError: (id: String, message: String) -> Unit = { _, _ -> }

	var onProgressUpdate: (id: String, progress: Int) -> Unit = { _, _ -> }

	private val arrayLock = ReentrantLock()

	fun setDownloadListener(
		onLoadSuccess: (id: String) -> Unit = {},
		onLoadError: (id: String, message: String) -> Unit = { _, _ -> },
		onUnzipError: (id: String, message: String) -> Unit = { _, _ -> },
		onUnknownError: (id: String, message: String) -> Unit = { _, _ -> }
	) {
		this.onLoadSuccess = onLoadSuccess
		this.onLoadError = onLoadError
		this.onUnzipError = onUnzipError
		this.onUnknownError = onUnknownError
	}

	fun download(pack: Pack) {
		if (isDownloaded(pack))
			loadSuccess(pack)
		else
			downloadWithoutCheck(pack)
	}

	val compositeDisposable = CompositeDisposable()

	fun downloadWithoutCheck(newPack: Pack) {
		arrayLock.lock()
		sharedPreferences.edit(commit = true) { remove(newPack.id) }

		val pack = loadingArray.firstOrNull { it.id == newPack.id }

		loadingArray.forEach { it.pause() }
		if (pack == null) {
			newPack.progressSubject
				.subscribeOn(Schedulers.io())
				.observeOn(Schedulers.io())
				.subscribeWith(object : DisposableObserver<Int?>() {
					override fun onComplete() {}

					override fun onNext(progress: Int) {
						onProgressUpdate.invoke(newPack.id, progress)
					}

					override fun onError(e: Throwable) {}
				}).addTo(compositeDisposable)

			newPack.errorSubject
				.subscribeOn(Schedulers.io())
				.observeOn(Schedulers.io())
				.subscribeWith(object : DisposableObserver<Pair<ErrorType, String>?>() {
					override fun onComplete() {}

					override fun onNext(error: Pair<ErrorType, String>) {
						when (error.first) {
							ErrorType.LOAD -> onLoadError.invoke(newPack.id, error.second)
							ErrorType.ZIP -> onUnzipError.invoke(newPack.id, error.second)
							ErrorType.UNKNOWN -> onUnknownError.invoke(newPack.id, error.second)
						}
						loadError(newPack)
					}

					override fun onError(e: Throwable) {}
				}).addTo(compositeDisposable)

			loadingArray.add(newPack)
			newPack.withTempFolder(tempFolder)
			timingListener?.let { newPack.withTimingListener(it) }
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
		onLoadSuccess.invoke(pack.id)
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
		compositeDisposable.clear()
	}

}

const val DOWNLOADED_FILES = "DOWNLOADED_FILES"
const val UPLOADED = "UPLOADED"

enum class LoadStatus { IN_PROGRESS, PAUSE, ERROR, COMPLETE, CANCEL }

enum class ErrorType { LOAD, ZIP, UNKNOWN }

interface TimingListener {
	/**
	 * @param url - url файла
	 * @param loading - время загрузки в милисекундах
	 * @param fileSize - размер файла в байтах
	 */
	fun onLoading(url: String, loading: Long, fileSize: Long)
}