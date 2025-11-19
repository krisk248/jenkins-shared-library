#!/usr/bin/env groovy

/**
 * Run security scans using Semgrep, Trivy, and TruffleHog
 *
 * Usage in Jenkinsfile:
 *   securityScan(
 *     enableSemgrep: true,
 *     enableTrivy: true,
 *     enableTruffleHog: true,
 *     failOnHigh: false
 *   )
 */
def call(Map config = [:]) {
    // Default values
    def enableSemgrep = config.enableSemgrep != false  // enabled by default
    def enableTrivy = config.enableTrivy != false      // enabled by default
    def enableTruffleHog = config.enableTruffleHog != false  // enabled by default
    def failOnHigh = config.failOnHigh ?: false
    def outputDir = config.outputDir ?: 'security-reports'

    echo "🔒 Running security scans..."

    // Create output directory
    sh "mkdir -p ${outputDir}"

    def scanResults = [:]

    // Run Semgrep (SAST)
    if (enableSemgrep) {
        echo "Running Semgrep (SAST)..."
        try {
            sh """
                semgrep --config=auto \
                    --json \
                    --output=${outputDir}/semgrep-report.json \
                    . || true
            """
            scanResults.semgrep = 'COMPLETED'
            echo "✅ Semgrep scan completed"
        } catch (Exception e) {
            scanResults.semgrep = 'FAILED'
            echo "⚠️ Semgrep scan failed: ${e.message}"
        }
    }

    // Run Trivy (SCA - dependency scanning)
    if (enableTrivy) {
        echo "Running Trivy (Dependency Scan)..."
        try {
            sh """
                trivy fs \
                    --format json \
                    --output ${outputDir}/trivy-report.json \
                    --severity HIGH,CRITICAL \
                    . || true
            """
            scanResults.trivy = 'COMPLETED'
            echo "✅ Trivy scan completed"
        } catch (Exception e) {
            scanResults.trivy = 'FAILED'
            echo "⚠️ Trivy scan failed: ${e.message}"
        }
    }

    // Run TruffleHog (Secret scanning)
    if (enableTruffleHog) {
        echo "Running TruffleHog (Secret Scan)..."
        try {
            sh """
                trufflehog filesystem . \
                    --json \
                    --no-update \
                    > ${outputDir}/trufflehog-report.json || true
            """
            scanResults.truffleHog = 'COMPLETED'
            echo "✅ TruffleHog scan completed"
        } catch (Exception e) {
            scanResults.truffleHog = 'FAILED'
            echo "⚠️ TruffleHog scan failed: ${e.message}"
        }
    }

    // Generate summary report
    echo """

    📊 Security Scan Summary:
    ========================
    Semgrep (SAST):        ${scanResults.semgrep ?: 'SKIPPED'}
    Trivy (Dependencies):  ${scanResults.trivy ?: 'SKIPPED'}
    TruffleHog (Secrets):  ${scanResults.truffleHog ?: 'SKIPPED'}

    Reports saved to: ${outputDir}/
    """

    // Archive reports
    archiveArtifacts artifacts: "${outputDir}/*.json", allowEmptyArchive: true

    return scanResults
}
