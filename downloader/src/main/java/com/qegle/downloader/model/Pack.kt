package com.qegle.downloader.model

import com.qegle.downloader.ErrorType
import com.qegle.downloader.LoadStatus
import io.reactivex.observers.DisposableObserver
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject

/**
 * Интерфейс обертки над загрузкой группы файлов
 */
interface IPack {
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
	 * Ставит заргузку текущего item'a на паузу
	 */
	fun pause()

	/**
	 * Возобновляет загрузку текущего item'a
	 */
	fun resume()

	/**
	 * Останавливает текущего item'a загрузку. Без возможности возобновить!!
	 */
	fun stop()

	/**
	 * Проверяет на успешность загрузки все item'ы
	 */
	fun isFilesExist(): Boolean
}

/**
 * Класс-обертка над группой файлов. Файлы загружаются последовательно.
 *
 * @param id - идентификатор группы файлов.
 * @param items - сама группа файлов
 */

class Pack(override val id: String, val items: List<Item>) : IPack {


	var status: LoadStatus = LoadStatus.PAUSE
	var progressSubject: PublishSubject<Int> = PublishSubject.create()
	var errorSubject: PublishSubject<Pair<ErrorType, String>> = PublishSubject.create()
	var currLoadingItem: Item? = null

	override fun download(onSuccess: () -> Unit) {
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

		val err = currLoadingItem?.errorSubject
			?.subscribeOn(Schedulers.io())
			?.observeOn(Schedulers.io())
			?.subscribeWith(object : DisposableObserver<Pair<ErrorType, String>?>() {
				override fun onComplete() {}

				override fun onNext(error: Pair<ErrorType, String>) {
					itemError(error.first, error.second)
				}

				override fun onError(error: Throwable) {
					itemError(ErrorType.LOAD, error.message ?: "")
				}
			})

		val progress = currLoadingItem?.progressSubject
			?.subscribeOn(Schedulers.io())
			?.observeOn(Schedulers.io())
			?.subscribeWith(object : DisposableObserver<Int?>() {
				override fun onComplete() {}

				override fun onNext(progress: Int) {
					itemProgressUpdate(progress)
				}

				override fun onError(error: Throwable) {
					itemError(ErrorType.LOAD, error.message ?: "")
				}
			})

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

	override fun pause() {
		if (status != LoadStatus.IN_PROGRESS) return
		status = LoadStatus.PAUSE
		currLoadingItem?.pause()
	}

	override fun resume() {
		if (status != LoadStatus.PAUSE) return
		status = LoadStatus.IN_PROGRESS
		currLoadingItem?.resume()
	}

	override fun stop() {
		currLoadingItem?.stop()
		currLoadingItem = null
		status = LoadStatus.CANCEL
	}

	override fun isFilesExist() = items.firstOrNull { !it.isExist() } == null


	override fun toString(): String {
		return "Pack(id='$id', items=$items)"
	}
}
