#!/usr/bin/env groovy

/**
 * Build Angular project
 *
 * Usage in Jenkinsfile:
 *   buildAngular(
 *     nodeVersion: '20',
 *     buildCommand: 'npm run build:prod',
 *     installCommand: 'npm ci'
 *   )
 */
def call(Map config = [:]) {
    // Default values
    def nodeVersion = config.nodeVersion ?: '20'
    def buildCommand = config.buildCommand ?: 'npm run build'
    def installCommand = config.installCommand ?: 'npm ci'
    def distDir = config.distDir ?: 'dist'

    echo "⚛️ Building Angular project with Node.js ${nodeVersion}"
    echo "Install command: ${installCommand}"
    echo "Build command: ${buildCommand}"

    // Set Node version using NVM and build
    sh """
        export NVM_DIR="/home/jenkins-agent/.nvm"
        [ -s "\$NVM_DIR/nvm.sh" ] && . "\$NVM_DIR/nvm.sh"

        # Use specified Node version
        nvm use ${nodeVersion}

        # Verify Node and npm versions
        node --version
        npm --version

        # Install dependencies
        ${installCommand}

        # Run build
        ${buildCommand}

        # Verify build output
        ls -la ${distDir}/
    """

    echo "✅ Angular build completed successfully"
}
