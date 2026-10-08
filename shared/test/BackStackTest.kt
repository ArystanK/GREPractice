package kz.arctan.grepractice

import kotlin.test.Test
import kotlin.test.assertEquals

class BackStackTest {
    @Test
    fun everyScreenSurvivesARoundTrip() {
        val stack = listOf(
            Screen.Home, Screen.Exams, Screen.Setup(exam = false, topic = "Topology"), Screen.Setup(exam = true),
            Screen.Result("r1"), Screen.Random, Screen.Bank, Screen.Editor("q1"), Screen.Editor(null),
            Screen.Transfer, Screen.History(), Screen.History(byTopic = true), Screen.TopicHistory("Real Analysis"), Screen.Account,
        )
        assertEquals(stack, decodeBackStack(encodeBackStack(stack), sessionRunning = false))
    }

    @Test
    fun theSessionScreenIsShownExactlyWhenASessionIsRunning() {
        val saved = encodeBackStack(listOf(Screen.Home, Screen.Exams, Screen.Session))
        assertEquals(listOf(Screen.Home, Screen.Exams), decodeBackStack(saved, sessionRunning = false))
        assertEquals(listOf(Screen.Home, Screen.Exams, Screen.Session), decodeBackStack(saved, sessionRunning = true))
        // A cold start (no saved stack, e.g. the desktop app reopened) still returns to the session.
        assertEquals(listOf(Screen.Home, Screen.Session), decodeBackStack(null, sessionRunning = true))
    }

    @Test
    fun missingOrUnreadableStateStartsAtHome() {
        assertEquals(listOf(Screen.Home), decodeBackStack(null, sessionRunning = false))
        assertEquals(listOf(Screen.Home), decodeBackStack("not json", sessionRunning = false))
        assertEquals(listOf(Screen.Home), decodeBackStack("""[{"type":"kz.arctan.grepractice.Screen.Removed"}]""", sessionRunning = false))
        // Home is always at the bottom, so "Back to home" and the back button keep working.
        assertEquals(listOf(Screen.Home, Screen.Bank), decodeBackStack(encodeBackStack(listOf(Screen.Bank)), sessionRunning = false))
    }
}
