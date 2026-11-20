package com.example.superdialer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.superdialer.notes.Notes
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** The notepad section of the Apps tab: write a note, find it again, search, delete. */
@RunWith(AndroidJUnit4::class)
class NotesScreenTest : AppTestBase() {
    private val marker = "테스트메모ZZ"

    @After
    fun cleanUp() {
        Notes.all().filter { it.text.contains(marker) }.forEach { Notes.delete(it.id) }
    }

    private fun openNotes() {
        tab("앱")
        waitFor(hasText("메모"))
        compose.onNode(hasText("메모")).performClick()
        waitFor(hasTag("notes_new"))
    }

    @Test
    fun aNoteCanBeWrittenSearchedAndDeleted() {
        openNotes()
        compose.onNodeWithTag("notes_new").performClick()
        waitFor(hasTag("note_text"))
        compose.onNodeWithTag("note_text").performTextInput("$marker 첫 줄\n둘째 줄")
        compose.onNodeWithTag("note_back").performClick()

        waitForText("$marker 첫 줄")
        compose.onNodeWithText("둘째 줄").assertIsDisplayed()

        compose.onNodeWithTag("notes_search").performTextInput("없는글자QQ")
        waitForText("일치하는 메모가 없어요.")
        waitGone(hasText("$marker 첫 줄"))
    }

    @Test
    fun aNoteCanBeDeletedFromItsEditor() {
        openNotes()
        compose.onNodeWithTag("notes_new").performClick()
        waitFor(hasTag("note_text"))
        compose.onNodeWithTag("note_text").performTextInput("$marker 지울 메모")
        compose.onNodeWithTag("note_back").performClick()
        waitForText("$marker 지울 메모")

        compose.onNodeWithText("$marker 지울 메모").performClick()
        waitFor(hasTag("note_delete"))
        compose.onNodeWithTag("note_delete").performClick()
        waitForText("이 메모를 삭제할까요?")
        compose.onNode(hasText("삭제") and hasClickAction()).performClick()
        waitFor(hasTag("notes_new"))
        waitGone(hasText("$marker 지울 메모"))
    }
}
