package com.qegle.downloader

import java.io.*
import java.util.zip.ZipInputStream


/**
 * Статический метод для распаковки zip-архивов
 *
 * @param file - файл, который нужно распаковать
 * @param unpackFolder - папка, в которую будет происходить распаковка
 * @param onSuccess - вызывается при успешной распаковке
 * @param onError - вызывается при ошибке
 */
fun unpack(file: File, unpackFolder: File, onSuccess: () -> Unit, onError: (message: String) -> Unit) {
	val buf = ByteArray(1024)
	var len: Int

	if (!file.exists()) {
		onError.invoke("file not exist ${file.path}")
		return
	}
	if (!unpackFolder.exists() && !unpackFolder.mkdirs()) {
		onError.invoke("unpackFolder not exist/not folder ${unpackFolder.path}")
		return
	}


	val zis = ZipInputStream(FileInputStream(file))

	var ze = zis.nextEntry
	while (ze != null) {

		val outFile = File(unpackFolder, ze.name)

		if (ze.isDirectory) {
			outFile.mkdirs()
			continue
		}

		val stream = BufferedOutputStream(FileOutputStream(outFile))

		try {

			len = zis.read(buf)
			while (len >= 0) {
				stream.write(buf, 0, len)
				len = zis.read(buf)
			}


		} catch (e: Exception) {
			stream.close()
			zis.closeEntry()
			onError.invoke("on unpack: ${e.message}")
		}

		stream.close()
		zis.closeEntry()
		ze = zis.nextEntry
	}
	zis.close()
	onSuccess.invoke()
}