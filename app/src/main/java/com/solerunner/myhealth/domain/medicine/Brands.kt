package com.solerunner.myhealth.domain.medicine

import com.solerunner.myhealth.domain.model.MedicineForm

/**
 * A small, local brand → molecule table for the Indian market.
 *
 * Why it earns its place: a bill says `TAB. DOLO 650`, which tells the patient nothing about
 * duplication, but `Paracetamol` does. With the molecule known the app can say "this appears in two
 * prescriptions" and the user can hand the doctor a clean molecule list instead of a pile of brands.
 * The table is intentionally small and easily extended; when a brand is unknown the app simply keeps
 * the printed name.
 */
internal object Brands {

    private fun entry(form: MedicineForm, molecule: String) = form to molecule

    val table: Map<String, Pair<MedicineForm, String>> = mapOf(
        // Analgesics / antipyretics / NSAIDs
        "dolo" to entry(MedicineForm.TABLET, "Paracetamol"),
        "crocin" to entry(MedicineForm.TABLET, "Paracetamol"),
        "calpol" to entry(MedicineForm.SYRUP, "Paracetamol"),
        "combiflam" to entry(MedicineForm.TABLET, "Ibuprofen + Paracetamol"),
        "brufen" to entry(MedicineForm.TABLET, "Ibuprofen"),
        "zerodol" to entry(MedicineForm.TABLET, "Aceclofenac"),
        "nise" to entry(MedicineForm.TABLET, "Nimesulide"),
        "voveran" to entry(MedicineForm.TABLET, "Diclofenac"),
        "volini" to entry(MedicineForm.CREAM, "Diclofenac"),
        "etoshine" to entry(MedicineForm.TABLET, "Etoricoxib"),
        "ultracet" to entry(MedicineForm.TABLET, "Tramadol + Paracetamol"),
        "sumo" to entry(MedicineForm.TABLET, "Paracetamol + Nimesulide"),
        // Antibiotics
        "augmentin" to entry(MedicineForm.TABLET, "Amoxicillin + Clavulanic acid"),
        "clavam" to entry(MedicineForm.TABLET, "Amoxicillin + Clavulanic acid"),
        "moxikind" to entry(MedicineForm.CAPSULE, "Amoxicillin"),
        "azee" to entry(MedicineForm.TABLET, "Azithromycin"),
        "azithral" to entry(MedicineForm.TABLET, "Azithromycin"),
        "zithromax" to entry(MedicineForm.TABLET, "Azithromycin"),
        "levoflox" to entry(MedicineForm.TABLET, "Levofloxacin"),
        "cifran" to entry(MedicineForm.TABLET, "Ciprofloxacin"),
        "monocef" to entry(MedicineForm.INJECTION, "Ceftriaxone"),
        "taxim" to entry(MedicineForm.INJECTION, "Cefotaxime"),
        "mahaflox" to entry(MedicineForm.TABLET, "Ofloxacin"),
        "doxy" to entry(MedicineForm.CAPSULE, "Doxycycline"),
        "metrogyl" to entry(MedicineForm.TABLET, "Metronidazole"),
        "flagyl" to entry(MedicineForm.TABLET, "Metronidazole"),
        "norflox" to entry(MedicineForm.TABLET, "Norfloxacin"),
        // Acid suppression / gastro
        "pan" to entry(MedicineForm.TABLET, "Pantoprazole"),
        "pan-d" to entry(MedicineForm.CAPSULE, "Pantoprazole + Domperidone"),
        "pantocid" to entry(MedicineForm.TABLET, "Pantoprazole"),
        "omez" to entry(MedicineForm.CAPSULE, "Omeprazole"),
        "rantac" to entry(MedicineForm.TABLET, "Ranitidine"),
        "zinetac" to entry(MedicineForm.TABLET, "Ranitidine"),
        "rabium" to entry(MedicineForm.TABLET, "Rabeprazole"),
        "domstal" to entry(MedicineForm.TABLET, "Domperidone"),
        "emeset" to entry(MedicineForm.TABLET, "Ondansetron"),
        "digene" to entry(MedicineForm.SYRUP, "Antacid (Al/Mg hydroxide)"),
        "gelusil" to entry(MedicineForm.SYRUP, "Antacid (Al/Mg hydroxide)"),
        "cremaffin" to entry(MedicineForm.SYRUP, "Liquid paraffin + Milk of magnesia"),
        "duphalac" to entry(MedicineForm.SYRUP, "Lactulose"),
        "lopamide" to entry(MedicineForm.CAPSULE, "Loperamide"),
        // Cardiovascular
        "ecosprin" to entry(MedicineForm.TABLET, "Aspirin"),
        "clopilet" to entry(MedicineForm.TABLET, "Clopidogrel"),
        "atorva" to entry(MedicineForm.TABLET, "Atorvastatin"),
        "rosuvas" to entry(MedicineForm.TABLET, "Rosuvastatin"),
        "telma" to entry(MedicineForm.TABLET, "Telmisartan"),
        "losar" to entry(MedicineForm.TABLET, "Losartan"),
        "amlopres" to entry(MedicineForm.TABLET, "Amlodipine"),
        "metolar" to entry(MedicineForm.TABLET, "Metoprolol"),
        "nebicard" to entry(MedicineForm.TABLET, "Nebivolol"),
        "concor" to entry(MedicineForm.TABLET, "Bisoprolol"),
        "ramipril" to entry(MedicineForm.TABLET, "Ramipril"),
        "cardace" to entry(MedicineForm.TABLET, "Ramipril"),
        "lasix" to entry(MedicineForm.TABLET, "Furosemide"),
        "aldactone" to entry(MedicineForm.TABLET, "Spironolactone"),
        "cetzine" to entry(MedicineForm.TABLET, "Cetirizine"),
        // Diabetes
        "glycomet" to entry(MedicineForm.TABLET, "Metformin"),
        "glucophage" to entry(MedicineForm.TABLET, "Metformin"),
        "glimestar" to entry(MedicineForm.TABLET, "Glimepiride"),
        "amaryl" to entry(MedicineForm.TABLET, "Glimepiride"),
        "zoryl" to entry(MedicineForm.TABLET, "Glimepiride"),
        "januvia" to entry(MedicineForm.TABLET, "Sitagliptin"),
        "trajenta" to entry(MedicineForm.TABLET, "Linagliptin"),
        "galvus" to entry(MedicineForm.TABLET, "Vildagliptin"),
        "lantus" to entry(MedicineForm.INJECTION, "Insulin glargine"),
        "mixtard" to entry(MedicineForm.INJECTION, "Insulin (premix)"),
        "human mixtard" to entry(MedicineForm.INJECTION, "Insulin (premix)"),
        // Thyroid, vitamins, supplements
        "thyronorm" to entry(MedicineForm.TABLET, "Levothyroxine"),
        "eltroxin" to entry(MedicineForm.TABLET, "Levothyroxine"),
        "shelcal" to entry(MedicineForm.TABLET, "Calcium + Vitamin D3"),
        "uprise" to entry(MedicineForm.TABLET, "Cholecalciferol"),
        "folvite" to entry(MedicineForm.TABLET, "Folic acid"),
        "orofer" to entry(MedicineForm.TABLET, "Ferrous ascorbate"),
        "autrin" to entry(MedicineForm.CAPSULE, "Iron + Folic acid + B12"),
        "dexorange" to entry(MedicineForm.SYRUP, "Iron + Folic acid + B12"),
        "neurobion" to entry(MedicineForm.TABLET, "Vitamin B complex"),
        "becosules" to entry(MedicineForm.CAPSULE, "Vitamin B complex"),
        "limcee" to entry(MedicineForm.TABLET, "Vitamin C"),
        "zincovit" to entry(MedicineForm.TABLET, "Multivitamin + Zinc"),
        "supradyn" to entry(MedicineForm.TABLET, "Multivitamin"),
        // Respiratory / allergy
        "montek" to entry(MedicineForm.TABLET, "Montelukast"),
        "allegra" to entry(MedicineForm.TABLET, "Fexofenadine"),
        "levocet" to entry(MedicineForm.TABLET, "Levocetirizine"),
        "avil" to entry(MedicineForm.TABLET, "Pheniramine"),
        "asthalin" to entry(MedicineForm.INHALER, "Salbutamol"),
        "foracort" to entry(MedicineForm.INHALER, "Budesonide + Formoterol"),
        "seroflo" to entry(MedicineForm.INHALER, "Salmeterol + Fluticasone"),
        "deriphyllin" to entry(MedicineForm.TABLET, "Etophylline + Theophylline"),
        // Neuro / psych
        "nexito" to entry(MedicineForm.TABLET, "Escitalopram"),
        "alprax" to entry(MedicineForm.TABLET, "Alprazolam"),
        "zapiz" to entry(MedicineForm.TABLET, "Clonazepam"),
        "vertin" to entry(MedicineForm.TABLET, "Betahistine"),
        "stemetil" to entry(MedicineForm.TABLET, "Prochlorperazine"),
        "suminat" to entry(MedicineForm.TABLET, "Sumatriptan"),
        "pregabid" to entry(MedicineForm.CAPSULE, "Pregabalin"),
        "nucoxia" to entry(MedicineForm.TABLET, "Etoricoxib"),
    ).mapKeys { it.key.lowercase() }
}
