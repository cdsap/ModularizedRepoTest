import com.google.cloud.bigquery.InsertAllRequest
import io.github.cdsap.talaiot.entities.ExecutionReport
import io.github.cdsap.talaiot.publisher.Publisher


buildscript {
    repositories {
        mavenCentral()
        //   mavenLocal()
    }
    dependencies {
        //classpath(platform("com.google.cloud:libraries-bom:26.22.0"))
        classpath("com.google.cloud:google-cloud-bigquery:2.7.0")

    }
}
plugins {
    id("com.autonomousapps.dependency-analysis") version "1.21.0"
    id("org.jetbrains.kotlin.jvm") version ("1.9.10") apply false
    id("com.android.application") version "8.1.1" apply false
    id("com.android.library") version "8.1.1" apply false
    id("io.github.cdsap.talaiot.plugin.base") version "2.0.3"
}

talaiot {
    publishers {
        jsonPublisher = true

        customPublishers(BiqQueryPublisher())
    }
}
data class ModuleDuration(
    val module: String,
    val executed: Boolean,
    val duration: Long,
    val durationModule: Long,
    val modulesExecuted: Int
)

class BiqQueryPublisher : Publisher {
    override fun publish(report: ExecutionReport) {
        val duration = report.durationMs
        val durations = mutableListOf<ModuleDuration>()
        var modulesUpdated = 0
        report.tasks?.groupBy { it.module }?.forEach {
            if(it.value.any { it.state == io.github.cdsap.talaiot.entities.TaskMessageState.EXECUTED }){
                modulesUpdated++
            }
        }

        report.tasks!!.groupBy { it.module }.forEach {
            val a =
                it.value.filter {
                    it.state == io.github.cdsap.talaiot.entities.TaskMessageState.EXECUTED }
            println("${it.key}  -- ${a.size} -- $modulesUpdated")
            durations.add(
                ModuleDuration(
                    it.key,
                    it.value.any { it.state == io.github.cdsap.talaiot.entities.TaskMessageState.EXECUTED },
                    duration?.toLong()!!,
                    it.value.sumOf { it.ms },
                    modulesUpdated
                )
            )
        }

        val table =
            com.google.cloud.bigquery.TableId.of("mobile_build_metrics", "builds")
        val client = com.google.cloud.bigquery.BigQueryOptions.newBuilder()
            .setCredentials(
                com.google.auth.oauth2.GoogleCredentials.fromStream(
                    java.io.FileInputStream(
                        "./key.json"
                    )
                )
            )
            .build()
            .service
        val row = mutableListOf<Map<String, Any>>()
        durations.forEach {
            val rowContent = mutableMapOf<String, Any>()
            rowContent["module"] = it.module
            rowContent["duration"] = it.duration
            rowContent["executed"] = it.executed
            rowContent["durationModule"] = it.durationModule
            rowContent["modulesExecuted"] = it.durationModule

            row.add(rowContent)
        }
        try {
            val insertRequestBuilder = InsertAllRequest.newBuilder(table)
            for (rowa in row) {

                insertRequestBuilder.addRow(rowa)
            }

            val response = client.insertAll(insertRequestBuilder.build())


            if (response.hasErrors()) {
                response.insertErrors.forEach { l, bigQueryErrors ->
                    println(l)
                    bigQueryErrors.forEach {
                        println(it.debugInfo)
                        println(it.location)
                        println(it.reason)
                        println(it.message)
                    }
                }
                response.insertErrors.forEach { (t, u) -> println("Response error for key: $t, value: $u") }
            }
            println("Insert operation performed successfully")
        } catch (e: com.google.cloud.bigquery.BigQueryException) {
            println("Insert operation not performed $e")
        }
    }
}





