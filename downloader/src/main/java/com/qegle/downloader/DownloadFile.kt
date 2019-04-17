package com.qegle.downloader

import android.os.Build
import android.webkit.URLUtil
import com.qegle.downloader.model.Item
import java.io.*
import java.net.HttpURLConnection
import java.net.URL

internal class DownloadFile(private val item: Item,
                            var onSuccess: (url: String, loading: Long, fileSize: Long) -> Unit,
                            var onProgress: (progress: Int) -> Unit,
                            var onError: (type: ErrorType, message: String) -> Unit) {
	private val meta = item.meta
	var isPaused = false
	var isStopped = false
	private var fileDownloadProgress = .0
	fun load() {
		load(prepareUrl(item.url))
	}

	private fun prepareUrl(url: String): String {
		if (url.isEmpty()) return url
		if (url[url.lastIndex] == '/') return url.substring(0, url.lastIndex)
		return url
	}

	private fun load(sUrl: String) {
		if (isStopped) return
		fileDownloadProgress = .0
		var loading: Long = 0

		var input: InputStream? = null
		var output: OutputStream? = null
		var connection: HttpURLConnection? = null

		try {
			val url = URL(sUrl)
			var startLoadingTime = System.currentTimeMillis()
			connection = url.openConnection() as HttpURLConnection
			connection.connect()
			if (isStopped) {
				closeConnection(input, output, connection)
				return
			}

			// expect HTTP 200 OK, so we don't mistakenly save error report
			// instead of the file
			if (connection.responseCode != HttpURLConnection.HTTP_OK) {
				closeConnection(input, output, connection)
				error(ErrorType.LOAD, "code: ${connection.responseCode}, respMsg:${connection.responseMessage}")
				return
			}

			var fileLength = connection.getContentLengthExt()
			val requestedFileNameWithExt = connection.getFileName(sUrl)
			val extension = "." + requestedFileNameWithExt.substringAfterLast(".")
			val requestedFileName = requestedFileNameWithExt.substringBeforeLast(".")
			val fileName = meta.namePrefix + (meta.tempFileName ?: requestedFileName) + meta.namePostfix + extension

			val destFolder = File(meta.savingFolder)
			val tempFolder = File(meta.loadingFolder)

			if (!destFolder.exists()) {
				if (!destFolder.mkdirs()) {
					closeConnection(input, output, connection)
					error(ErrorType.LOAD, "can't create destFolder at path: ${destFolder.path}")
					return
				}
			}

			if (!tempFolder.exists()) {
				if (!tempFolder.mkdirs()) {
					closeConnection(input, output, connection)
					error(ErrorType.LOAD, "can't create tempFolder at path: ${tempFolder.path}")
					return
				}
			}

			val downloadFile = File(tempFolder, fileName)

			// download the file
			input = connection.inputStream
			output = FileOutputStream(downloadFile)

			val data = ByteArray(4096)
			var total = 0.0
			var count: Int
			fileDownloadProgress = 0.0
			if (isStopped) {
				closeConnection(input, output, connection)
				return
			}

			var hasPaused = isPaused

			while (true) {
				if (isStopped) return
				if (isPaused) {
					if (!hasPaused) {
						hasPaused = isPaused
						loading += System.currentTimeMillis() - startLoadingTime
						startLoadingTime = System.currentTimeMillis()
					} else {
						startLoadingTime = System.currentTimeMillis()
					}

					Thread.sleep(50)
					continue
				}
				hasPaused = isPaused

				count = input.read(data)
				if (count == -1) break


				total += count.toDouble()
				// publishing the progress....
				if (fileLength > 0) {
					fileDownloadProgress = total / fileLength
					onProgress.invoke((fileDownloadProgress * 100).toInt())
				}
				output.write(data, 0, count)
			}
			closeConnection(input, output, connection)
			loading += System.currentTimeMillis() - startLoadingTime

			if (fileLength < total) fileLength = total.toLong()

			if (isStopped) {
				closeConnection(input, output, connection)
				return
			}

			if (meta.needClearFolder) {
				destFolder.listFiles().forEach { it.deleteRecursively() }
			}


			closeConnection(input, output, connection)
			if (downloadFile.extension == "zip") {
				val unpackFolder = if (meta.onNewFolder)
					File(destFolder, meta.fileName ?: downloadFile.nameWithoutExtension)
				else
					destFolder
				if (meta.needClearFolder) unpackFolder.listFiles()?.forEach { it.deleteRecursively() }
				downloadFile.extract(unpackFolder, onSuccess = { onSuccess.invoke(sUrl, loading, fileLength) })
			} else {
				if (fileLength == downloadFile.length() || fileLength == -1L) {
					if (downloadFile.parentFile.path != destFolder.path)
						downloadFile.copyTo(File(destFolder,
							(meta.fileName ?: downloadFile.nameWithoutExtension) + "." + downloadFile.extension), true)
					onSuccess.invoke(sUrl, loading, fileLength)
				}
			}
		} catch (e: Exception) {
			e.printStackTrace()
			closeConnection(input, output, connection)
			error(ErrorType.LOAD, e.message ?: "")
		}
	}

	private fun error(type: ErrorType, message: String) = onError.invoke(type, " $message, url:${item.url}")

	private fun HttpURLConnection.getFileName(url: String) = URLUtil
		.guessFileName(url, this.getHeaderField("Content-Disposition"), null)

	private fun HttpURLConnection.getContentLengthExt() =
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
			this.contentLengthLong
		else
			this.contentLength.toLong()

	private fun File.extract(destFolder: File, onSuccess: () -> Unit) {
		UnpackZip.unpack(this, destFolder, onSuccess,
			onError = { error(ErrorType.ZIP, it) })
	}

	private fun closeConnection(input: InputStream?, output: OutputStream?, connection: HttpURLConnection?) {
		try {
			output?.flush()
			output?.close()
			input?.close()
			connection?.disconnect()
		} catch (ignored: IOException) {
		}
	}

	fun stopLoading() {
		isStopped = true
	}
}