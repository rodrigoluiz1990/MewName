package com.mewname.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal suspend fun storeProfilePicture(context: Context, uri: Uri, qr: Boolean) = withContext(Dispatchers.IO) {
    val limit = if (qr) 1600 else 512
    val bitmap = if (Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val ratio = minOf(1f, limit.toFloat() / maxOf(info.size.width, info.size.height))
            decoder.setTargetSize(maxOf(1, (info.size.width * ratio).toInt()), maxOf(1, (info.size.height * ratio).toInt()))
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply { inSampleSize = 1 }
        while (maxOf(bounds.outWidth, bounds.outHeight) / options.inSampleSize > limit) options.inSampleSize *= 2
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Could not read image")
    }
    try {
        val destination = File(context.filesDir, if (qr) "profile-qr.png" else "profile-avatar.png")
        val temp = File(context.filesDir, destination.name + ".tmp")
        temp.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        check(temp.renameTo(destination))
    } finally {
        bitmap.recycle()
    }
}

@Composable
internal fun ProfilePicture(qr: Boolean, revision: Int, modifier: Modifier, description: String, placeholder: String) {
    val context = LocalContext.current
    val picture by produceState<Bitmap?>(null, qr, revision) {
        value = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(File(context.filesDir, if (qr) "profile-qr.png" else "profile-avatar.png").absolutePath)
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val bitmap = picture
        if (bitmap == null) Text(placeholder)
        else Image(bitmap.asImageBitmap(), description,
            modifier = Modifier.matchParentSize().then(if (qr) Modifier.background(Color.White) else Modifier),
            contentScale = if (qr) ContentScale.Fit else ContentScale.Crop)
    }
}