package com.ubertimetracker.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Collections
import android.content.pm.PackageManager
import android.os.Build
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleDriveManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val gsonFactory = GsonFactory.getDefaultInstance()
    private val driveScopes = listOf(DriveScopes.DRIVE_FILE)
    
    // Check if user is already signed in
    fun getLastSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    // Get Sign-In Intent for UI
    fun getSignInIntent(): Intent {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
        val client = GoogleSignIn.getClient(context, gso)
        return client.signInIntent
    }

    // Initialize Drive Service with account
    private fun getDriveService(account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            context, Collections.singleton(DriveScopes.DRIVE_FILE)
        )
        credential.selectedAccount = account.account
        
        return Drive.Builder(
            AndroidHttp.newCompatibleTransport(),
            gsonFactory,
            credential
        ).setApplicationName("UberTimeTracker").build()
    }

    suspend fun uploadFile(file: java.io.File, mimeType: String): String? = withContext(Dispatchers.IO) {
        val account = getLastSignedInAccount() ?: throw Exception("Not signed in to Google Drive")
        val service = getDriveService(account)
        
        android.util.Log.d("GoogleDriveManager", "Uploading file: ${file.name}, Size: ${file.length()} bytes")

        if (file.length() == 0L) {
             android.util.Log.e("GoogleDriveManager", "File is empty! Aborting upload.")
             return@withContext null
        }
        
        // 1. Search for existing file by name
        // We might want to limit search to a specific folder or just root for simplicity as per request "If file already exists... update"
        val query = "name = '${file.name}' and trashed = false"
        val fileList = service.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute()
            
        val existingFileId = fileList.files.firstOrNull()?.id
        
        val fileMetadata = com.google.api.services.drive.model.File().apply {
            name = file.name
        }
        val mediaContent = FileContent(mimeType, file)

        if (existingFileId != null) {
            // Update existing
            service.files().update(existingFileId, null, mediaContent).execute()
            android.util.Log.d("GoogleDriveManager", "Updated existing file ID: $existingFileId")
            return@withContext existingFileId
        } else {
            // Create new
            val uploadedFile = service.files().create(fileMetadata, mediaContent)
                .setFields("id")
                .execute()
            android.util.Log.d("GoogleDriveManager", "Created new file ID: ${uploadedFile.id}")
            return@withContext uploadedFile.id
        }
    }

    fun signOut(callback: () -> Unit) {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        val client = GoogleSignIn.getClient(context, gso)
        client.signOut().addOnCompleteListener {
            callback()
        }
    }

    fun getSha1Fingerprint(): String {
        try {
            val pm = context.packageManager
            val packageName = context.packageName
            val flags = PackageManager.GET_SIGNATURES
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            }

            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo.apkContentsSigners
            } else {
                packageInfo.signatures
            }

            val cert = signatures[0].toByteArray()
            val input = ByteArrayInputStream(cert)
            val cf = CertificateFactory.getInstance("X509")
            val c = cf.generateCertificate(input) as X509Certificate
            val md = MessageDigest.getInstance("SHA1")
            val publicKey = md.digest(c.encoded)

            val hexString = StringBuilder()
            for (i in publicKey.indices) {
                val appendString = Integer.toHexString(0xFF and publicKey[i].toInt())
                if (appendString.length == 1) hexString.append("0")
                hexString.append(appendString)
                hexString.append(":")
            }
            return hexString.toString().dropLast(1).uppercase()
        } catch (e: Exception) {
            return "Error retrieving SHA1: ${e.message}"
        }
    }
}
