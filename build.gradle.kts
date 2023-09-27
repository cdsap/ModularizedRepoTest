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
    id("io.github.cdsap.talaiot") version "2.0.3-SNAPSHOT"
}

talaiot {
    publishers {
        jsonPublisher = true

        customPublishers(BiqQueryPublisher())
    }
}
data class ModuleDuration(val module: String, val duration: Long, val durationModule: Long)
class BiqQueryPublisher : Publisher {
    override fun publish(report: ExecutionReport) {
        val duration = report.durationMs
        val durations = mutableListOf<ModuleDuration>()
        report.tasks!!.groupBy { it.module }.forEach {

            durations.add(ModuleDuration(it.key, duration?.toLong()!!, it.value.sumOf { it.ms }))
        }

        val table =
            com.google.cloud.bigquery.TableId.of("mobile_build_metrics", "test2")
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
             rowContent["durationModule"] = it.durationModule
             row.add(rowContent)
         }
        try {
            val insertRequestBuilder = InsertAllRequest.newBuilder(table)
            for (rowa in row) {
                println(rowa)
                insertRequestBuilder.addRow(rowa)
            }

            val response = client.insertAll( insertRequestBuilder.build())


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

