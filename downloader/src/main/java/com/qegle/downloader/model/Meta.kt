package com.qegle.downloader.model

class Meta(var loadingFolder: String,
           val savingFolder: String = loadingFolder,

           val fileName: String? = null,
           val tempFileName: String? = fileName,

           val onNewFolder: Boolean = true,
           val needClearFolder: Boolean = false,

           val namePrefix: String = "",
           val namePostfix: String = "")
