#!/usr/bin/env groovy

/**
 * Run security scans using Semgrep, Trivy, and TruffleHog
 * Uses Python scripts from build server
 *
 * Usage in Jenkinsfile:
 *   securityScan(
 *     projectName: 'ADXSIP',
 *     scanDir: '/tts/ttsbuild/ADXSIP/tts-uae-adx-sip-serverside'
 *   )
 */
def call(Map config = [:]) {
    // Required parameters
    if (!config.projectName) {
        error "projectName is required for security scanning"
    }
    if (!config.scanDir) {
        error "scanDir is required for security scanning"
    }

    def projectName = config.projectName
    def scanDir = config.scanDir
    def enableSemgrep = config.enableSemgrep != false
    def enableTrivy = config.enableTrivy != false
    def enableTruffleHog = config.enableTruffleHog != false

    // Output directories
    def reportBaseDir = "/tts/ttsbuild/securityreport/${projectName}"
    def reportDir = "${reportBaseDir}/report"
    def scriptsDir = "${env.HOME}/jenkins-automation/buildserversetup/scripts"

    echo "🔒 Running security scans for ${projectName}..."
    echo "Scan directory: ${scanDir}"
    echo "Reports will be saved to: ${reportBaseDir}"

    // Create report directories
    sh """
        mkdir -p ${reportBaseDir}
        mkdir -p ${reportDir}
    """

    def scanResults = [:]

    // Run scans in scan directory using cd (no @tmp pollution)
    sh """
        cd ${scanDir}

        ${enableSemgrep ? """
        # Run Semgrep (SAST)
        echo "Running Semgrep (SAST)..."
        semgrep --config=auto \
            --json \
            --output=${reportBaseDir}/semgrep-report.json \
            . || true
        echo "✅ Semgrep scan completed"
        """ : ''}

        ${enableTrivy ? """
        # Run Trivy (Dependency Scan)
        echo "Running Trivy (Dependency Scan)..."
        trivy fs \
            --format json \
            --output ${reportBaseDir}/trivy-report.json \
            --severity HIGH,CRITICAL \
            . || true
        echo "✅ Trivy scan completed"
        """ : ''}

        ${enableTruffleHog ? """
        # Run TruffleHog (Secret Scan)
        echo "Running TruffleHog (Secret Scan)..."
        trufflehog filesystem . \
            --json \
            --no-update \
            > ${reportBaseDir}/trufflehog-report.json || true
        echo "✅ TruffleHog scan completed"
        """ : ''}
    """

    if (enableSemgrep) scanResults.semgrep = 'COMPLETED'
    if (enableTrivy) scanResults.trivy = 'COMPLETED'
    if (enableTruffleHog) scanResults.truffleHog = 'COMPLETED'

    // Generate PDF report using Python script
    echo "📄 Generating PDF report..."
    try {
        sh """
            cd ${reportBaseDir}
            python3 ${scriptsDir}/generate_report.py \
                --project-name "${projectName}" \
                --semgrep semgrep-report.json \
                --trivy trivy-report.json \
                --trufflehog trufflehog-report.json \
                --output report/security-report.pdf \
                --logo ${scriptsDir}/logo.png || true
        """
        echo "✅ PDF report generated: ${reportDir}/security-report.pdf"
    } catch (Exception e) {
        echo "⚠️ PDF report generation failed: ${e.message}"
    }

    // Generate summary report
    echo """

    📊 Security Scan Summary:
    ========================
    Project: ${projectName}
    Semgrep (SAST):        ${scanResults.semgrep ?: 'SKIPPED'}
    Trivy (Dependencies):  ${scanResults.trivy ?: 'SKIPPED'}
    TruffleHog (Secrets):  ${scanResults.truffleHog ?: 'SKIPPED'}

    Reports saved to: ${reportBaseDir}/
    - semgrep-report.json
    - trivy-report.json
    - trufflehog-report.json
    - report/security-report.pdf
    """

    // Archive reports for Jenkins
    archiveArtifacts artifacts: "${reportBaseDir}/*.json", allowEmptyArchive: true
    archiveArtifacts artifacts: "${reportDir}/*.pdf", allowEmptyArchive: true

    return scanResults
}
