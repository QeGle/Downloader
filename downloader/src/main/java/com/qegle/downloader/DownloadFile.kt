package com.qegle.downloader

import android.webkit.URLUtil
import java.io.*
import java.net.HttpURLConnection
import java.net.URL

internal class DownloadFile(private val destinationFolder: String,
                            private val tempFolder: String,
                            private val sUrl: String,
                            val onNewFolder: Boolean,
                            val namePrefix: String = "",
                            val needClearDestinyFolder: Boolean = false,
                            val needClearUnpackFolder: Boolean = false,
                            val filename: String? = null,
                            var onSuccess: () -> Unit,
                            var onProgress: (progress: Int) -> Unit,
                            var onError: (type: ErrorType, message: String) -> Unit) : Thread(sUrl) {
	var isPaused = false
	var isStopped = false
	private var fileDownloadProgress = .0

	override fun run() {
		downloadFileFromURL(destinationFolder, tempFolder, sUrl)
	}

	private fun downloadFileFromURL(destinationFolderPath: String, tempFolderPath: String, url: String) {
		if (isStopped) return
		fileDownloadProgress = .0
		var input: InputStream? = null
		var output: OutputStream? = null
		var connection: HttpURLConnection? = null
		try {

			var sUrl = url
			if (sUrl[sUrl.length - 1] == '/') {
				sUrl = sUrl.substring(0, sUrl.length - 1)
			}
			val url = URL(sUrl)
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
			val fileLength = connection.contentLength

			val requestedFileNameWithExt = URLUtil.guessFileName(sUrl, connection.getHeaderField("Content-Disposition"), null)

			val extension = "." + requestedFileNameWithExt.substringAfterLast(".")
			val requestedFileName = requestedFileNameWithExt.substringBeforeLast(".")

			val fileName = namePrefix + (filename ?: requestedFileName) + extension


			val destFolder = File(destinationFolderPath)
			val tempFolder = File(tempFolderPath)

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
			while (true) {
				if (isStopped) return
				if (isPaused) {
					Thread.sleep(50)
					continue
				}

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

			if (isStopped) {
				closeConnection(input, output, connection)
				return
			}

			if (needClearDestinyFolder) {
				destFolder.listFiles().forEach { it.deleteRecursively() }
			}

			closeConnection(input, output, connection)
			if (downloadFile.extension == "zip") {
				val unpackFolder = if (onNewFolder) File(destFolder, downloadFile.nameWithoutExtension) else destFolder
				if (needClearUnpackFolder) unpackFolder.listFiles()?.forEach { it.deleteRecursively() }
				downloadFile.extract(unpackFolder, onSuccess)
			} else {
				if (fileLength.toLong() == downloadFile.length() || fileLength == -1) {
					if (downloadFile.parentFile.path != destFolder.path)
						downloadFile.copyTo(File(destFolder, downloadFile.name), true)
					onSuccess.invoke()
				}
			}
		} catch (e: Exception) {
			e.printStackTrace()
			closeConnection(input, output, connection)
			error(ErrorType.LOAD, e.message ?: "")
		}
	}

	fun error(type: ErrorType, message: String) = onError.invoke(type, " $message, url:$sUrl")


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