package com.encryptedjournal.data

import android.content.Context
import java.io.File
import java.io.InputStream
import java.io.OutputStream

import android.util.Log

object FileEncryptionManager {

    fun encryptFile(context: Context, inputFile: File, outputFile: File) {
        if (!inputFile.exists()) {
            Log.e("FileEncryption", "Input file not found: ${inputFile.absolutePath}")
            return
        }
        
        Log.d("FileEncryption", "Encrypting: ${inputFile.name} to ${outputFile.name}")
        val encryptedFile = SecurityUtils.getEncryptedFile(context, outputFile)
        
        try {
            val inputStream: InputStream = inputFile.inputStream()
            val outputStream: OutputStream = encryptedFile.openFileOutput()
            
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            // delete original plain file
            inputFile.delete()
            Log.d("FileEncryption", "Encryption successful")
        } catch (e: Exception) {
            Log.e("FileEncryption", "Encryption failed", e)
        }
    }

    fun decryptToTempFile(context: Context, encryptedFile: File): File? {
        if (!encryptedFile.exists()) {
            Log.e("FileEncryption", "Encrypted file not found: ${encryptedFile.absolutePath}")
            return null
        }
        
        // If it's already a decrypted temp file or not encrypted, just return it
        if (encryptedFile.name.startsWith("dec_") || !encryptedFile.name.startsWith("enc_")) {
            Log.d("FileEncryption", "File already plain: ${encryptedFile.name}")
            return encryptedFile
        }

        try {
            Log.d("FileEncryption", "Decrypting: ${encryptedFile.name}")
            val secureFile = SecurityUtils.getEncryptedFile(context, encryptedFile)
            val decryptedDir = File(context.filesDir, "decrypted_media")
            if (!decryptedDir.exists()) decryptedDir.mkdirs()
            
            val tempFile = File(decryptedDir, "dec_${encryptedFile.name}")
            
            val inputStream: InputStream = secureFile.openFileInput()
            val outputStream: OutputStream = tempFile.outputStream()
            
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            Log.d("FileEncryption", "Decryption successful: ${tempFile.absolutePath}, size: ${tempFile.length()}")
            return tempFile
        } catch (e: Exception) {
            Log.e("FileEncryption", "Decryption failed", e)
            return null
        }
    }

    fun clearDecryptedCache(context: Context) {
        try {
            val decryptedDir = File(context.filesDir, "decrypted_media")
            if (decryptedDir.exists()) {
                decryptedDir.listFiles()?.forEach { it.delete() }
                Log.d("FileEncryption", "Decrypted cache cleared")
            }
        } catch (e: Exception) {
            Log.e("FileEncryption", "Failed to clear cache", e)
        }
    }
    
    fun openEncryptedInput(context: Context, file: File): InputStream {
        return SecurityUtils.getEncryptedFile(context, file).openFileInput()
    }
}
