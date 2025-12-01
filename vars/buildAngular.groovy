#!/usr/bin/env groovy

/**
 * Build Angular project
 * Supports building in permanent directory (with svn/git update) or workspace
 *
 * Usage in Jenkinsfile:
 *   // Simple usage with baseHref
 *   buildAngular(
 *     buildDir: '/tts/ttsbuild/ADXSIP/tts-uae-adx-sip-clientside',
 *     nodeVersion: '20',
 *     baseHref: '/TTS-CAP/'
 *   )
 *
 *   // Custom build command
 *   buildAngular(
 *     buildDir: '/tts/ttsbuild/IPO-MIG/frontend',
 *     nodeVersion: '20',
 *     buildCommand: 'gulp build --prod'
 *   )
 */
def call(Map config = [:]) {
    // Default values
    def buildDir = config.buildDir ?: ''
    def nodeVersion = config.nodeVersion ?: '20'
    def installCommand = config.installCommand ?: 'npm ci'
    def distDir = config.distDir ?: 'dist'
    def useSvn = config.useSvn ?: false
    def updateCode = config.updateCode != false
    def gitBranch = config.gitBranch ?: ''

    // Build command - support baseHref shorthand or custom command
    def baseHref = config.baseHref ?: ''
    def buildCommand = config.buildCommand ?: ''

    // If no buildCommand but baseHref provided, construct Angular CLI command
    if (!buildCommand && baseHref) {
        buildCommand = "ng build --configuration=production --aot --output-hashing=all --source-map=false --base-href=\"${baseHref}\""
    } else if (!buildCommand) {
        buildCommand = 'npm run build'
    }

    // Summary log (clean output)
    echo "════════════════════════════════════════════════════════════"
    echo "⚛️ Angular Build"
    echo "────────────────────────────────────────────────────────────"
    echo "   Directory   : ${buildDir ?: 'workspace'}"
    echo "   Node.js     : ${nodeVersion}"
    if (baseHref) {
        echo "   Base Href   : ${baseHref}"
    }
    echo "   Command     : ${buildCommand}"
    echo "════════════════════════════════════════════════════════════"

    if (buildDir) {
        // Build in permanent directory using cd
        sh """#!/bin/bash
            set -e
            cd ${buildDir}

            # Update code if requested
            ${updateCode ? (useSvn ? 'svn update -q' : (gitBranch ? 'git checkout ' + gitBranch + ' -q && git pull -q' : 'git pull -q')) : ''}

            # Setup Node environment - use system Node if available, fallback to nvm
            if command -v node &> /dev/null; then
                echo "   Using system Node: \$(node --version)"
            elif [ -s "\$HOME/.nvm/nvm.sh" ]; then
                export NVM_DIR="\$HOME/.nvm"
                source "\$NVM_DIR/nvm.sh"
                nvm use ${nodeVersion} > /dev/null 2>&1 || nvm use default > /dev/null 2>&1 || true
                echo "   Using nvm Node: \$(node --version)"
            fi

            echo "   Node: \$(node --version) | npm: \$(npm --version)"

            # Install dependencies (quiet)
            ${installCommand} --silent 2>/dev/null || ${installCommand}

            # Run build
            ${buildCommand}

            # Verify output exists
            if [ -d "${distDir}" ]; then
                echo "   Output: ${distDir}/ (\$(du -sh ${distDir} | cut -f1))"
            fi
        """
    } else {
        // Build in current workspace
        sh """#!/bin/bash
            set -e

            # Setup Node environment - use system Node if available, fallback to nvm
            if command -v node &> /dev/null; then
                echo "   Using system Node: \$(node --version)"
            elif [ -s "\$HOME/.nvm/nvm.sh" ]; then
                export NVM_DIR="\$HOME/.nvm"
                source "\$NVM_DIR/nvm.sh"
                nvm use ${nodeVersion} > /dev/null 2>&1 || nvm use default > /dev/null 2>&1 || true
                echo "   Using nvm Node: \$(node --version)"
            fi

            echo "   Node: \$(node --version) | npm: \$(npm --version)"

            # Install dependencies (quiet)
            ${installCommand} --silent 2>/dev/null || ${installCommand}

            # Run build
            ${buildCommand}

            # Verify output exists
            if [ -d "${distDir}" ]; then
                echo "   Output: ${distDir}/ (\$(du -sh ${distDir} | cut -f1))"
            fi
        """
    }

    echo "────────────────────────────────────────────────────────────"
    echo "✅ Angular Build: COMPLETED"
    echo "════════════════════════════════════════════════════════════"
}
