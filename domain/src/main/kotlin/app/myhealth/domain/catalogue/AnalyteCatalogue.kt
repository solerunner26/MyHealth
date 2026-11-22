package app.myhealth.domain.catalogue

import app.myhealth.domain.catalogue.Conversion.Affine
import app.myhealth.domain.catalogue.Conversion.Factor
import app.myhealth.domain.catalogue.Direction.HigherIsBetter
import app.myhealth.domain.catalogue.Direction.InRange
import app.myhealth.domain.catalogue.Direction.LowerIsBetter
import app.myhealth.domain.catalogue.Units.EGFR
import app.myhealth.domain.catalogue.Units.FL
import app.myhealth.domain.catalogue.Units.G_DL
import app.myhealth.domain.catalogue.Units.G_L
import app.myhealth.domain.catalogue.Units.K_UL
import app.myhealth.domain.catalogue.Units.LAKH_UL
import app.myhealth.domain.catalogue.Units.MEQ_L
import app.myhealth.domain.catalogue.Units.MG_DL
import app.myhealth.domain.catalogue.Units.MG_G
import app.myhealth.domain.catalogue.Units.MG_L
import app.myhealth.domain.catalogue.Units.MMOL_L
import app.myhealth.domain.catalogue.Units.MMOL_MOL
import app.myhealth.domain.catalogue.Units.MM_HR
import app.myhealth.domain.catalogue.Units.M_UL
import app.myhealth.domain.catalogue.Units.NG_DL
import app.myhealth.domain.catalogue.Units.NG_ML
import app.myhealth.domain.catalogue.Units.NMOL_L
import app.myhealth.domain.catalogue.Units.PERCENT
import app.myhealth.domain.catalogue.Units.PER_UL
import app.myhealth.domain.catalogue.Units.PG
import app.myhealth.domain.catalogue.Units.PG_ML
import app.myhealth.domain.catalogue.Units.PMOL_L
import app.myhealth.domain.catalogue.Units.RATIO
import app.myhealth.domain.catalogue.Units.UG_DL
import app.myhealth.domain.catalogue.Units.UIU_ML
import app.myhealth.domain.catalogue.Units.UMOL_L
import app.myhealth.domain.catalogue.Units.U_L
import app.myhealth.domain.model.Sex.Female
import app.myhealth.domain.model.Sex.Male

/**
 * Built-in analyte catalogue. Reference ranges are typical adult values used only when a report does not print its
 * own range (a printed range always wins, because it is specific to the lab's method). Aliases are written in the
 * parser's normalised form: lower case, punctuation replaced by spaces.
 */
object AnalyteCatalogue {
    private fun r(low: Double?, high: Double?) = RefRange(low, high)
    private fun m(low: Double?, high: Double?) = RefRange(low, high, Male)
    private fun f(low: Double?, high: Double?) = RefRange(low, high, Female)

    private val cholesterolMmol = mapOf(MMOL_L to Factor(38.67))

    val all: List<Analyte> = listOf(
        // ---- Blood count
        Analyte("hb", "Haemoglobin", listOf("hemoglobin", "haemoglobin", "hb", "hgb", "hb%"), Panel.Blood, G_DL,
            mapOf(G_L to Factor(0.1), MMOL_L to Factor(1.611)), listOf(m(13.0, 17.0), f(12.0, 15.5), r(12.0, 17.0)),
            InRange, 7.0, 20.0, 12, 3, 5.0, keyMarker = true),
        Analyte("rbc", "Red blood cell count", listOf("rbc", "rbc count", "red blood cell count", "red blood cells", "total rbc count", "erythrocyte count", "red cell count"),
            Panel.Blood, M_UL, emptyMap(), listOf(m(4.5, 5.5), f(3.8, 4.8), r(3.8, 5.5)), decimals = 2),
        Analyte("hct", "Haematocrit (PCV)", listOf("hematocrit", "haematocrit", "hct", "pcv", "packed cell volume"), Panel.Blood, PERCENT,
            ranges = listOf(m(40.0, 50.0), f(36.0, 46.0), r(36.0, 50.0))),
        Analyte("mcv", "MCV", listOf("mcv", "mean corpuscular volume", "mean cell volume"), Panel.Blood, FL, ranges = listOf(r(83.0, 101.0))),
        Analyte("mch", "MCH", listOf("mch", "mean corpuscular hemoglobin", "mean corpuscular haemoglobin", "mean cell hemoglobin"), Panel.Blood, PG,
            ranges = listOf(r(27.0, 32.0))),
        Analyte("mchc", "MCHC", listOf("mchc", "mean corpuscular hemoglobin concentration", "mean corpuscular haemoglobin concentration", "mean cell hemoglobin concentration"),
            Panel.Blood, G_DL, mapOf(G_L to Factor(0.1)), listOf(r(31.5, 34.5))),
        Analyte("rdw", "RDW-CV", listOf("rdw", "rdw cv", "rdw-cv", "red cell distribution width"), Panel.Blood, PERCENT, ranges = listOf(r(11.6, 14.0))),
        Analyte("wbc", "White blood cell count", listOf("wbc", "wbc count", "tlc", "total leucocyte count", "total leukocyte count", "total wbc count", "white blood cell count", "white blood cells", "leucocyte count", "leukocyte count", "total count"),
            Panel.Blood, K_UL, mapOf(PER_UL to Factor(0.001), LAKH_UL to Factor(100.0)), listOf(r(4.0, 11.0)), InRange, 2.0, 30.0, 12, 3, keyMarker = false),
        Analyte("neut", "Neutrophils", listOf("neutrophils", "neutrophil", "polymorphs", "segmented neutrophils", "neutrophils %"), Panel.Blood, PERCENT, ranges = listOf(r(40.0, 80.0))),
        Analyte("lymph", "Lymphocytes", listOf("lymphocytes", "lymphocyte", "lymphocytes %"), Panel.Blood, PERCENT, ranges = listOf(r(20.0, 40.0))),
        Analyte("mono", "Monocytes", listOf("monocytes", "monocyte"), Panel.Blood, PERCENT, ranges = listOf(r(2.0, 10.0))),
        Analyte("eos", "Eosinophils", listOf("eosinophils", "eosinophil"), Panel.Blood, PERCENT, ranges = listOf(r(1.0, 6.0))),
        Analyte("baso", "Basophils", listOf("basophils", "basophil"), Panel.Blood, PERCENT, ranges = listOf(r(0.0, 2.0))),
        Analyte("plt", "Platelet count", listOf("platelet count", "platelets", "platelet", "plt", "thrombocyte count", "total platelet count"),
            Panel.Blood, K_UL, mapOf(PER_UL to Factor(0.001), LAKH_UL to Factor(100.0)), listOf(r(150.0, 410.0)), InRange, 50.0, 1000.0, 12, 3, decimals = 0),
        Analyte("esr", "ESR", listOf("esr", "erythrocyte sedimentation rate", "sedimentation rate"), Panel.Inflammation, MM_HR,
            ranges = listOf(m(0.0, 15.0), f(0.0, 20.0), r(0.0, 20.0)), direction = LowerIsBetter, decimals = 0),

        // ---- Diabetes
        Analyte("fbs", "Fasting glucose", listOf("fasting blood sugar", "blood sugar fasting", "fbs", "fasting plasma glucose", "plasma glucose fasting", "glucose fasting", "fasting glucose", "fbg", "glucose f", "sugar fasting", "fpg"),
            Panel.Diabetes, MG_DL, mapOf(MMOL_L to Factor(18.016)), listOf(r(70.0, 99.0)), LowerIsBetter, 54.0, 400.0, 12, 3, decimals = 0, keyMarker = true),
        Analyte("ppbs", "Post-meal glucose", listOf("post prandial blood sugar", "postprandial blood sugar", "ppbs", "blood sugar pp", "glucose pp", "post prandial glucose", "plasma glucose pp", "glucose post prandial", "pp blood sugar", "2 hr pp", "ppbg"),
            Panel.Diabetes, MG_DL, mapOf(MMOL_L to Factor(18.016)), listOf(r(70.0, 140.0)), LowerIsBetter, 54.0, 400.0, decimals = 0),
        Analyte("rbs", "Random glucose", listOf("random blood sugar", "rbs", "glucose random", "random glucose", "blood sugar random", "glucose", "blood glucose", "blood sugar", "plasma glucose"),
            Panel.Diabetes, MG_DL, mapOf(MMOL_L to Factor(18.016)), listOf(r(70.0, 140.0)), LowerIsBetter, 54.0, 400.0, decimals = 0),
        Analyte("hba1c", "HbA1c", listOf("hba1c", "hb a1c", "glycated hemoglobin", "glycated haemoglobin", "glycosylated hemoglobin", "glycosylated haemoglobin", "a1c", "glycohemoglobin"),
            Panel.Diabetes, PERCENT, mapOf(MMOL_MOL to Affine(0.09148, 2.152)), listOf(r(4.0, 5.6)), LowerIsBetter, null, null, 12, 3, 5.0, keyMarker = true),
        Analyte("eag", "Estimated average glucose", listOf("estimated average glucose", "eag", "mean blood glucose", "average blood glucose", "mean plasma glucose"),
            Panel.Diabetes, MG_DL, mapOf(MMOL_L to Factor(18.016)), listOf(r(70.0, 126.0)), LowerIsBetter, decimals = 0),

        // ---- Lipids
        Analyte("tc", "Total cholesterol", listOf("total cholesterol", "cholesterol total", "cholesterol", "serum cholesterol", "s cholesterol", "t cholesterol"),
            Panel.Lipids, MG_DL, cholesterolMmol, listOf(r(null, 200.0)), LowerIsBetter, null, null, 12, 6, decimals = 0, keyMarker = true),
        Analyte("hdl", "HDL cholesterol", listOf("hdl cholesterol", "hdl", "hdl c", "hdl-c", "cholesterol hdl", "hdl direct", "high density lipoprotein"),
            Panel.Lipids, MG_DL, cholesterolMmol, listOf(m(40.0, null), f(50.0, null), r(40.0, null)), HigherIsBetter, null, null, 12, 6, decimals = 0),
        Analyte("ldl", "LDL cholesterol", listOf("ldl cholesterol", "ldl", "ldl c", "ldl-c", "cholesterol ldl", "ldl direct", "ldl calculated", "low density lipoprotein"),
            Panel.Lipids, MG_DL, cholesterolMmol, listOf(r(null, 100.0)), LowerIsBetter, null, null, 12, 6, decimals = 0, keyMarker = true),
        Analyte("vldl", "VLDL cholesterol", listOf("vldl cholesterol", "vldl", "vldl c", "very low density lipoprotein"), Panel.Lipids, MG_DL, cholesterolMmol,
            listOf(r(2.0, 30.0)), LowerIsBetter, decimals = 0),
        Analyte("tg", "Triglycerides", listOf("triglycerides", "triglyceride", "tg", "serum triglycerides", "trigs"), Panel.Lipids, MG_DL,
            mapOf(MMOL_L to Factor(88.57)), listOf(r(null, 150.0)), LowerIsBetter, null, 1000.0, 12, 6, decimals = 0, keyMarker = true),
        Analyte("nonhdl", "Non-HDL cholesterol", listOf("non hdl cholesterol", "non-hdl cholesterol", "non hdl", "non-hdl", "non hdl c"), Panel.Lipids, MG_DL, cholesterolMmol,
            listOf(r(null, 130.0)), LowerIsBetter, decimals = 0),
        Analyte("tchdl", "Total cholesterol / HDL ratio", listOf("total cholesterol/hdl ratio", "cholesterol/hdl ratio", "tc/hdl ratio", "chol/hdl ratio", "total cholesterol hdl ratio", "tc hdl ratio", "chol hdl ratio", "cholesterol hdl ratio"),
            Panel.Lipids, RATIO, ranges = listOf(r(null, 5.0)), direction = LowerIsBetter),
        Analyte("ldlhdl", "LDL / HDL ratio", listOf("ldl/hdl ratio", "ldl hdl ratio", "ldl/hdl"), Panel.Lipids, RATIO, ranges = listOf(r(null, 3.5)), direction = LowerIsBetter),

        // ---- Kidney
        Analyte("creat", "Creatinine", listOf("creatinine", "serum creatinine", "s creatinine", "creatinine serum"), Panel.Kidney, MG_DL,
            mapOf(UMOL_L to Factor(1 / 88.4)), listOf(m(0.7, 1.3), f(0.6, 1.1), r(0.6, 1.3)), LowerIsBetter, null, 10.0, 12, 6, 10.0, 2, keyMarker = true),
        Analyte("urea", "Urea", listOf("urea", "blood urea", "serum urea", "urea serum"), Panel.Kidney, MG_DL, mapOf(MMOL_L to Factor(6.006)),
            listOf(r(17.0, 43.0)), LowerIsBetter, decimals = 0),
        Analyte("bun", "Blood urea nitrogen", listOf("bun", "blood urea nitrogen", "urea nitrogen"), Panel.Kidney, MG_DL, mapOf(MMOL_L to Factor(2.801)),
            listOf(r(7.0, 20.0)), LowerIsBetter),
        Analyte("ua", "Uric acid", listOf("uric acid", "serum uric acid", "s uric acid"), Panel.Kidney, MG_DL, mapOf(UMOL_L to Factor(1 / 59.48)),
            listOf(m(3.5, 7.2), f(2.6, 6.0), r(2.6, 7.2)), LowerIsBetter, null, null, 12, 6),
        Analyte("egfr", "eGFR", listOf("egfr", "estimated gfr", "e gfr", "glomerular filtration rate", "estimated glomerular filtration rate"), Panel.Kidney, EGFR,
            ranges = listOf(r(60.0, null)), direction = HigherIsBetter, criticalLow = 15.0, monitorMonthsNormal = 12, monitorMonthsAbnormal = 6, rapidChangePctPerMonth = 5.0, decimals = 0, keyMarker = true),
        Analyte("uacr", "Urine albumin/creatinine ratio", listOf("albumin/creatinine ratio", "albumin creatinine ratio", "uacr", "acr", "microalbumin/creatinine ratio", "urine acr"),
            Panel.Urine, MG_G, ranges = listOf(r(null, 30.0)), direction = LowerIsBetter, decimals = 0),

        // ---- Electrolytes
        Analyte("na", "Sodium", listOf("sodium", "serum sodium", "na+", "s sodium"), Panel.Electrolytes, MMOL_L, mapOf(MEQ_L to Factor(1.0)),
            listOf(r(136.0, 145.0)), InRange, 120.0, 160.0, decimals = 0),
        Analyte("k", "Potassium", listOf("potassium", "serum potassium", "k+", "s potassium"), Panel.Electrolytes, MMOL_L, mapOf(MEQ_L to Factor(1.0)),
            listOf(r(3.5, 5.1)), InRange, 2.8, 6.2),
        Analyte("cl", "Chloride", listOf("chloride", "serum chloride", "cl-"), Panel.Electrolytes, MMOL_L, mapOf(MEQ_L to Factor(1.0)),
            listOf(r(98.0, 107.0)), decimals = 0),
        Analyte("hco3", "Bicarbonate", listOf("bicarbonate", "hco3", "total co2", "serum bicarbonate"), Panel.Electrolytes, MMOL_L, mapOf(MEQ_L to Factor(1.0)),
            listOf(r(22.0, 29.0)), decimals = 0),
        Analyte("ca", "Calcium", listOf("calcium", "serum calcium", "total calcium", "s calcium"), Panel.Electrolytes, MG_DL, mapOf(MMOL_L to Factor(4.008)),
            listOf(r(8.6, 10.3)), InRange, 6.5, 13.0),
        Analyte("mg", "Magnesium", listOf("magnesium", "serum magnesium", "mg++"), Panel.Electrolytes, MG_DL, mapOf(MMOL_L to Factor(2.431)), listOf(r(1.7, 2.4))),
        Analyte("phos", "Phosphorus", listOf("phosphorus", "inorganic phosphorus", "phosphate", "serum phosphorus"), Panel.Electrolytes, MG_DL,
            mapOf(MMOL_L to Factor(3.097)), listOf(r(2.5, 4.5))),

        // ---- Liver
        Analyte("tbil", "Total bilirubin", listOf("total bilirubin", "bilirubin total", "serum bilirubin total", "s bilirubin total", "bilirubin", "t bilirubin"), Panel.Liver, MG_DL,
            mapOf(UMOL_L to Factor(1 / 17.1)), listOf(r(0.3, 1.2)), LowerIsBetter, null, 15.0, decimals = 2),
        Analyte("dbil", "Direct bilirubin", listOf("direct bilirubin", "bilirubin direct", "conjugated bilirubin", "d bilirubin", "bilirubin conjugated"), Panel.Liver, MG_DL,
            mapOf(UMOL_L to Factor(1 / 17.1)), listOf(r(0.0, 0.3)), LowerIsBetter, decimals = 2),
        Analyte("ibil", "Indirect bilirubin", listOf("indirect bilirubin", "bilirubin indirect", "unconjugated bilirubin"), Panel.Liver, MG_DL,
            mapOf(UMOL_L to Factor(1 / 17.1)), listOf(r(0.1, 1.0)), LowerIsBetter, decimals = 2),
        Analyte("ast", "AST (SGOT)", listOf("sgot", "ast", "aspartate aminotransferase", "aspartate transaminase", "ast sgot", "sgot ast", "s g o t"), Panel.Liver, U_L,
            ranges = listOf(m(0.0, 40.0), f(0.0, 32.0), r(0.0, 40.0)), direction = LowerIsBetter, monitorMonthsNormal = 12, monitorMonthsAbnormal = 3, decimals = 0, keyMarker = true),
        Analyte("alt", "ALT (SGPT)", listOf("sgpt", "alt", "alanine aminotransferase", "alanine transaminase", "alt sgpt", "sgpt alt", "s g p t"), Panel.Liver, U_L,
            ranges = listOf(m(0.0, 41.0), f(0.0, 33.0), r(0.0, 41.0)), direction = LowerIsBetter, monitorMonthsNormal = 12, monitorMonthsAbnormal = 3, decimals = 0, keyMarker = true),
        Analyte("alp", "Alkaline phosphatase", listOf("alkaline phosphatase", "alp", "alk phos", "serum alkaline phosphatase"), Panel.Liver, U_L, ranges = listOf(r(44.0, 147.0)), decimals = 0),
        Analyte("ggt", "GGT", listOf("ggt", "gamma gt", "gamma glutamyl transferase", "ggtp", "gamma glutamyl transpeptidase"), Panel.Liver, U_L,
            ranges = listOf(m(10.0, 71.0), f(6.0, 42.0), r(6.0, 71.0)), direction = LowerIsBetter, decimals = 0),
        Analyte("tp", "Total protein", listOf("total protein", "protein total", "serum protein", "total proteins", "s protein total"), Panel.Liver, G_DL,
            mapOf(G_L to Factor(0.1)), listOf(r(6.4, 8.3))),
        Analyte("alb", "Albumin", listOf("albumin", "serum albumin", "s albumin"), Panel.Liver, G_DL, mapOf(G_L to Factor(0.1)), listOf(r(3.5, 5.2)), HigherIsBetter),
        Analyte("glob", "Globulin", listOf("globulin", "serum globulin"), Panel.Liver, G_DL, mapOf(G_L to Factor(0.1)), listOf(r(2.0, 3.5))),
        Analyte("agr", "Albumin/globulin ratio", listOf("a/g ratio", "albumin/globulin ratio", "a g ratio", "albumin globulin ratio"), Panel.Liver, RATIO, ranges = listOf(r(1.0, 2.1))),

        // ---- Thyroid
        Analyte("tsh", "TSH", listOf("tsh", "thyroid stimulating hormone", "tsh ultrasensitive", "ultrasensitive tsh", "tsh 3rd generation", "s tsh"), Panel.Thyroid, UIU_ML,
            ranges = listOf(r(0.27, 4.2)), criticalHigh = 50.0, monitorMonthsNormal = 12, monitorMonthsAbnormal = 3, decimals = 2, keyMarker = true),
        Analyte("t3", "Total T3", listOf("t3", "total t3", "triiodothyronine", "t3 total", "tri iodothyronine"), Panel.Thyroid, NG_DL,
            mapOf(NMOL_L to Factor(65.1), NG_ML to Factor(100.0)), listOf(r(80.0, 200.0)), decimals = 0),
        Analyte("t4", "Total T4", listOf("t4", "total t4", "thyroxine", "t4 total"), Panel.Thyroid, UG_DL, mapOf(NMOL_L to Factor(1 / 12.87)), listOf(r(5.1, 14.1))),
        Analyte("ft3", "Free T3", listOf("free t3", "ft3", "free triiodothyronine"), Panel.Thyroid, PG_ML, mapOf(PMOL_L to Factor(0.651)), listOf(r(2.0, 4.4)), decimals = 2),
        Analyte("ft4", "Free T4", listOf("free t4", "ft4", "free thyroxine"), Panel.Thyroid, NG_DL, mapOf(PMOL_L to Factor(1 / 12.87)), listOf(r(0.93, 1.7)), decimals = 2),

        // ---- Vitamins / iron
        Analyte("vitd", "Vitamin D (25-OH)", listOf("vitamin d", "vitamin d3", "25 oh vitamin d", "25-oh vitamin d", "25 hydroxy vitamin d", "vitamin d 25 hydroxy", "vit d", "25 hydroxyvitamin d", "vitamin d total", "25-hydroxy vitamin d"),
            Panel.Vitamins, NG_ML, mapOf(NMOL_L to Factor(0.4006)), listOf(r(30.0, 100.0)), HigherIsBetter, null, 150.0, 12, 3, keyMarker = true),
        Analyte("b12", "Vitamin B12", listOf("vitamin b12", "vit b12", "b12", "cyanocobalamin", "cobalamin", "vitamin b 12"), Panel.Vitamins, PG_ML,
            mapOf(PMOL_L to Factor(1.355)), listOf(r(211.0, 911.0)), HigherIsBetter, null, null, 12, 6, decimals = 0),
        Analyte("folate", "Folate", listOf("folate", "folic acid", "serum folate"), Panel.Vitamins, NG_ML, mapOf(NMOL_L to Factor(0.4413)), listOf(r(4.6, 18.7)), HigherIsBetter),
        Analyte("ferritin", "Ferritin", listOf("ferritin", "serum ferritin", "s ferritin"), Panel.Iron, NG_ML, ranges = listOf(m(30.0, 400.0), f(13.0, 150.0), r(13.0, 400.0)), decimals = 0),
        Analyte("iron", "Serum iron", listOf("serum iron", "iron", "s iron", "iron serum"), Panel.Iron, UG_DL, mapOf(UMOL_L to Factor(5.585)),
            listOf(m(65.0, 175.0), f(50.0, 170.0), r(50.0, 175.0)), decimals = 0),
        Analyte("tibc", "TIBC", listOf("tibc", "total iron binding capacity"), Panel.Iron, UG_DL, mapOf(UMOL_L to Factor(5.585)), listOf(r(250.0, 450.0)), decimals = 0),
        Analyte("tsat", "Transferrin saturation", listOf("transferrin saturation", "tsat", "iron saturation", "% saturation"), Panel.Iron, PERCENT, ranges = listOf(r(20.0, 50.0)), decimals = 0),

        // ---- Inflammation / other
        Analyte("crp", "C-reactive protein", listOf("crp", "c reactive protein", "c-reactive protein", "hs crp", "hscrp", "hs-crp", "high sensitivity crp", "crp quantitative"), Panel.Inflammation, MG_L,
            mapOf(Units.MG_DL to Factor(10.0)), listOf(r(0.0, 5.0)), LowerIsBetter),
        Analyte("psa", "PSA (total)", listOf("psa", "total psa", "prostate specific antigen", "psa total"), Panel.Hormones, NG_ML,
            ranges = listOf(RefRange(0.0, 4.0, Male, 18, 200)), direction = LowerIsBetter, monitorMonthsNormal = 12, monitorMonthsAbnormal = 6, decimals = 2),
    )

    private val byId = all.associateBy { it.id }
    fun byId(id: String): Analyte? = byId[id]

    /** (alias, analyte) sorted longest alias first; used for deterministic matching. */
    val aliasIndex: List<Pair<String, Analyte>> = all.flatMap { a -> (a.aliases + a.name.lowercase()).distinct().map { it to a } }
        .sortedByDescending { it.first.length }
}

/** Terms that look like a catalogue alias but are a different test; a longer ignored match suppresses the row. */
object IgnoredTerms {
    val terms = listOf(
        "free psa", "urine glucose", "urine sugar", "urine protein", "urine albumin", "vitamin k", "free t4 index", "t3 uptake",
        "absolute neutrophil count", "absolute lymphocyte count", "absolute eosinophil count", "absolute monocyte count", "absolute basophil count",
        "mean platelet volume", "platelet distribution width", "plateletcrit", "glucose tolerance", "insulin", "c peptide",
        "reticulocyte count", "sample type", "method", "specimen", "page", "reference range", "biological reference",
    )
}
