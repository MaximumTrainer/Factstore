package com.factstore.adapter.inbound.web

import com.factstore.application.ComplianceMetricsService
import com.factstore.dto.ComplianceMetricsSummary
import com.factstore.dto.SecurityMetricsSummary
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/metrics")
class MetricsController(private val complianceMetricsService: ComplianceMetricsService) {

    @GetMapping("/compliance")
    fun getComplianceMetrics(): ResponseEntity<ComplianceMetricsSummary> =
        ResponseEntity.ok(complianceMetricsService.getComplianceMetrics())

    @GetMapping("/security")
    fun getSecurityMetrics(): ResponseEntity<SecurityMetricsSummary> =
        ResponseEntity.ok(complianceMetricsService.getSecurityMetrics())
}
