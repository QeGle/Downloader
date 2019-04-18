package com.qegle.downloader.model

import com.qegle.downloader.*
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.observers.DisposableObserver
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import java.io.File

/**
 * Интерфейс обертки над загрузкой файла
 */
interface IItem {
	/**
	 * Уникальный идентификатор
	 */
	val id: String

	/**
	 * Ничинает загрузку. Возвращает реультат в $onSuccess
	 * @param onSuccess - вызывается при успешной загрузке
	 */
	fun download(onSuccess: () -> Unit)

	/**
	 * Ставит заргузку на паузу
	 */
	fun pause()

	/**
	 * Возобновляет загрузку
	 */
	fun resume()

	/**
	 * Останавливает загрузку. Без возможности возобновить!!
	 */
	fun stop()

	/**
	 * Проверяет на успешность загрузки текущего item'a
	 */
	fun isExist(): Boolean
}

/**
 * Класс-обертка над загрузкой самого файла.
 * Управляет загрузкой "себя" и обработкой результатов
 * @param id - уникальный идентификатор
 * @param url - урл для загрузки
 * @param meta - метаинформация
 *
 */
class Item(override val id: String, val url: String, var meta: Meta) : IItem {
	constructor(id: String, url: String, loadingFolder: String) : this(id, url, Meta(loadingFolder))

	var timingListener: TimingListener? = null
	var status: LoadStatus = LoadStatus.PAUSE
	var progressSubject: PublishSubject<Int> = PublishSubject.create()
	var errorSubject: PublishSubject<Pair<ErrorType, String>> = PublishSubject.create()
	private var loader: DownloadFile? = null
	private var progress = 0
	private var loadingDis: Disposable? = null

	override fun download(onSuccess: () -> Unit) {
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

	override fun pause() {
		if (status != LoadStatus.IN_PROGRESS) return
		status = LoadStatus.PAUSE
		loader?.isPaused = true
	}

	override fun resume() {
		if (status != LoadStatus.PAUSE) return
		status = LoadStatus.IN_PROGRESS
		loader?.isPaused = false
	}

	override fun stop() {
		loadingDis?.dispose()
		loader?.stop()
		loader = null
		status = LoadStatus.CANCEL
	}

	override fun toString(): String {
		val folder = File(meta.savingFolder, meta.namePrefix + (meta.fileName ?: id) + meta.namePostfix)
		return "Item(url=$url, path=${folder.path}, id=$id)"
	}

	override fun isExist(): Boolean {
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

/**
 * Генерируется при ошибке загрузки. содержит в себе тип события, которое ее вызвало.
 * @param type - тип события, которое вызвало ошибку
 */
class LoadingException(val type: ErrorType, message: String) : Exception(message)