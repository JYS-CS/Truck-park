package com.truckpark.management.feature.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlateOcrNormalizerTest {

    @Test
    fun testZambianStandardPlates() {
        assertTrue(PlateOcrNormalizer.isValidPlate("BAA1234"))
        assertTrue(PlateOcrNormalizer.isValidPlate("BCA1234B"))
        assertTrue(PlateOcrNormalizer.isValidPlate("ALX123"))
        assertTrue(PlateOcrNormalizer.isValidPlate("CD1234"))
    }

    @Test
    fun testRegionalSadcPlates() {
        // South Africa formats
        assertTrue(PlateOcrNormalizer.isValidPlate("CA123456"))
        assertTrue(PlateOcrNormalizer.isValidPlate("AB12CDGP"))
        assertTrue(PlateOcrNormalizer.isValidPlate("CK88LLGP"))
        assertTrue(PlateOcrNormalizer.isValidPlate("BCD123MP"))

        // Zimbabwe / Malawi
        assertTrue(PlateOcrNormalizer.isValidPlate("AEB1234"))
        assertTrue(PlateOcrNormalizer.isValidPlate("BL1234"))

        // Botswana / Tanzania / Lesotho
        assertTrue(PlateOcrNormalizer.isValidPlate("B123ABC"))
        assertTrue(PlateOcrNormalizer.isValidPlate("T123ABC"))
        assertTrue(PlateOcrNormalizer.isValidPlate("A123ABC"))

        // Namibia
        assertTrue(PlateOcrNormalizer.isValidPlate("N12345W"))
        assertTrue(PlateOcrNormalizer.isValidPlate("N12345WB"))

        // Mozambique / Eswatini / Angola
        assertTrue(PlateOcrNormalizer.isValidPlate("AAA123MC"))
        assertTrue(PlateOcrNormalizer.isValidPlate("ASD123AM"))
        assertTrue(PlateOcrNormalizer.isValidPlate("LD1234AB"))

        // DR Congo
        assertTrue(PlateOcrNormalizer.isValidPlate("1234AB01"))
    }

    @Test
    fun testNormalizationRepairs() {
        // Zambia / Zimbabwe / Malawi '0' <-> 'O' and '8' <-> 'B'
        assertEquals("BCA1204B", PlateOcrNormalizer.normalizeCandidate("BCA12O4B"))
        assertEquals("BAA1234", PlateOcrNormalizer.normalizeCandidate("8AA1234"))
        assertEquals("BBA1234", PlateOcrNormalizer.normalizeCandidate("B8A1234"))
        assertEquals("BAB1234", PlateOcrNormalizer.normalizeCandidate("BA81234"))
        assertEquals("BAB1234", PlateOcrNormalizer.normalizeCandidate("BA8 1234"))
        assertEquals("BB1234", PlateOcrNormalizer.normalizeCandidate("B81234"))
        assertEquals("BL1234", PlateOcrNormalizer.normalizeCandidate("8L 1234"))
        assertEquals("BCA1234B", PlateOcrNormalizer.normalizeCandidate("BCA12348"))
        assertEquals("BCA1234B", PlateOcrNormalizer.normalizeCandidate("BCA 1234 8"))
        assertEquals("BCA1234B", PlateOcrNormalizer.normalizeCandidate("BCA 1234 B"))

        // South Africa '8' <-> 'B' in prefix and suffix letters
        assertEquals("AB12CBGP", PlateOcrNormalizer.normalizeCandidate("A8 12 C8 GP"))
        assertEquals("AB12CBGP", PlateOcrNormalizer.normalizeCandidate("AB12C8GP"))

        // Botswana & Tanzania ('B 123 ABC' / 'T 123 ABC')
        assertEquals("B123ABC", PlateOcrNormalizer.normalizeCandidate("8 123 A8C"))
        assertEquals("B123ABC", PlateOcrNormalizer.normalizeCandidate("8123A8C"))
        assertEquals("T123ABB", PlateOcrNormalizer.normalizeCandidate("T123AB8"))

        // Namibia ('N 12345 WB')
        assertEquals("N12345WB", PlateOcrNormalizer.normalizeCandidate("N12345W8"))
        assertEquals("N12345WB", PlateOcrNormalizer.normalizeCandidate("N 12345 W8"))

        // Mozambique & Angola ('AAA 123 MB' / 'LD 12-34-AB')
        assertEquals("AAA123MB", PlateOcrNormalizer.normalizeCandidate("AAA123M8"))
        assertEquals("LD1234AB", PlateOcrNormalizer.normalizeCandidate("LD 12-34-A8"))

        // DR Congo ('1234 AB 01')
        assertEquals("1234AB01", PlateOcrNormalizer.normalizeCandidate("1234 A8 01"))
        assertEquals("1234AB01", PlateOcrNormalizer.normalizeCandidate("1234A801"))
    }

    @Test
    fun testInvalidCandidatesRejected() {
        // Words without sufficient digits or too short
        assertFalse(PlateOcrNormalizer.isValidPlate("VOLVO"))
        assertFalse(PlateOcrNormalizer.isValidPlate("STOP"))
        assertFalse(PlateOcrNormalizer.isValidPlate("12345"))
        assertFalse(PlateOcrNormalizer.isValidPlate("ABCDEF"))
    }

    @Test
    fun testIgnoredTransporterWords() {
        assertTrue(PlateOcrNormalizer.isIgnoredTransporterWord("DIESEL"))
        assertTrue(PlateOcrNormalizer.isIgnoredTransporterWord("LONG VEHICLE"))
        assertTrue(PlateOcrNormalizer.isIgnoredTransporterWord("SCANIA"))
        assertFalse(PlateOcrNormalizer.isIgnoredTransporterWord("Alistair Group"))
    }
}
