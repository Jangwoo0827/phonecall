package com.example.superdialer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.superdialer.messages.MmsShare
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Staging a picked picture and the intent that hands it to the phone's message composer. */
@RunWith(AndroidJUnit4::class)
class MmsShareTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val source = File(context.cacheDir, "pick_test.png")

    @After
    fun cleanUp() {
        source.delete()
        File(context.cacheDir, "outgoing").deleteRecursively()
    }

    @Test
    fun aPickedFileIsCopiedAndExposedThroughTheFileProvider() {
        source.writeBytes(ByteArray(2048) { it.toByte() })
        val staged = MmsShare.stage(context, source.toUri())

        assertEquals("content", staged.uri.scheme)
        assertEquals("${context.packageName}.files", staged.uri.authority)
        // the bytes come through the provider unchanged
        val copied = context.contentResolver.openInputStream(staged.uri)!!.use { it.readBytes() }
        assertEquals(2048, copied.size)
        assertEquals(source.readBytes().toList(), copied.toList())
    }

    @Test
    fun theComposerIntentCarriesRecipientAttachmentTextAndTheGrant() {
        val staged = MmsShare.Staged(Uri.parse("content://${context.packageName}.files/outgoing/mms_1.jpg"), "image/jpeg")
        val intent = MmsShare.buildIntent("01012345678", staged, "사진이에요", "com.example.messages")

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("image/jpeg", intent.type)
        assertEquals("01012345678", intent.getStringExtra("address"))
        assertEquals("사진이에요", intent.getStringExtra("sms_body"))
        assertEquals(staged.uri, intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
        assertEquals("com.example.messages", intent.`package`)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertNotNull(intent.clipData)
    }

    @Test
    fun withoutAMessagingAppThereIsNoPackageRestriction() {
        val staged = MmsShare.Staged(Uri.parse("content://x/y.png"), "image/png")
        assertEquals(null, MmsShare.buildIntent("0101", staged, "", null).`package`)
    }
}
