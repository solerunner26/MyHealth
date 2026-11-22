package com.solerunner.myhealth.domain.lab

import com.solerunner.myhealth.domain.model.Sex
import com.solerunner.myhealth.domain.model.ValueStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BiomarkerCatalogTest {

    @Test
    fun `catalog has a broad, unique analyte set`() {
        val all = BiomarkerCatalog.all
        assertTrue("expected at least 90 analytes, found ${all.size}", all.size >= 90)
        assertEquals(all.size, all.map { it.key }.distinct().size)
        assertTrue(all.all { it.aliases.isNotEmpty() })
        assertTrue(all.all { it.displayName.isNotBlank() })
    }

    @Test
    fun `looks analytes up by key and by alias`() {
        assertEquals("HbA1c (Glycated Haemoglobin)", BiomarkerCatalog.require("hba1c").displayName)
        val match = BiomarkerCatalog.match(BiomarkerCatalog.normalize("Haemoglobin (Hb) 13.2 g/dL"))
        assertNotNull(match)
        assertEquals("hemoglobin", match!!.biomarker.key)
    }

    @Test
    fun `converts printed units into the canonical unit`() {
        val glucose = BiomarkerCatalog.require("fasting_glucose")
        assertEquals(118.0, glucose.toCanonical(6.55, "mmol/L"), 0.2)
        assertEquals(118.0, glucose.toCanonical(118.0, "mg/dL"), 0.001)
    }

    @Test
    fun `classifies values against sex specific ranges`() {
        val creatinine = BiomarkerCatalog.require("creatinine")
        assertEquals(ValueStatus.IN_RANGE, creatinine.classify(1.0, Sex.MALE))
        assertEquals(ValueStatus.HIGH, creatinine.classify(1.5, Sex.MALE))
        assertEquals(ValueStatus.IN_RANGE, creatinine.classify(1.05, Sex.FEMALE))
        assertEquals(ValueStatus.VERY_HIGH, creatinine.classify(2.6, Sex.MALE))
    }

    @Test
    fun `knows which direction is good`() {
        assertEquals(
            com.solerunner.myhealth.domain.model.DirectionOfGood.LOWER,
            BiomarkerCatalog.require("ldl_cholesterol").directionOfGood,
        )
        assertEquals(
            com.solerunner.myhealth.domain.model.DirectionOfGood.HIGHER,
            BiomarkerCatalog.require("vitamin_d").directionOfGood,
        )
    }
}
