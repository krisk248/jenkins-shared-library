#!/usr/bin/env groovy

/**
 * Cleanup old security reports from the report directory
 * Deletes reports older than specified days
 *
 * Usage in Jenkinsfile:
 *   cleanupOldReports(
 *     reportDir: '/tts/securityreports/ADXSIP',
 *     retentionDays: 30
 *   )
 */
def call(Map config = [:]) {
    def reportDir = config.reportDir ?: '/tts/securityreports'
    def retentionDays = config.retentionDays ?: 30

    echo "🧹 Cleaning up reports older than ${retentionDays} days..."
    echo "📁 Report directory: ${reportDir}"

    try {
        def result = sh(
            script: """#!/bin/bash
                if [ -d "${reportDir}" ]; then
                    # Find and delete directories older than retention days
                    echo "Finding reports older than ${retentionDays} days..."

                    # Count before cleanup
                    BEFORE=\$(find ${reportDir} -mindepth 1 -maxdepth 1 -type d | wc -l)

                    # Delete old directories (build number folders)
                    find ${reportDir} -mindepth 1 -maxdepth 1 -type d -mtime +${retentionDays} -exec rm -rf {} \\; 2>/dev/null || true

                    # Count after cleanup
                    AFTER=\$(find ${reportDir} -mindepth 1 -maxdepth 1 -type d | wc -l)

                    DELETED=\$((BEFORE - AFTER))
                    echo "✅ Cleanup complete: Deleted \${DELETED} old report directories"
                    echo "📊 Remaining: \${AFTER} report directories"
                else
                    echo "⚠️ Report directory does not exist: ${reportDir}"
                fi
            """,
            returnStdout: true
        ).trim()

        echo result
        return true
    } catch (Exception e) {
        echo "❌ Cleanup failed: ${e.message}"
        return false
    }
}
