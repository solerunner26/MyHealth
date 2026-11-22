package app.myhealth.ingest

import app.myhealth.data.security.Crypto
import app.myhealth.domain.importing.ImportDraft
import app.myhealth.domain.importing.ImportDrafts
import app.myhealth.domain.importing.SourceFile
import app.myhealth.domain.model.FamilyMember
import app.myhealth.domain.parse.ReportParser
import app.myhealth.domain.repository.RecordRepository
import java.time.LocalDate

/** read → hash → duplicate check → extract text → parse → editable draft. Nothing is stored until the user saves. */
class ImportPipeline(private val extractor: TextExtractor, private val records: RecordRepository) {

    class Prepared(val draft: ImportDraft, val document: PickedDocument)

    suspend fun prepare(doc: PickedDocument, member: FamilyMember, today: LocalDate, defaultTitle: String, progress: ExtractionProgress): Prepared {
        val sha = Crypto.sha256Hex(doc.bytes)
        val duplicate = records.findBySha256(sha)
        val text = extractor.extract(doc, progress)
        val parsed = ReportParser(today).parse(text.pages, member.sex, member.ageYears(today))
        val source = SourceFile(doc.displayName, doc.kind, sha, doc.bytes.size.toLong(), text.pageCount, text.ocrPages)
        return Prepared(ImportDrafts.fromParsed(parsed, member, source, duplicate, defaultTitle), doc)
    }
}
