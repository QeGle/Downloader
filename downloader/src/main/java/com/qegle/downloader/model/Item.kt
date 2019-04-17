package com.qegle.downloader.model

import com.qegle.downloader.*
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.observers.DisposableObserver
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import java.io.File

/**
 * Created by Sergey Makhaev on 30.10.2017.
 */

class Item(val id: String, val url: String, var meta: Meta) {
	constructor(id: String, url: String, loadingFolder: String) : this(id, url, Meta(loadingFolder))

	var timingListener: TimingListener? = null
	var status: LoadStatus = LoadStatus.PAUSE
	var progressSubject: PublishSubject<Int> = PublishSubject.create()
	var errorSubject: PublishSubject<Pair<ErrorType, String>> = PublishSubject.create()
	private var loader: DownloadFile? = null
	private var progress = 0
	private var loadingDis: Disposable? = null

	internal fun download(onSuccess: () -> Unit) {
		if (status == LoadStatus.IN_PROGRESS) stop()

		loadingDis = Observable.create<Int> {
			loader = DownloadFile(this,
				onSuccess = { url: String, loading: Long, fileSize: Long ->
					timingListener?.onLoading(url, loading, fileSize)
					it.onComplete()
				},
				onProgress = { progress -> it.onNext(progress) },
				onError = { type, message -> it.tryOnError(LoadingException(type, message)) })
			status = LoadStatus.IN_PROGRESS
			loader?.load()
		}
			.subscribeOn(Schedulers.io())
			.observeOn(Schedulers.io())
			.subscribeWith(object : DisposableObserver<Int?>() {
				override fun onComplete() {
					status = LoadStatus.COMPLETE
					onSuccess.invoke()
				}

				override fun onNext(progress: Int) {
					this@Item.progress = progress
					progressSubject.onNext(progress)
				}

				override fun onError(e: Throwable) {
					status = LoadStatus.ERROR
					if (e is LoadingException)
						errorSubject.onNext(Pair(e.type, e.message ?: ""))
					else
						errorSubject.onNext(Pair(ErrorType.UNKNOWN, e.message ?: ""))
				}
			})
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
		loadingDis?.dispose()
		loader?.stopLoading()
		loader = null
		status = LoadStatus.CANCEL
	}

	override fun toString(): String {
		val folder = File(meta.savingFolder, meta.namePrefix + (meta.fileName ?: id) + meta.namePostfix)
		return "Item(url=$url, path=${folder.path}, id=$id)"
	}

	fun isExist(): Boolean {
		val folder = File(meta.savingFolder)
		if (folder.exists() && folder.isDirectory) {
			val x = folder.listFiles()?.find {
				it.nameWithoutExtension == meta.namePrefix + (meta.fileName ?: id) + meta.namePostfix
			}

			return x != null
		}
		return false
	}
}

class LoadingException(val type: ErrorType, message: String) : Exception(message)