#!/usr/bin/env groovy

/**
 * Build Angular project
 * Supports building in permanent directory (with svn/git update) or workspace
 *
 * Usage in Jenkinsfile:
 *   buildAngular(
 *     buildDir: '/tts/ttsbuild/IPO-MIG/frontend/package/horizontal',
 *     nodeVersion: '20',
 *     buildCommand: 'ng build --aot --base-href="/TTS-CAP/" --configuration=production',
 *     useSvn: true
 *   )
 */
def call(Map config = [:]) {
    // Default values
    def buildDir = config.buildDir ?: ''  // If empty, build in current directory
    def nodeVersion = config.nodeVersion ?: '20'
    def buildCommand = config.buildCommand ?: 'npm run build'
    def installCommand = config.installCommand ?: 'npm ci'
    def distDir = config.distDir ?: 'dist'
    def useSvn = config.useSvn ?: false
    def updateCode = config.updateCode != false  // Update by default if buildDir specified

    echo "⚛️ Building Angular project with Node.js ${nodeVersion}"
    echo "Build command: ${buildCommand}"

    if (buildDir) {
        echo "Build directory: ${buildDir}"
        dir(buildDir) {
            // Update code if in permanent directory
            if (updateCode) {
                if (useSvn) {
                    echo "📥 Updating code from SVN..."
                    sh "svn update"
                } else {
                    echo "📥 Updating code from Git..."
                    sh "git pull"
                }
            }

            // Build
            sh """
                export NVM_DIR="/home/jenkins-agent/.nvm"
                [ -s "\$NVM_DIR/nvm.sh" ] && . "\$NVM_DIR/nvm.sh"

                # Use specified Node version
                nvm use ${nodeVersion}

                # Verify Node and npm versions
                node --version
                npm --version

                # Install dependencies (only if package.json changed or first time)
                ${installCommand}

                # Run build
                ${buildCommand}

                # Verify build output
                ls -la ${distDir}/
            """
        }
    } else {
        // Build in current workspace
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
    }

    echo "✅ Angular build completed successfully"
}
