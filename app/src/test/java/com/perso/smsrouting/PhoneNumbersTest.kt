package com.perso.smsrouting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumbersTest {

    @Test
    fun `les formats d'un meme numero sont equivalents`() {
        assertTrue(PhoneNumbers.sameNumber("0612345678", "+33612345678"))
        assertTrue(PhoneNumbers.sameNumber("0612345678", "0033 6 12 34 56 78"))
        assertTrue(PhoneNumbers.sameNumber("06 12 34 56 78", "06.12.34.56.78"))
    }

    @Test
    fun `deux numeros distincts ne sont pas confondus`() {
        assertFalse(PhoneNumbers.sameNumber("0612345678", "0612345679"))
        assertFalse(PhoneNumbers.sameNumber("0612345678", "0712345678"))
    }

    @Test
    fun `un numero court est compare en entier`() {
        assertTrue(PhoneNumbers.sameNumber("36103", "36103"))
        assertFalse(PhoneNumbers.sameNumber("36103", "36104"))
    }

    @Test
    fun `un expediteur alphanumerique est reconnu`() {
        assertTrue(PhoneNumbers.sameNumber("Swile", "SWILE"))
        assertTrue(PhoneNumbers.sameNumber("Credit Agricole", "CreditAgricole"))
        assertTrue(PhoneNumbers.sameNumber("Crédit", "Credit"))
        assertFalse(PhoneNumbers.sameNumber("Swile", "Ricardo"))
    }

    @Test
    fun `un identifiant textuel n'entre jamais en collision avec un numero`() {
        assertFalse(PhoneNumbers.sameNumber("36103", "36103A"))
        assertTrue(PhoneNumbers.key("36103").startsWith("N:"))
        assertTrue(PhoneNumbers.key("Swile").startsWith("A:"))
    }

    @Test
    fun `un numero long contenant une annotation reste traite comme un numero`() {
        assertTrue(PhoneNumbers.sameNumber("0612345678 (perso)", "+33612345678"))
    }

    @Test
    fun `une valeur vide ne correspond a rien`() {
        assertEquals("", PhoneNumbers.key(null))
        assertEquals("", PhoneNumbers.key("   "))
        assertEquals("", PhoneNumbers.key("---"))
        assertFalse(PhoneNumbers.sameNumber(null, null))
    }
}
