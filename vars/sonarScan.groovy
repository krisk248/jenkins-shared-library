#!/usr/bin/env groovy

/**
 * Run SonarQube analysis - Optimized for both Java and TypeScript/Angular
 *
 * Features:
 * - 5 minute timeout protection (prevents stuck builds)
 * - Memory limits for scanner (prevents OOM)
 * - Optimized for TypeScript/Angular (skips node_modules properly)
 * - Auto-generates sonar-project.properties for frontend
 *
 * Usage for Java/Maven:
 *   sonarScan(
 *     projectKey: 'my-backend',
 *     projectName: 'My Backend',
 *     language: 'java'
 *   )
 *
 * Usage for TypeScript/Angular:
 *   sonarScan(
 *     projectKey: 'my-frontend',
 *     projectName: 'My Frontend',
 *     sources: 'src',
 *     language: 'ts'
 *   )
 */
def call(Map config = [:]) {
    // Required parameters
    if (!config.projectKey) {
        error "projectKey is required for SonarQube scan"
    }

    def projectKey = config.projectKey
    def projectName = config.projectName ?: config.projectKey
    def sources = config.sources ?: 'src'
    def language = config.language ?: 'java'
    def sonarServer = config.sonarServer ?: 'SonarQube'

    // Configurable limits
    def timeoutMinutes = config.timeout ?: 5
    def maxMemory = config.maxMemory ?: '1g'
    def nodeMaxMemory = config.nodeMaxMemory ?: 2048

    // Exclusions
    def exclusions = config.exclusions ?: '**/node_modules/**,**/dist/**,**/target/**,**/build/**,**/.angular/**'
    def testExclusions = config.testExclusions ?: '**/*.spec.ts,**/*.test.ts,**/test/**,**/tests/**'

    echo "Running SonarQube analysis..."
    echo "Project: ${projectName} (${projectKey})"
    echo "Language: ${language}"
    echo "Timeout: ${timeoutMinutes} minutes"

    // Wrap everything in timeout
    timeout(time: timeoutMinutes, unit: 'MINUTES') {
        withSonarQubeEnv(sonarServer) {
            if (language == 'java' || language == 'maven') {
                // Maven project - use mvn sonar:sonar (already optimized)
                echo "Using Maven SonarQube plugin..."
                sh """#!/bin/bash
                    export SDKMAN_DIR="\$HOME/.sdkman"
                    source "\$SDKMAN_DIR/bin/sdkman-init.sh"

                    mvn -B sonar:sonar \
                        -Dsonar.projectKey=${projectKey} \
                        -Dsonar.projectName="${projectName}"
                """
            } else if (language == 'ts' || language == 'typescript' || language == 'angular' || language == 'js' || language == 'javascript') {
                // TypeScript/Angular/JavaScript - use optimized sonar-scanner
                echo "Using optimized sonar-scanner for ${language}..."

                // Generate sonar-project.properties for better performance
                def sonarProps = """
sonar.projectKey=${projectKey}
sonar.projectName=${projectName}
sonar.sources=${sources}
sonar.sourceEncoding=UTF-8

# Exclusions - skip node_modules and build artifacts
sonar.exclusions=${exclusions}
sonar.test.exclusions=${testExclusions}
sonar.coverage.exclusions=${testExclusions}
sonar.cpd.exclusions=**/node_modules/**,**/*.min.js,**/*.bundle.js

# TypeScript/JavaScript specific
sonar.javascript.node.maxspace=${nodeMaxMemory}
sonar.typescript.node.maxspace=${nodeMaxMemory}

# Skip node_modules for copy-paste detection
sonar.cpd.js.minimumtokens=100
sonar.cpd.ts.minimumtokens=100
"""

                // Write properties file
                writeFile file: 'sonar-project.properties', text: sonarProps

                // Run scanner with memory limits
                sh """#!/bin/bash
                    echo "Generated sonar-project.properties:"
                    cat sonar-project.properties
                    echo ""
                    echo "Starting sonar-scanner with memory limit ${maxMemory}..."

                    # Set memory limits
                    export SONAR_SCANNER_OPTS="-Xmx${maxMemory}"
                    export NODE_OPTIONS="--max-old-space-size=${nodeMaxMemory}"

                    # Run scanner
                    sonar-scanner

                    # Cleanup
                    rm -f sonar-project.properties
                """
            } else {
                // Generic scanner for other languages
                echo "Using generic sonar-scanner..."
                sh """#!/bin/bash
                    export SONAR_SCANNER_OPTS="-Xmx${maxMemory}"

                    sonar-scanner \
                        -Dsonar.projectKey=${projectKey} \
                        -Dsonar.projectName="${projectName}" \
                        -Dsonar.sources=${sources} \
                        -Dsonar.exclusions=${exclusions}
                """
            }
        }
    }

    echo "SonarQube analysis completed"
    echo "View results at: http://192.168.1.136:9000/dashboard?id=${projectKey}"
}
