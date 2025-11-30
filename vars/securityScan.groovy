#!/usr/bin/env groovy

/**
 * Run security scans using ttssecure module
 * Generates PDF, HTML, and JSON reports with professional formatting
 *
 * Usage in Jenkinsfile (with config file - RECOMMENDED):
 *   securityScan(
 *     configFile: 'adxsip-backend.yaml',
 *     buildNumber: env.BUILD_NUMBER
 *   )
 *
 * Or with inline config:
 *   securityScan(
 *     projectName: 'ADXSIP',
 *     component: 'backend',
 *     scanDir: '/tts/ttsbuild/ADXSIP/tts-uae-adx-sip-serverside'
 *   )
 */
def call(Map config = [:]) {
    // ttssecure location
    def ttssecureDir = config.ttssecureDir ?: "${env.HOME}/jenkins-automation/buildserversetup/ttssecure"
    def configsDir = "${ttssecureDir}/configs"

    // Build number for report naming
    def buildNumber = config.buildNumber ?: env.BUILD_NUMBER ?: '1'

    // Report output base directory
    def reportBaseDir = config.reportBaseDir ?: '/tts/securityreports'

    echo "🔒 Running Security Scan with ttssecure..."

    def scanResult = [:]

    // Determine configuration method
    if (config.configFile) {
        // Use YAML config file (RECOMMENDED)
        def configFile = config.configFile
        def configPath = configFile.startsWith('/') ? configFile : "${configsDir}/${configFile}"

        echo "Using config file: ${configPath}"
        echo "Build number: ${buildNumber}"

        def exitCode = sh(
            script: """#!/bin/bash
                export PATH="\$HOME/.local/bin:\$PATH"
                cd ${ttssecureDir}
                pipenv run python ttssecure.py --config ${configPath} --build-number ${buildNumber}
            """,
            returnStatus: true
        )

        scanResult.exitCode = exitCode
        scanResult.configFile = configPath

    } else if (config.projectName && config.scanDir) {
        // Inline configuration - for quick testing
        def projectName = config.projectName
        def component = config.component ?: 'main'
        def scanDir = config.scanDir
        def includePaths = config.includePaths ?: ['src']
        def excludePaths = config.excludePaths ?: ['node_modules', 'target', '.git']

        echo "Project: ${projectName} (${component})"
        echo "Scan directory: ${scanDir}"

        // Build include/exclude strings
        def includeStr = includePaths.collect { "    - \"${it}\"" }.join('\n')
        def excludeStr = excludePaths.collect { "    - \"${it}\"" }.join('\n')

        // Create temporary YAML config
        def tempConfig = "/tmp/ttssecure-${projectName}-${component}-${buildNumber}.yaml"

        writeFile file: tempConfig, text: """# Auto-generated config for ${projectName} ${component}
project:
  name: "${projectName}"
  component: "${component}"

source:
  path: "${scanDir}"
  include_paths:
${includeStr}
  exclude_paths:
${excludeStr}

metadata:
  developer_team: "Development Team"
  developer_contact: "dev@ttsme.com"
  devsecops_contact: "devsecops@ttsme.com"
  qa_url: "http://qa.ttsme.com/${projectName}"

output:
  base_dir: "${reportBaseDir}"

scanners:
  semgrep:
    enabled: true
    config: "auto"
    timeout: 600
  trivy:
    enabled: true
    severity: "CRITICAL,HIGH,MEDIUM,LOW"
    timeout: 600
  trufflehog:
    enabled: true
  spotbugs:
    enabled: ${component == 'backend' || component == 'java' ? 'true' : 'false'}
  owasp_dependency:
    enabled: ${component == 'backend' || component == 'java' ? 'true' : 'false'}
  eslint_security:
    enabled: ${component == 'frontend' || component == 'angular' ? 'true' : 'false'}

threshold:
  max_critical: 0
  max_high: 10
  max_medium: 50
  max_low: 100
  fail_on_secrets: true
"""

        def exitCode = sh(
            script: """#!/bin/bash
                export PATH="\$HOME/.local/bin:\$PATH"
                cd ${ttssecureDir}
                pipenv run python ttssecure.py --config ${tempConfig} --build-number ${buildNumber}
                rm -f ${tempConfig}
            """,
            returnStatus: true
        )

        scanResult.exitCode = exitCode
        scanResult.projectName = projectName
        scanResult.component = component

    } else {
        error "securityScan requires either 'configFile' or 'projectName' + 'scanDir' parameters"
    }

    // Determine scan status
    switch(scanResult.exitCode) {
        case 0:
            echo "✅ Security scan completed - All thresholds passed"
            scanResult.status = 'PASSED'
            break
        case 1:
            echo "⚠️ Security scan completed - Threshold violations detected"
            scanResult.status = 'THRESHOLD_EXCEEDED'
            break
        case 2:
            echo "⚠️ Security scan completed - Some scanners failed"
            scanResult.status = 'PARTIAL_FAILURE'
            break
        default:
            echo "❌ Security scan failed with exit code: ${scanResult.exitCode}"
            scanResult.status = 'FAILED'
    }

    echo "📊 Reports available at: ${reportBaseDir}/"

    // Return results for downstream use
    scanResult.reportDir = reportBaseDir
    scanResult.buildNumber = buildNumber

    return scanResult
}
