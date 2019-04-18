package com.qegle.downloader.model

/**
 * Метаинформация, содержащия дополнительные действия над файлом до/после загрузки
 *
 *
 * @param loadingFolder - папка, куда загружается файл
 * @param savingFolder - папка, куда перемещается файл после загрузки
 *
 * @param fileName - название файла. null, если нужно взять его из хедера
 * @param tempFileName - названия файла на время загрузки
 *
 * @param onNewFolder - нужно ли создать папку с именем файла и поместить загружаемый/разархивируемый файл в нее
 * @param needClearFolder - нужно ли очистить папку перед перемещением в нее файла
 *
 * @param namePrefix - префикс для имени сохраняемого файла
 * @param namePostfix - постфикс для имени сохраняемого файла
 *
 */
class Meta(var loadingFolder: String,
           val savingFolder: String = loadingFolder,

           val fileName: String? = null,
           val tempFileName: String? = fileName,

           val onNewFolder: Boolean = true,
           val needClearFolder: Boolean = false,

           val namePrefix: String = "",
           val namePostfix: String = "")
