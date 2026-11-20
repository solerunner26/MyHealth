package com.solerunner.myhealth.domain.lab

import com.solerunner.myhealth.domain.model.AnalyteCategory
import com.solerunner.myhealth.domain.model.DirectionOfGood
import com.solerunner.myhealth.domain.model.Sex

/**
 * Offline knowledge base of everything My Health can recognise in a lab report.
 *
 * Design notes
 * * Aliases are matched case-insensitively against a normalised form of the OCR line, so
 *   "HAEMOGLOBIN (Hb)", "Hemoglobin" and "Hb" all land on `hemoglobin`.
 * * [Biomarker.unitConversions] maps a printed unit to the canonical one by multiplication, which is
 *   what makes a 3-year table comparable when a lab switches from mg/dL to mmol/L.
 * * Reference ranges are adult, India-typical values; the report's own printed range always wins when
 *   it is present, and the user can see both.
 */
object BiomarkerCatalog {

    /** Compact builder: keeps the table below readable and cheap to extend. */
    private fun bm(
        key: String,
        name: String,
        cat: AnalyteCategory,
        unit: String?,
        low: Double? = null,
        high: Double? = null,
        sexRanges: List<ReferenceRange> = emptyList(),
        dir: DirectionOfGood = DirectionOfGood.UNKNOWN,
        conv: Map<String, Double> = emptyMap(),
        decimals: Int = 2,
        recheck: Int? = null,
        critLow: Double? = null,
        critHigh: Double? = null,
        band: Boolean = false,
        vararg aliases: String,
    ) = Biomarker(
        key = key,
        displayName = name,
        category = cat,
        canonicalUnit = unit,
        aliases = aliases.toList(),
        ranges = if (sexRanges.isNotEmpty()) sexRanges else listOf(ReferenceRange(low, high)),
        directionOfGood = dir,
        unitConversions = conv,
        decimals = decimals,
        recheckMonths = recheck,
        criticalLow = critLow,
        criticalHigh = critHigh,
        bandTarget = band,
    )

    // ---------------------------------------------------------------------------------------
    // Haematology
    // ---------------------------------------------------------------------------------------
    private val hematology = listOf(
        bm(
            "hemoglobin", "Haemoglobin (Hb)", AnalyteCategory.HEMATOLOGY, "g/dL",
            sexRanges = listOf(
                ReferenceRange(13.0, 17.0, Sex.MALE),
                ReferenceRange(12.0, 15.0, Sex.FEMALE),
                ReferenceRange(12.0, 17.0),
            ),
            dir = DirectionOfGood.HIGHER, conv = mapOf("g/L" to 0.1), decimals = 1, recheck = 12,
            critLow = 7.0, critHigh = 20.0, aliases = *arrayOf("hemoglobin", "haemoglobin", "hb", "hgb", "h.g.b", "haemoglobin (hb)", "hemoglobin (hb)"),
        ),
        bm(
            "rbc_count", "RBC Count", AnalyteCategory.HEMATOLOGY, "mill/µL",
            sexRanges = listOf(
                ReferenceRange(4.5, 5.9, Sex.MALE),
                ReferenceRange(4.1, 5.1, Sex.FEMALE),
                ReferenceRange(4.1, 5.9),
            ),
            band = true, conv = mapOf("10^6/µl" to 1.0, "x10^6/µl" to 1.0), decimals = 2,
            aliases = *arrayOf("rbc count", "total rbc", "r.b.c. count", "erythrocyte count", "rbc"),
        ),
        bm(
            "wbc_count", "Total Leucocyte Count (TLC)", AnalyteCategory.HEMATOLOGY, "/µL",
            low = 4000.0, high = 11000.0, band = true, conv = mapOf("10^3/µl" to 1000.0, "x10^3/µl" to 1000.0, "cells/µl" to 1.0),
            decimals = 0, critLow = 2000.0, critHigh = 20000.0,
            aliases = *arrayOf("total leucocyte count", "total leukocyte count", "tlc", "wbc count", "white blood cell count", "wbc", "leucocytes"),
        ),
        bm(
            "platelet_count", "Platelet Count", AnalyteCategory.HEMATOLOGY, "/µL",
            low = 150000.0, high = 450000.0, band = true, conv = mapOf("lakh/µl" to 100000.0, "10^3/µl" to 1000.0),
            decimals = 0, critLow = 50000.0, critHigh = 1000000.0,
            aliases = *arrayOf("platelet count", "platelets", "plt", "thrombocyte count"),
        ),
        bm(
            "hematocrit", "Haematocrit (PCV)", AnalyteCategory.HEMATOLOGY, "%",
            sexRanges = listOf(ReferenceRange(40.0, 50.0, Sex.MALE), ReferenceRange(36.0, 46.0, Sex.FEMALE), ReferenceRange(36.0, 50.0)),
            dir = DirectionOfGood.HIGHER, decimals = 1,
            aliases = *arrayOf("hematocrit", "haematocrit", "pcv", "packed cell volume", "hct"),
        ),
        bm("mcv", "MCV", AnalyteCategory.HEMATOLOGY, "fL", 80.0, 100.0, band = true, decimals = 1, aliases = *arrayOf("mcv", "mean corpuscular volume")),
        bm("mch", "MCH", AnalyteCategory.HEMATOLOGY, "pg", 27.0, 32.0, band = true, decimals = 1, aliases = *arrayOf("mch", "mean corpuscular haemoglobin", "mean corpuscular hemoglobin")),
        bm("mchc", "MCHC", AnalyteCategory.HEMATOLOGY, "g/dL", 32.0, 36.0, band = true, decimals = 1, aliases = *arrayOf("mchc", "mean corpuscular haemoglobin concentration")),
        bm("rdw", "RDW", AnalyteCategory.HEMATOLOGY, "%", 11.5, 14.5, band = true, decimals = 1, aliases = *arrayOf("rdw", "rdw-cv", "red cell distribution width")),
        bm("neutrophils", "Neutrophils", AnalyteCategory.HEMATOLOGY, "%", 40.0, 80.0, band = true, decimals = 1, aliases = *arrayOf("neutrophils", "neutrophil", "neutrophil %")),
        bm("lymphocytes", "Lymphocytes", AnalyteCategory.HEMATOLOGY, "%", 20.0, 40.0, band = true, decimals = 1, aliases = *arrayOf("lymphocytes", "lymphocyte", "lymphocyte %")),
        bm("eosinophils", "Eosinophils", AnalyteCategory.HEMATOLOGY, "%", 1.0, 6.0, band = true, decimals = 1, aliases = *arrayOf("eosinophils", "eosinophil", "eosinophil %")),
        bm("monocytes", "Monocytes", AnalyteCategory.HEMATOLOGY, "%", 2.0, 10.0, band = true, decimals = 1, aliases = *arrayOf("monocytes", "monocyte", "monocyte %")),
        bm("basophils", "Basophils", AnalyteCategory.HEMATOLOGY, "%", 0.0, 2.0, band = true, decimals = 1, aliases = *arrayOf("basophils", "basophil", "basophil %")),
        bm("absolute_neutrophil_count", "Absolute Neutrophil Count", AnalyteCategory.HEMATOLOGY, "/µL", 2000.0, 7000.0, band = true, decimals = 0, aliases = *arrayOf("absolute neutrophil count", "anc", "neutrophils absolute")),
        bm("absolute_lymphocyte_count", "Absolute Lymphocyte Count", AnalyteCategory.HEMATOLOGY, "/µL", 1000.0, 3000.0, band = true, decimals = 0, aliases = *arrayOf("absolute lymphocyte count", "alc", "lymphocytes absolute")),
        bm("esr", "ESR", AnalyteCategory.INFLAMMATION, "mm/hr", sexRanges = listOf(ReferenceRange(0.0, 15.0, Sex.MALE), ReferenceRange(0.0, 20.0, Sex.FEMALE), ReferenceRange(0.0, 20.0)), dir = DirectionOfGood.LOWER, decimals = 0, aliases = *arrayOf("esr", "erythrocyte sedimentation rate", "sed rate")),
    )

    // ---------------------------------------------------------------------------------------
    // Diabetes / metabolic
    // ---------------------------------------------------------------------------------------
    private val metabolic = listOf(
        bm("hba1c", "HbA1c (Glycated Haemoglobin)", AnalyteCategory.DIABETES, "%", 4.0, 5.6, dir = DirectionOfGood.LOWER, conv = mapOf("mmol/mol" to 0.09148), decimals = 1, recheck = 3, critHigh = 10.0, aliases = *arrayOf("hba1c", "hb a1c", "glycosylated hemoglobin", "glycated haemoglobin", "a1c")),
        bm("fasting_glucose", "Fasting Blood Sugar", AnalyteCategory.DIABETES, "mg/dL", 70.0, 100.0, dir = DirectionOfGood.LOWER, conv = mapOf("mmol/l" to 18.0182), decimals = 0, recheck = 3, critLow = 54.0, critHigh = 250.0, aliases = *arrayOf("fasting blood sugar", "fasting glucose", "fbs", "glucose fasting", "fasting plasma glucose", "fpg", "blood sugar fasting")),
        bm("postprandial_glucose", "Post Prandial Blood Sugar", AnalyteCategory.DIABETES, "mg/dL", 70.0, 140.0, dir = DirectionOfGood.LOWER, conv = mapOf("mmol/l" to 18.0182), decimals = 0, recheck = 3, critHigh = 300.0, aliases = *arrayOf("post prandial blood sugar", "postprandial glucose", "ppbs", "pp blood sugar", "2 hr post glucose", "glucose post prandial")),
        bm("random_glucose", "Random Blood Sugar", AnalyteCategory.DIABETES, "mg/dL", 70.0, 140.0, dir = DirectionOfGood.LOWER, conv = mapOf("mmol/l" to 18.0182), decimals = 0, critLow = 54.0, critHigh = 300.0, aliases = *arrayOf("random blood sugar", "rbs", "random glucose", "blood sugar random")),
        bm("fasting_insulin", "Fasting Insulin", AnalyteCategory.HORMONES, "µIU/mL", 2.6, 24.9, band = true, decimals = 1, aliases = *arrayOf("fasting insulin", "insulin fasting", "insulin")),
        bm("homocysteine", "Homocysteine", AnalyteCategory.CARDIAC, "µmol/L", 5.0, 15.0, dir = DirectionOfGood.LOWER, decimals = 1, aliases = *arrayOf("homocysteine", "serum homocysteine")),
    )

    // ---------------------------------------------------------------------------------------
    // Lipids
    // ---------------------------------------------------------------------------------------
    private val lipids = listOf(
        bm("total_cholesterol", "Total Cholesterol", AnalyteCategory.LIPIDS, "mg/dL", null, 200.0, dir = DirectionOfGood.LOWER, conv = mapOf("mmol/l" to 38.67), decimals = 0, recheck = 12, critHigh = 300.0, aliases = *arrayOf("total cholesterol", "cholesterol total", "serum cholesterol", "cholesterol")),
        bm("ldl_cholesterol", "LDL Cholesterol", AnalyteCategory.LIPIDS, "mg/dL", null, 100.0, dir = DirectionOfGood.LOWER, conv = mapOf("mmol/l" to 38.67), decimals = 0, recheck = 12, critHigh = 190.0, aliases = *arrayOf("ldl cholesterol", "ldl-c", "ldl", "bad cholesterol", "direct ldl")),
        bm("hdl_cholesterol", "HDL Cholesterol", AnalyteCategory.LIPIDS, "mg/dL", 40.0, null, dir = DirectionOfGood.HIGHER, conv = mapOf("mmol/l" to 38.67), decimals = 0, recheck = 12, critLow = 25.0, aliases = *arrayOf("hdl cholesterol", "hdl-c", "hdl", "good cholesterol")),
        bm("triglycerides", "Triglycerides", AnalyteCategory.LIPIDS, "mg/dL", null, 150.0, dir = DirectionOfGood.LOWER, conv = mapOf("mmol/l" to 88.57), decimals = 0, recheck = 12, critHigh = 500.0, aliases = *arrayOf("triglycerides", "triglyceride", "tg", "serum triglycerides")),
        bm("vldl_cholesterol", "VLDL Cholesterol", AnalyteCategory.LIPIDS, "mg/dL", 2.0, 30.0, band = true, decimals = 0, aliases = *arrayOf("vldl cholesterol", "vldl", "vldl-c")),
        bm("non_hdl_cholesterol", "Non-HDL Cholesterol", AnalyteCategory.LIPIDS, "mg/dL", null, 130.0, dir = DirectionOfGood.LOWER, decimals = 0, aliases = *arrayOf("non hdl cholesterol", "non-hdl", "non hdl")),
        bm("chol_hdl_ratio", "Total Cholesterol / HDL Ratio", AnalyteCategory.LIPIDS, "ratio", null, 4.5, dir = DirectionOfGood.LOWER, decimals = 1, aliases = *arrayOf("cholesterol hdl ratio", "total cholesterol hdl ratio", "chol/hdl ratio", "t.chol/hdl")),
        bm("ldl_hdl_ratio", "LDL / HDL Ratio", AnalyteCategory.LIPIDS, "ratio", null, 3.5, dir = DirectionOfGood.LOWER, decimals = 1, aliases = *arrayOf("ldl hdl ratio", "ldl/hdl ratio")),
        bm("apolipoprotein_b", "Apolipoprotein B", AnalyteCategory.LIPIDS, "mg/dL", null, 90.0, dir = DirectionOfGood.LOWER, decimals = 0, aliases = *arrayOf("apolipoprotein b", "apob", "apo b")),
        bm("lipoprotein_a", "Lipoprotein (a)", AnalyteCategory.LIPIDS, "mg/dL", null, 30.0, dir = DirectionOfGood.LOWER, decimals = 1, aliases = *arrayOf("lipoprotein a", "lp(a)", "lpa")),
    )

    // ---------------------------------------------------------------------------------------
    // Liver
    // ---------------------------------------------------------------------------------------
    private val liver = listOf(
        bm("bilirubin_total", "Bilirubin Total", AnalyteCategory.LIVER, "mg/dL", 0.3, 1.2, dir = DirectionOfGood.LOWER, conv = mapOf("µmol/l" to 0.0585), decimals = 2, critHigh = 5.0, aliases = *arrayOf("bilirubin total", "total bilirubin", "bilirubin (total)", "tbili")),
        bm("bilirubin_direct", "Bilirubin Direct", AnalyteCategory.LIVER, "mg/dL", 0.0, 0.3, dir = DirectionOfGood.LOWER, conv = mapOf("µmol/l" to 0.0585), decimals = 2, aliases = *arrayOf("bilirubin direct", "direct bilirubin", "conjugated bilirubin")),
        bm("bilirubin_indirect", "Bilirubin Indirect", AnalyteCategory.LIVER, "mg/dL", 0.1, 0.8, dir = DirectionOfGood.LOWER, decimals = 2, aliases = *arrayOf("bilirubin indirect", "indirect bilirubin", "unconjugated bilirubin")),
        bm("sgpt", "SGPT (ALT)", AnalyteCategory.LIVER, "U/L", 0.0, 41.0, dir = DirectionOfGood.LOWER, decimals = 0, recheck = 12, critHigh = 400.0, aliases = *arrayOf("sgpt", "alt", "alanine aminotransferase", "sgpt (alt)", "alanine transaminase")),
        bm("sgot", "SGOT (AST)", AnalyteCategory.LIVER, "U/L", 0.0, 40.0, dir = DirectionOfGood.LOWER, decimals = 0, recheck = 12, critHigh = 400.0, aliases = *arrayOf("sgot", "ast", "aspartate aminotransferase", "sgot (ast)", "aspartate transaminase")),
        bm("alkaline_phosphatase", "Alkaline Phosphatase", AnalyteCategory.LIVER, "U/L", 40.0, 129.0, band = true, decimals = 0, aliases = *arrayOf("alkaline phosphatase", "alp", "s.alkaline phosphatase", "alk phos")),
        bm("ggt", "Gamma GT (GGT)", AnalyteCategory.LIVER, "U/L", sexRanges = listOf(ReferenceRange(10.0, 71.0, Sex.MALE), ReferenceRange(6.0, 42.0, Sex.FEMALE), ReferenceRange(6.0, 71.0)), dir = DirectionOfGood.LOWER, decimals = 0, aliases = *arrayOf("gamma gt", "ggt", "gamma glutamyl transferase", "gamma gt (ggt)")),
        bm("total_protein", "Total Protein", AnalyteCategory.LIVER, "g/dL", 6.4, 8.3, band = true, decimals = 2, aliases = *arrayOf("total protein", "serum total protein", "proteins total")),
        bm("albumin", "Albumin", AnalyteCategory.LIVER, "g/dL", 3.5, 5.2, dir = DirectionOfGood.HIGHER, decimals = 2, aliases = *arrayOf("albumin", "serum albumin", "albumin serum")),
        bm("globulin", "Globulin", AnalyteCategory.LIVER, "g/dL", 2.0, 3.5, band = true, decimals = 2, aliases = *arrayOf("globulin", "serum globulin")),
        bm("ag_ratio", "Albumin / Globulin Ratio", AnalyteCategory.LIVER, "ratio", 1.0, 2.2, band = true, decimals = 2, aliases = *arrayOf("albumin globulin ratio", "a/g ratio", "ag ratio", "alb/glob ratio")),
    )

    // ---------------------------------------------------------------------------------------
    // Kidney, electrolytes, minerals
    // ---------------------------------------------------------------------------------------
    private val kidney = listOf(
        bm("urea", "Blood Urea", AnalyteCategory.KIDNEY, "mg/dL", 15.0, 40.0, dir = DirectionOfGood.LOWER, conv = mapOf("mmol/l" to 6.0), decimals = 0, critHigh = 120.0, aliases = *arrayOf("blood urea", "urea", "serum urea", "urea (blood)")),
        bm("bun", "Blood Urea Nitrogen (BUN)", AnalyteCategory.KIDNEY, "mg/dL", 7.0, 20.0, dir = DirectionOfGood.LOWER, conv = mapOf("mmol/l" to 2.8), decimals = 0, aliases = *arrayOf("blood urea nitrogen", "bun", "urea nitrogen")),
        bm("creatinine", "Serum Creatinine", AnalyteCategory.KIDNEY, "mg/dL", sexRanges = listOf(ReferenceRange(0.7, 1.3, Sex.MALE), ReferenceRange(0.6, 1.1, Sex.FEMALE), ReferenceRange(0.6, 1.3)), dir = DirectionOfGood.LOWER, conv = mapOf("µmol/l" to 0.0113), decimals = 2, recheck = 12, critHigh = 2.5, aliases = *arrayOf("serum creatinine", "creatinine", "s. creatinine", "creatinine serum")),
        bm("egfr", "eGFR", AnalyteCategory.KIDNEY, "mL/min/1.73m2", 90.0, null, dir = DirectionOfGood.HIGHER, decimals = 0, critLow = 45.0, aliases = *arrayOf("egfr", "estimated gfr", "gfr", "e-gfr")),
        bm("uric_acid", "Uric Acid", AnalyteCategory.KIDNEY, "mg/dL", sexRanges = listOf(ReferenceRange(3.4, 7.0, Sex.MALE), ReferenceRange(2.4, 6.0, Sex.FEMALE), ReferenceRange(2.4, 7.0)), dir = DirectionOfGood.LOWER, conv = mapOf("µmol/l" to 0.0168), decimals = 1, critHigh = 10.0, aliases = *arrayOf("uric acid", "serum uric acid", "s. uric acid")),
        bm("sodium", "Sodium", AnalyteCategory.ELECTROLYTES, "mmol/L", 136.0, 145.0, band = true, decimals = 0, critLow = 125.0, critHigh = 155.0, aliases = *arrayOf("sodium", "na+", "serum sodium", "na")),
        bm("potassium", "Potassium", AnalyteCategory.ELECTROLYTES, "mmol/L", 3.5, 5.1, band = true, decimals = 1, critLow = 2.8, critHigh = 6.0, aliases = *arrayOf("potassium", "k+", "serum potassium", "k")),
        bm("chloride", "Chloride", AnalyteCategory.ELECTROLYTES, "mmol/L", 98.0, 107.0, band = true, decimals = 0, aliases = *arrayOf("chloride", "cl-", "serum chloride")),
        bm("calcium", "Calcium", AnalyteCategory.ELECTROLYTES, "mg/dL", 8.6, 10.2, band = true, conv = mapOf("mmol/l" to 4.008), decimals = 1, critLow = 7.0, critHigh = 12.0, aliases = *arrayOf("calcium", "serum calcium", "total calcium", "ca++")),
        bm("phosphorus", "Phosphorus", AnalyteCategory.ELECTROLYTES, "mg/dL", 2.5, 4.5, band = true, conv = mapOf("mmol/l" to 3.097), decimals = 1, aliases = *arrayOf("phosphorus", "phosphate", "inorganic phosphorus", "serum phosphate")),
        bm("magnesium", "Magnesium", AnalyteCategory.ELECTROLYTES, "mg/dL", 1.7, 2.4, band = true, conv = mapOf("mmol/l" to 2.43), decimals = 2, aliases = *arrayOf("magnesium", "serum magnesium")),
        bm("bicarbonate", "Bicarbonate", AnalyteCategory.ELECTROLYTES, "mmol/L", 22.0, 29.0, band = true, decimals = 0, aliases = *arrayOf("bicarbonate", "hco3", "total co2")),
    )

    // ---------------------------------------------------------------------------------------
    // Thyroid, vitamins, hormones
    // ---------------------------------------------------------------------------------------
    private val thyroid = listOf(
        bm("tsh", "TSH", AnalyteCategory.THYROID, "µIU/mL", 0.4, 4.0, band = true, decimals = 2, recheck = 6, critHigh = 10.0, aliases = *arrayOf("tsh", "thyroid stimulating hormone", "thyrotropin")),
        bm("t3_total", "T3 Total", AnalyteCategory.THYROID, "ng/dL", 80.0, 200.0, band = true, conv = mapOf("nmol/l" to 65.1), decimals = 0, aliases = *arrayOf("t3 total", "total t3", "triiodothyronine", "t3")),
        bm("t4_total", "T4 Total", AnalyteCategory.THYROID, "µg/dL", 5.1, 14.1, band = true, conv = mapOf("nmol/l" to 0.0777), decimals = 2, aliases = *arrayOf("t4 total", "total t4", "thyroxine", "t4")),
        bm("ft3", "Free T3", AnalyteCategory.THYROID, "pg/mL", 2.3, 4.2, band = true, decimals = 2, aliases = *arrayOf("free t3", "ft3", "ft-3")),
        bm("ft4", "Free T4", AnalyteCategory.THYROID, "ng/dL", 0.8, 1.8, band = true, decimals = 2, aliases = *arrayOf("free t4", "ft4", "ft-4")),
        bm("anti_tpo", "Anti TPO Antibody", AnalyteCategory.THYROID, "IU/mL", null, 34.0, dir = DirectionOfGood.LOWER, decimals = 1, aliases = *arrayOf("anti tpo", "anti-tpo", "thyroid peroxidase antibody", "tpo ab")),
    )

    private val vitamins = listOf(
        bm("vitamin_d", "Vitamin D (25-OH)", AnalyteCategory.VITAMINS, "ng/mL", 30.0, 100.0, dir = DirectionOfGood.HIGHER, conv = mapOf("nmol/l" to 0.4), decimals = 1, recheck = 12, critLow = 10.0, critHigh = 150.0, aliases = *arrayOf("vitamin d", "25 oh vitamin d", "25-hydroxy vitamin d", "vitamin d3", "25 hydroxy cholecalciferol", "vit d")),
        bm("vitamin_b12", "Vitamin B12", AnalyteCategory.VITAMINS, "pg/mL", 200.0, 900.0, dir = DirectionOfGood.HIGHER, conv = mapOf("pmol/l" to 1.355), decimals = 0, recheck = 12, critLow = 120.0, aliases = *arrayOf("vitamin b12", "vit b12", "cobalamin", "cyanocobalamin", "b12")),
        bm("folate", "Folate (Vitamin B9)", AnalyteCategory.VITAMINS, "ng/mL", 3.1, 17.5, dir = DirectionOfGood.HIGHER, conv = mapOf("nmol/l" to 0.4413, "µg/l" to 1.0), decimals = 1, aliases = *arrayOf("folate", "folic acid", "vitamin b9", "serum folate")),
        bm("iron", "Serum Iron", AnalyteCategory.VITAMINS, "µg/dL", sexRanges = listOf(ReferenceRange(65.0, 175.0, Sex.MALE), ReferenceRange(50.0, 170.0, Sex.FEMALE), ReferenceRange(50.0, 175.0)), dir = DirectionOfGood.HIGHER, conv = mapOf("µmol/l" to 5.587), decimals = 0, aliases = *arrayOf("serum iron", "iron", "iron serum", "s. iron")),
        bm("ferritin", "Ferritin", AnalyteCategory.VITAMINS, "ng/mL", sexRanges = listOf(ReferenceRange(30.0, 400.0, Sex.MALE), ReferenceRange(13.0, 150.0, Sex.FEMALE), ReferenceRange(13.0, 400.0)), dir = DirectionOfGood.HIGHER, decimals = 0, recheck = 12, critLow = 10.0, aliases = *arrayOf("ferritin", "serum ferritin", "s. ferritin")),
        bm("tibc", "TIBC", AnalyteCategory.VITAMINS, "µg/dL", 250.0, 450.0, band = true, decimals = 0, aliases = *arrayOf("tibc", "total iron binding capacity")),
        bm("transferrin_saturation", "Transferrin Saturation", AnalyteCategory.VITAMINS, "%", 20.0, 50.0, band = true, decimals = 1, aliases = *arrayOf("transferrin saturation", "tsat", "iron saturation")),
        bm("vitamin_b6", "Vitamin B6", AnalyteCategory.VITAMINS, "ng/mL", 5.0, 50.0, band = true, decimals = 1, aliases = *arrayOf("vitamin b6", "pyridoxal phosphate")),
        bm("zinc", "Zinc", AnalyteCategory.VITAMINS, "µg/dL", 70.0, 120.0, band = true, decimals = 0, aliases = *arrayOf("zinc", "serum zinc")),
    )

    private val hormones = listOf(
        bm("testosterone_total", "Testosterone Total", AnalyteCategory.HORMONES, "ng/dL", sexRanges = listOf(ReferenceRange(300.0, 1000.0, Sex.MALE), ReferenceRange(15.0, 70.0, Sex.FEMALE), ReferenceRange(15.0, 1000.0)), dir = DirectionOfGood.HIGHER, conv = mapOf("nmol/l" to 28.84), decimals = 0, aliases = *arrayOf("testosterone total", "total testosterone", "testosterone")),
        bm("estradiol", "Estradiol (E2)", AnalyteCategory.HORMONES, "pg/mL", sexRanges = listOf(ReferenceRange(10.0, 40.0, Sex.MALE), ReferenceRange(19.0, 500.0, Sex.FEMALE)), band = true, decimals = 1, aliases = *arrayOf("estradiol", "e2", "oestradiol")),
        bm("prolactin", "Prolactin", AnalyteCategory.HORMONES, "ng/mL", sexRanges = listOf(ReferenceRange(4.0, 15.0, Sex.MALE), ReferenceRange(4.0, 23.0, Sex.FEMALE), ReferenceRange(4.0, 23.0)), band = true, decimals = 1, aliases = *arrayOf("prolactin", "prl", "serum prolactin")),
        bm("cortisol_morning", "Cortisol (Morning)", AnalyteCategory.HORMONES, "µg/dL", 6.2, 19.4, band = true, conv = mapOf("nmol/l" to 0.0363), decimals = 1, aliases = *arrayOf("cortisol morning", "cortisol (am)", "serum cortisol", "cortisol")),
        bm("lh", "LH", AnalyteCategory.HORMONES, "mIU/mL", sexRanges = listOf(ReferenceRange(1.7, 8.6, Sex.MALE), ReferenceRange(2.4, 12.6, Sex.FEMALE), ReferenceRange(1.7, 12.6)), band = true, decimals = 1, aliases = *arrayOf("lh", "luteinizing hormone", "lutenizing hormone")),
        bm("fsh", "FSH", AnalyteCategory.HORMONES, "mIU/mL", sexRanges = listOf(ReferenceRange(1.5, 12.4, Sex.MALE), ReferenceRange(3.5, 12.5, Sex.FEMALE), ReferenceRange(1.5, 12.5)), band = true, decimals = 1, aliases = *arrayOf("fsh", "follicle stimulating hormone")),
        bm("pth", "Parathyroid Hormone", AnalyteCategory.HORMONES, "pg/mL", 15.0, 65.0, band = true, decimals = 1, aliases = *arrayOf("parathyroid hormone", "pth", "intact pth")),
        bm("vitamin_d_binding", "Vitamin D Binding Protein", AnalyteCategory.HORMONES, "mg/L", 200.0, 550.0, band = true, decimals = 0, aliases = *arrayOf("vitamin d binding protein", "dbp")),
    )

    private val cardiac = listOf(
        bm("troponin_i", "Troponin I", AnalyteCategory.CARDIAC, "ng/mL", null, 0.04, dir = DirectionOfGood.LOWER, decimals = 3, critHigh = 0.5, aliases = *arrayOf("troponin i", "troponin-i", "hs troponin i", "trop i")),
        bm("troponin_t", "Troponin T", AnalyteCategory.CARDIAC, "ng/mL", null, 0.014, dir = DirectionOfGood.LOWER, decimals = 3, critHigh = 0.1, aliases = *arrayOf("troponin t", "hs troponin t", "trop t")),
        bm("ck_mb", "CK-MB", AnalyteCategory.CARDIAC, "ng/mL", null, 5.0, dir = DirectionOfGood.LOWER, decimals = 2, aliases = *arrayOf("ck-mb", "ck mb", "creatine kinase mb")),
        bm("bnp", "BNP", AnalyteCategory.CARDIAC, "pg/mL", null, 100.0, dir = DirectionOfGood.LOWER, decimals = 0, critHigh = 400.0, aliases = *arrayOf("bnp", "brain natriuretic peptide", "nt probnp", "nt-probnp", "pro bnp")),
        bm("ck_total", "Creatine Kinase Total", AnalyteCategory.CARDIAC, "U/L", sexRanges = listOf(ReferenceRange(39.0, 308.0, Sex.MALE), ReferenceRange(26.0, 192.0, Sex.FEMALE)), band = true, decimals = 0, aliases = *arrayOf("creatine kinase total", "ck total", "cpk", "creatine phosphokinase")),
        bm("ldh", "Lactate Dehydrogenase", AnalyteCategory.CARDIAC, "U/L", 140.0, 280.0, band = true, decimals = 0, aliases = *arrayOf("lactate dehydrogenase", "ldh", "s. ldh")),
    )

    private val inflammation = listOf(
        bm("crp", "C-Reactive Protein", AnalyteCategory.INFLAMMATION, "mg/L", null, 5.0, dir = DirectionOfGood.LOWER, decimals = 2, critHigh = 50.0, aliases = *arrayOf("c-reactive protein", "c reactive protein", "crp", "crp quantitative")),
        bm("hs_crp", "hs-CRP", AnalyteCategory.INFLAMMATION, "mg/L", null, 3.0, dir = DirectionOfGood.LOWER, decimals = 2, aliases = *arrayOf("hs-crp", "hs crp", "high sensitivity crp")),
        bm("procalcitonin", "Procalcitonin", AnalyteCategory.INFLAMMATION, "ng/mL", null, 0.5, dir = DirectionOfGood.LOWER, decimals = 2, critHigh = 10.0, aliases = *arrayOf("procalcitonin", "pct")),
        bm("esr_westergren", "ESR (Westergren)", AnalyteCategory.INFLAMMATION, "mm/hr", null, 20.0, dir = DirectionOfGood.LOWER, decimals = 0, aliases = *arrayOf("esr westergren", "westergren esr")),
    )

    private val urine = listOf(
        bm("urine_ph", "Urine pH", AnalyteCategory.URINE, "", 4.6, 8.0, band = true, decimals = 1, aliases = *arrayOf("urine ph", "ph (urine)", "reaction")),
        bm("urine_specific_gravity", "Urine Specific Gravity", AnalyteCategory.URINE, "", 1.005, 1.03, band = true, decimals = 3, aliases = *arrayOf("specific gravity", "urine specific gravity", "sp. gravity")),
        bm("urine_protein", "Urine Protein", AnalyteCategory.URINE, "mg/dL", null, 15.0, dir = DirectionOfGood.LOWER, decimals = 0, aliases = *arrayOf("urine protein", "protein (urine)", "albumin (urine)")),
        bm("urine_glucose", "Urine Glucose", AnalyteCategory.URINE, "", null, 0.0, dir = DirectionOfGood.LOWER, decimals = 0, aliases = *arrayOf("urine glucose", "glucose (urine)", "sugar (urine)")),
        bm("urine_wbc", "Urine Pus Cells (WBC)", AnalyteCategory.URINE, "/hpf", null, 5.0, dir = DirectionOfGood.LOWER, decimals = 0, aliases = *arrayOf("pus cells", "urine wbc", "wbc (urine)", "leucocytes (urine)")),
        bm("urine_rbc", "Urine RBC", AnalyteCategory.URINE, "/hpf", null, 2.0, dir = DirectionOfGood.LOWER, decimals = 0, aliases = *arrayOf("urine rbc", "rbc (urine)", "red blood cells (urine)")),
        bm("urine_ketones", "Urine Ketones", AnalyteCategory.URINE, "", null, 0.0, dir = DirectionOfGood.LOWER, decimals = 0, aliases = *arrayOf("urine ketones", "ketone bodies", "ketones")),
        bm("urine_microalbumin", "Urine Microalbumin", AnalyteCategory.URINE, "mg/L", null, 30.0, dir = DirectionOfGood.LOWER, decimals = 1, aliases = *arrayOf("microalbumin", "urine microalbumin", "albumin creatinine ratio", "acr")),
    )

    private val coagulation = listOf(
        bm("pt", "Prothrombin Time (PT)", AnalyteCategory.HEMATOLOGY, "sec", 11.0, 13.5, band = true, decimals = 1, critHigh = 25.0, aliases = *arrayOf("prothrombin time", "pt", "pt test")),
        bm("inr", "INR", AnalyteCategory.HEMATOLOGY, "ratio", 0.8, 1.1, band = true, decimals = 2, critHigh = 4.0, aliases = *arrayOf("inr", "international normalised ratio", "international normalized ratio")),
        bm("aptt", "aPTT", AnalyteCategory.HEMATOLOGY, "sec", 25.0, 35.0, band = true, decimals = 1, aliases = *arrayOf("aptt", "a.p.t.t", "activated partial thromboplastin time", "ptt")),
        bm("d_dimer", "D-Dimer", AnalyteCategory.HEMATOLOGY, "ng/mL", null, 500.0, dir = DirectionOfGood.LOWER, decimals = 0, aliases = *arrayOf("d-dimer", "d dimer", "d-dimer (feu)")),
        bm("fibrinogen", "Fibrinogen", AnalyteCategory.HEMATOLOGY, "mg/dL", 200.0, 400.0, band = true, decimals = 0, aliases = *arrayOf("fibrinogen", "plasma fibrinogen")),
    )

    private val other = listOf(
        bm("psa_total", "PSA Total", AnalyteCategory.OTHER, "ng/mL", null, 4.0, dir = DirectionOfGood.LOWER, decimals = 2, aliases = *arrayOf("psa total", "prostate specific antigen", "psa")),
        bm("ca_125", "CA-125", AnalyteCategory.OTHER, "U/mL", null, 35.0, dir = DirectionOfGood.LOWER, decimals = 1, aliases = *arrayOf("ca-125", "ca 125", "cancer antigen 125")),
        bm("cea", "CEA", AnalyteCategory.OTHER, "ng/mL", null, 5.0, dir = DirectionOfGood.LOWER, decimals = 2, aliases = *arrayOf("cea", "carcinoembryonic antigen")),
        bm("amylase", "Amylase", AnalyteCategory.OTHER, "U/L", 28.0, 100.0, band = true, decimals = 0, aliases = *arrayOf("amylase", "serum amylase")),
        bm("lipase", "Lipase", AnalyteCategory.OTHER, "U/L", 13.0, 60.0, band = true, decimals = 0, aliases = *arrayOf("lipase", "serum lipase")),
        bm("total_ige", "Total IgE", AnalyteCategory.OTHER, "IU/mL", null, 100.0, dir = DirectionOfGood.LOWER, decimals = 1, aliases = *arrayOf("total ige", "ige", "immunoglobulin e")),
        bm("vitamin_a", "Vitamin A", AnalyteCategory.VITAMINS, "µg/dL", 30.0, 80.0, band = true, decimals = 0, aliases = *arrayOf("vitamin a", "retinol")),
    )

    /** Everything the catalog knows, in a stable order (category order matters for the UI). */
    val all: List<Biomarker> = hematology + metabolic + lipids + liver + kidney + thyroid + vitamins +
        hormones + cardiac + inflammation + urine + coagulation + other

    private val byKeyIndex: Map<String, Biomarker> = all.associateBy { it.key }

    fun byKey(key: String?): Biomarker? = key?.let { byKeyIndex[it] }

    fun require(key: String): Biomarker = byKeyIndex.getValue(key)

    fun ofCategory(category: AnalyteCategory): List<Biomarker> = all.filter { it.category == category }

    data class Match(val biomarker: Biomarker, val alias: String, val start: Int, val end: Int)

    /** Normalised form used for both the alias index and incoming OCR text. */
    fun normalize(text: String): String = text.lowercase()
        .replace('µ', 'u')
        .replace(Regex("[^a-z0-9%./]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private val aliasIndex: List<Pair<String, Biomarker>> = all
        .flatMap { biomarker -> biomarker.aliases.map { normalize(it) to biomarker } }
        .filter { it.first.isNotEmpty() }
        .sortedByDescending { it.first.length }

    /**
     * Finds the biomarker mentioned in [normalizedLine]. The longest alias wins, which is what keeps
     * "total cholesterol" from being read as "cholesterol" and "free t4" from being read as "t4".
     */
    fun match(normalizedLine: String): Match? {
        if (normalizedLine.isBlank()) return null
        val padded = " " + normalizedLine + " "
        for ((alias, biomarker) in aliasIndex) {
            val index = padded.indexOf(" $alias ")
            if (index >= 0) return Match(biomarker, alias, index, index + alias.length + 1)
        }
        return null
    }
}
