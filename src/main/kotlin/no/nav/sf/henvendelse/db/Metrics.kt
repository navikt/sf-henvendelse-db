package no.nav.sf.henvendelse.db

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter
import io.prometheus.client.Gauge
import io.prometheus.client.exporter.common.TextFormat
import io.prometheus.client.hotspot.DefaultExports
import mu.KotlinLogging
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status
import java.io.StringWriter

object Metrics {
    private val log = KotlinLogging.logger { }

    private val cRegistry: CollectorRegistry = CollectorRegistry.defaultRegistry

    val apiCalls: Counter = registerLabelCounter("api_calls", "ingress")

    val cacheSize: Gauge = registerGauge("cache_size")

    val cacheDelete = registerLabelCounter("cache_delete", "source")

    fun registerGauge(name: String): Gauge {
        return Gauge.build().name(name).help(name).register()
    }

    fun registerLabelCounter(name: String, vararg labels: String): Counter {
        return Counter.build().name(name).help(name).labelNames(*labels).register()
    }

    val metricsHandler: HttpHandler = {
        try {
            val metricsString = StringWriter().apply {
                TextFormat.write004(this, cRegistry.metricFamilySamples())
            }.toString()
            if (metricsString.isNotEmpty()) {
                Response(Status.OK).body(metricsString)
            } else {
                Response(Status.NO_CONTENT)
            }
        } catch (e: Exception) {
            log.error("Prometheus failed writing metrics - ${e.message}")
            Response(Status.INTERNAL_SERVER_ERROR).body("An unexpected error occurred")
        }
    }

    init {
        DefaultExports.initialize()
    }
}
