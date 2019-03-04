package com.qegle.downloader.model

import com.qegle.downloader.*
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject

/**
 * Created by Sergey Makhaev on 30.10.2017.
 */

class Pack(val id: String, val items: List<Item>) {
	constructor(id: String, item: Item) : this(id, arrayListOf(item))

	internal var tempFolder: String = ""
	var status: LoadStatus = LoadStatus.PAUSE
	var progressSubject: PublishSubject<Int> = PublishSubject.create()
	var errorSubject: PublishSubject<Pair<ErrorType, String>> = PublishSubject.create()
	var currLoadingItem: Item? = null
	var timingListener: TimingListener? = null

	internal fun download(onSuccess: () -> Unit) {
		if (status == LoadStatus.IN_PROGRESS) stop()
		if (items.isEmpty()) {
			success(onSuccess)
			return
		}
		doNext { success(onSuccess) }
	}

	private fun doNext(onSuccess: () -> Unit) {
		currLoadingItem = items.firstOrNull { it.status == LoadStatus.PAUSE }

		if (currLoadingItem == null) {
			success(onSuccess)
			return
		}
		currLoadingItem?.tempFolder = tempFolder
		currLoadingItem?.timingListener = timingListener

		val err = currLoadingItem?.errorSubject
			?.subscribeOn(Schedulers.io())
			?.observeOn(Schedulers.io())
			?.doOnNext { error -> itemError(error.first, error.second) }
			?.doOnError { error -> itemError(ErrorType.LOAD, error.message ?: "") }
			?.subscribe()
		val progress = currLoadingItem?.progressSubject
			?.subscribeOn(Schedulers.io())
			?.observeOn(Schedulers.io())
			?.doOnNext { progress -> itemProgressUpdate(progress) }
			?.doOnError { error -> itemError(ErrorType.LOAD, error.message ?: "") }
			?.subscribe()

		currLoadingItem?.download {
			err?.dispose()
			progress?.dispose()
			doNext(onSuccess)
		}
		status = LoadStatus.IN_PROGRESS

	}

	private fun itemProgressUpdate(progress: Int) {
		val loaded = items.filter { it.status == LoadStatus.COMPLETE }.size.toFloat()
		val all = items.size.toFloat()
		if (all == loaded) {
			progressSubject.onNext(100)
		} else {
			progressSubject.onNext(((progress + loaded * 100) / all).toInt())
		}
	}

	private fun itemError(type: ErrorType, message: String) {
		status = LoadStatus.ERROR
		errorSubject.onNext(Pair(type, message))
		currLoadingItem?.stop()
		currLoadingItem = null
	}

	private fun success(onSuccess: () -> Unit) {
		status = LoadStatus.COMPLETE
		onSuccess.invoke()
	}

	fun pause() {
		if (status != LoadStatus.IN_PROGRESS) return
		status = LoadStatus.PAUSE
		currLoadingItem?.pause()
	}

	fun resume() {
		if (status != LoadStatus.PAUSE) return
		status = LoadStatus.IN_PROGRESS
		currLoadingItem?.resume()
	}

	fun stop() {
		currLoadingItem?.stop()
		currLoadingItem = null
		status = LoadStatus.CANCEL
	}

	fun isFilesExist() = items.firstOrNull { !it.isExist() } == null


	override fun toString(): String {
		return "Pack(id='$id', items=$items)"
	}

}
