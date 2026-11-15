package com.forestry.counter.presentation.screens.field

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.forestry.counter.R
import java.io.File
import kotlinx.coroutines.*

@Composable internal fun FieldPhotos(vm: FieldJournalViewModel, ids: List<String>, editable: Boolean) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var cameraPath by rememberSaveable(vm) { mutableStateOf<String?>(null) }
    var galleryDraftId by rememberSaveable(vm) { mutableStateOf<String?>(null) }
    var cameraDraftId by rememberSaveable(vm) { mutableStateOf<String?>(null) }
    fun import(expectedDraftId: String?, uri: Uri, temporary: File?=null) {
        if (expectedDraftId == null || expectedDraftId != vm.currentPhotoDraftId()) {
            temporary?.delete()
            return
        }
        scope.launch {
            try {
                val result=withContext(Dispatchers.IO) {
                    val bytes=context.contentResolver.openInputStream(uri)?.use { stream ->
                        val output=java.io.ByteArrayOutputStream()
                        val buffer=ByteArray(8192)
                        while(true) {
                            val n=stream.read(buffer); if(n<0) break
                            require(output.size()+n <= 12*1024*1024)
                            output.write(buffer,0,n)
                        }; output.toByteArray()
                    } ?: error("NO_IMAGE")
                    val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
                    BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
                    bytes to (bounds.outMimeType ?: error("INVALID_IMAGE"))
                }
                vm.addPhoto(expectedDraftId,result.first,result.second)
            } catch(e: CancellationException) {throw e}
            catch(_: Exception) {
                if (expectedDraftId == vm.currentPhotoDraftId()) vm.photoError()
            }
            finally {temporary?.delete()}
        }
    }
    val gallery=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val expectedDraftId=galleryDraftId
        galleryDraftId=null
        if (uri != null) import(expectedDraftId,uri)
    }
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val expectedDraftId=cameraDraftId
        cameraDraftId=null
        val file=cameraPath?.let(::File);cameraPath=null
        if(success && file!=null) import(expectedDraftId,FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file),file)
        else file?.delete()
    }
    fun launchCamera() {
        val expectedDraftId=cameraDraftId
        if (expectedDraftId == null || expectedDraftId != vm.currentPhotoDraftId()) {
            cameraDraftId=null
            return
        }
        try {
            val dir=File(context.cacheDir,"field_capture").apply {mkdirs()}
            // Only stale captures are removed; never delete another in-flight capture.
            dir.listFiles()?.filter {System.currentTimeMillis()-it.lastModified()>24*60*60*1000L}?.forEach {it.delete()}
            val file=File.createTempFile("capture-",".jpg",dir)
            cameraPath=file.absolutePath
            camera.launch(FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file))
        } catch(_: Exception) {
            cameraPath?.let(::File)?.delete();cameraPath=null;cameraDraftId=null
            if (expectedDraftId == vm.currentPhotoDraftId()) vm.photoError()
        }
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if(it) launchCamera()
        else {
            val expectedDraftId=cameraDraftId
            cameraDraftId=null
            if (expectedDraftId != null && expectedDraftId == vm.currentPhotoDraftId()) vm.photoError()
        }
    }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.field_photos),style=MaterialTheme.typography.titleMedium)
        ids.forEach { id ->
            val bitmap by produceState<android.graphics.Bitmap?>(null,id,vm) {
                try {
                    val bytes=vm.photo(id)
                    value=withContext(Dispatchers.Default) {
                        val options=BitmapFactory.Options().apply {inJustDecodeBounds=true}
                        BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)
                        options.inSampleSize=generateSequence(1) {it*2}.first {maxOf(options.outWidth,options.outHeight)/it <= 1200}
                        options.inJustDecodeBounds=false
                        BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)
                    }
                } catch(e: CancellationException) {throw e} catch(_: Exception) {value=null}
            }
            bitmap?.let {Image(it.asImageBitmap(),stringResource(R.string.field_photos),Modifier.fillMaxWidth().heightIn(max=260.dp),contentScale=ContentScale.Fit)}
            if(editable) TextButton({vm.change {copy(photoIds=photoIds-id)}}) {Text(stringResource(R.string.field_photo_remove))}
        }
        if(editable && ids.size<6) {
            Text(stringResource(R.string.field_photo_limit),style=MaterialTheme.typography.bodySmall)
            OutlinedButton({
                val expectedDraftId=vm.currentPhotoDraftId()
                if (expectedDraftId != null) {
                    galleryDraftId=expectedDraftId
                    gallery.launch("image/*")
                }
            },modifier=Modifier.fillMaxWidth()) {Text(stringResource(R.string.field_photo_add))}
            OutlinedButton({
                val expectedDraftId=vm.currentPhotoDraftId()
                if (expectedDraftId != null) {
                    cameraDraftId=expectedDraftId
                    if(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) launchCamera()
                    else permission.launch(Manifest.permission.CAMERA)
                }
            },modifier=Modifier.fillMaxWidth()) {Text(stringResource(R.string.field_photo_camera))}
        }
    }
}
