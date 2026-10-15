package com.example.superdialer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.superdialer.kakao.KakaoInbox
import com.example.superdialer.kakao.KakaoNotificationService
import com.example.superdialer.kakao.notificationAccessGranted
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * KakaoTalk's notifications are collected into the inbox and answered through the notification's reply button. The test
 * posts its own notification (with this app's package standing in for KakaoTalk's) and grants itself notification access
 * through the shell, which only works on an emulator or a debuggable device: never run this on the real phone.
 */
@RunWith(AndroidJUnit4::class)
class KakaoInboxFlowTest : AppTestBase() {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val service = "${context.packageName}/${KakaoNotificationService::class.java.name}"
    private val replyAction = "${context.packageName}.test.REPLY"
    private val friend = "카톡테스트친구"
    private val replies = ArrayList<String>()
    private var replyLatch = CountDownLatch(1)
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            RemoteInput.getResultsFromIntent(intent)?.getCharSequence("reply")?.let { replies += it.toString() }
            replyLatch.countDown()
        }
    }

    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use { fd ->
            java.io.FileInputStream(fd.fileDescriptor).readBytes()
        }
    }

    private fun postFriendNotification(text: String) {
        notifications.createNotificationChannel(NotificationChannel("kakao_test", "test", NotificationManager.IMPORTANCE_DEFAULT))
        val input = RemoteInput.Builder("reply").setLabel("답장").build()
        val reply = PendingIntent.getBroadcast(
            context, 1, Intent(replyAction).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val action = Notification.Action.Builder(null, "답장", reply).addRemoteInput(input).build()
        notifications.notify(
            7001,
            Notification.Builder(context, "kakao_test")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(friend)
                .setContentText(text)
                .addAction(action)
                .build(),
        )
    }

    @Before
    fun feedTheInbox() {
        KakaoInbox.clear()
        KakaoInbox.targetPackages += context.packageName
        context.registerReceiver(receiver, IntentFilter(replyAction), Context.RECEIVER_NOT_EXPORTED)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { KakaoInbox.switchCollecting(true) }
        shell("cmd notification allow_listener $service")
        val deadline = System.currentTimeMillis() + 15_000
        while (!notificationAccessGranted(context) && System.currentTimeMillis() < deadline) Thread.sleep(200)
        assertTrue("notification access was not granted", notificationAccessGranted(context))
    }

    @After
    fun cleanUp() {
        notifications.cancel(7001)
        shell("cmd notification disallow_listener $service")
        runCatching { context.unregisterReceiver(receiver) }
        KakaoInbox.targetPackages -= context.packageName
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            KakaoInbox.switchCollecting(false)
            KakaoInbox.clear()
        }
    }

    private fun waitForMessage(text: String) {
        val deadline = System.currentTimeMillis() + 20_000
        while (KakaoInbox.messages.none { it.text == text } && System.currentTimeMillis() < deadline) Thread.sleep(200)
        assertTrue("message '$text' never reached the inbox", KakaoInbox.messages.any { it.text == text })
    }

    @Test
    fun aNotificationShowsUpAsAChatAndTheReplyReachesTheNotification() {
        postFriendNotification("점심 뭐 먹을래?")
        waitForMessage("점심 뭐 먹을래?")

        tab("최근기록")
        waitFor(hasText("카톡"))
        compose.onNodeWithText("카톡").performClick()
        waitFor(hasTestTag("kakao_room_list"))
        compose.onNodeWithText(friend).assertIsDisplayed()

        compose.onNodeWithText(friend).performClick()
        waitFor(hasText("점심 뭐 먹을래?"))
        waitFor(hasSetTextAction()) // the reply box: the notification is still showing

        compose.onNode(hasSetTextAction()).performTextInput("김치찌개!")
        compose.onNodeWithContentDescription("답장 보내기").performClick()

        assertTrue("the reply never reached the notification", replyLatch.await(10, TimeUnit.SECONDS))
        assertEquals(listOf("김치찌개!"), replies)
        // and it is part of the chat here too
        waitFor(hasText("김치찌개!"))
    }

    @Test
    fun withCollectingOffNothingIsKept() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { KakaoInbox.switchCollecting(false) }
        postFriendNotification("이건 저장되면 안 돼")
        Thread.sleep(3_000)
        assertTrue(KakaoInbox.messages.isEmpty())
    }

    @Test
    fun theKakaoViewExplainsHowToSwitchItOnWhenItIsOff() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { KakaoInbox.switchCollecting(false) }
        tab("최근기록")
        waitFor(hasText("카톡"))
        compose.onNodeWithText("카톡").performClick()
        waitFor(hasTestTag("kakao_setup"))
        compose.onNodeWithText("모아 보기 켜기").assertIsDisplayed()
    }
}
