#!/usr/bin/env groovy

/**
 * Deploy artifacts to network share
 *
 * Usage in Jenkinsfile:
 *   deployToNetworkShare(
 *     source: 'target/*.war',
 *     destination: '/192.168.1.136/tts-builds/myapp',
 *     credentialsId: 'network-share-credentials'
 *   )
 */
def call(Map config = [:]) {
    // Required parameters
    if (!config.source) {
        error "source is required for deployment"
    }
    if (!config.destination) {
        error "destination is required for deployment"
    }

    def source = config.source
    def destination = config.destination
    def credentialsId = config.credentialsId ?: 'network-share-credentials'
    def createDir = config.createDir != false  // create directory by default

    echo "🚀 Deploying to network share..."
    echo "Source: ${source}"
    echo "Destination: ${destination}"

    withCredentials([usernamePassword(
        credentialsId: credentialsId,
        usernameVariable: 'SHARE_USER',
        passwordVariable: 'SHARE_PASS'
    )]) {
        sh """
            # Create destination directory if needed
            if [ "${createDir}" = "true" ]; then
                mkdir -p ${destination}
            fi

            # Copy files
            cp -rv ${source} ${destination}/

            # Verify deployment
            ls -lh ${destination}/
        """
    }

    echo "✅ Deployment completed successfully"
}
