package projektor

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.zaxxer.hikari.HikariDataSource
import io.ktor.application.Application
import io.ktor.config.MapApplicationConfig
import io.ktor.util.KtorExperimentalAPI
import java.math.BigDecimal
import kotlin.test.AfterTest
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.awaitility.kotlin.await
import org.awaitility.kotlin.until
import org.jooq.DSLContext
import org.koin.ktor.ext.get
import projektor.database.generated.tables.daos.*
import projektor.parser.ResultsXmlLoader
import projektor.server.api.PublicId

@KtorExperimentalAPI
open class ApplicationTestCase {
    val resultsXmlLoader = ResultsXmlLoader()

    val objectMapper: ObjectMapper = ObjectMapper()
            .registerKotlinModule()
            .registerModule(JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    lateinit var dataSource: HikariDataSource
    lateinit var dslContext: DSLContext
    lateinit var testRunDao: TestRunDao
    lateinit var testSuiteGroupDao: TestSuiteGroupDao
    lateinit var testSuiteDao: TestSuiteDao
    lateinit var testCaseDao: TestCaseDao
    lateinit var testFailureDao: TestFailureDao
    lateinit var attachmentDao: TestRunAttachmentDao
    lateinit var testRunSystemAttributesDao: TestRunSystemAttributesDao
    lateinit var testRunDBGenerator: TestRunDBGenerator
    lateinit var application: Application

    protected var databaseSchema: String = "public"

    protected var publishToken: String? = null

    protected var attachmentsEnabled: Boolean? = null
    protected var attachmentsMaxSizeMB: BigDecimal? = null

    protected var cleanupMaxAgeDays: Int? = null

    @ObsoleteCoroutinesApi
    fun createTestApplication(application: Application) {
        val schema = databaseSchema

        (application.environment.config as MapApplicationConfig).apply {
            // Set here the properties
            put("ktor.datasource.username", System.getenv("DB_USERNAME") ?: "testuser")
            put("ktor.datasource.password", System.getenv("DB_PASSWORD") ?: "testpass")
            put("ktor.datasource.jdbcUrl", System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5433/projektordb")
            put("ktor.datasource.schema", schema)

            publishToken?.let { put("ktor.auth.publishToken", it) }

            attachmentsEnabled?.let {
                put("ktor.attachment.url", "http://localhost:9000")
                put("ktor.attachment.bucketName", "attachmentstesting")
                put("ktor.attachment.autoCreateBucket", "true")
                put("ktor.attachment.accessKey", "minio_access_key")
                put("ktor.attachment.secretKey", "minio_secret_key")
                attachmentsMaxSizeMB?.let { put("ktor.attachment.maxSizeMB", it.toString()) }
            }

            cleanupMaxAgeDays?.let {
                put("ktor.cleanup.maxReportAgeDays", it.toString())
            }
        }

        application.main()

        dataSource = application.get()
        dslContext = application.get()
        testRunDao = TestRunDao(dslContext.configuration())
        testSuiteGroupDao = TestSuiteGroupDao(dslContext.configuration())
        testSuiteDao = TestSuiteDao(dslContext.configuration())
        testCaseDao = TestCaseDao(dslContext.configuration())
        testFailureDao = TestFailureDao(dslContext.configuration())
        attachmentDao = TestRunAttachmentDao(dslContext.configuration())
        testRunSystemAttributesDao = TestRunSystemAttributesDao(dslContext.configuration())
        testRunDBGenerator = TestRunDBGenerator(testRunDao, testSuiteGroupDao, testSuiteDao, testCaseDao, testFailureDao)

        this.application = application
    }

    fun waitUntilTestRunHasAttachments(publicId: PublicId, attachmentCount: Int) {
        await until { attachmentDao.fetchByTestRunPublicId(publicId.id).size == attachmentCount }
    }

    @AfterTest
    fun closeDataSource() {
        dataSource.close()
    }
}
