package run.thespot.weft.map.navermap

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs

class NaverMapAndroidSdkInitializerTest {
    @BeforeTest
    @AfterTest
    fun resetRegistry() {
        NaverMapAndroidSdkInitializer.resetForTest()
    }

    @Test
    fun sameKeyInitializesOnce() {
        val initializedKeys = mutableListOf<String>()

        NaverMapAndroidSdkInitializer.initialize("same-key", initializedKeys::add)
        NaverMapAndroidSdkInitializer.initialize("same-key", initializedKeys::add)

        assertEquals(listOf("same-key"), initializedKeys)
    }

    @Test
    fun conflictingKeyDoesNotReinitialize() {
        val initializedKeys = mutableListOf<String>()
        NaverMapAndroidSdkInitializer.initialize("first-key", initializedKeys::add)

        val failure = assertFailsWith<NaverMapException> {
            NaverMapAndroidSdkInitializer.initialize("second-key", initializedKeys::add)
        }

        assertEquals(listOf("first-key"), initializedKeys)
        assertFalse(failure.message.orEmpty().contains("first-key"))
        assertFalse(failure.message.orEmpty().contains("second-key"))
        assertIs<NaverMapException>(failure)
    }
}
