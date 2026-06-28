package com.chronie.homemoney.core.error

import retrofit2.Response

/**
 * Mock ErrorReportApi class
 * Used in development and testing environments to simulate error reporting functionality
 */
class MockErrorReportApi : ErrorReportApi {
    
    override suspend fun reportError(request: ErrorReportRequest): Response<Unit> {
        println("Mock error report: ${request.errorType} - ${request.message}")
        println("App Version: ${request.appVersion} (${request.appBuild})")
        println("Environment: ${request.environment}")
        println("Device Info: ${request.deviceInfo}")
        
        return Response.success(Unit)
    }
}
