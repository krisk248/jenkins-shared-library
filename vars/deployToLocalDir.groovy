#!/usr/bin/env groovy

/**
 * Deploy artifacts to local directory
 *
 * Usage in Jenkinsfile:
 *   deployToLocalDir(
 *     source: 'target/*.war',
 *     destination: '/tts/outputttsbuild/ADXSIP/tts-uae-adx-sip-serverside'
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
    def createDir = config.createDir != false  // create directory by default

    echo "📦 Deploying to local directory..."
    echo "Source: ${source}"
    echo "Destination: ${destination}"

    sh """
        # Create destination directory if needed
        if [ "${createDir}" = "true" ]; then
            mkdir -p ${destination}
        fi

        # Copy files
        cp -rv ${source} ${destination}/

        # Verify deployment
        echo ""
        echo "✅ Deployed files:"
        ls -lh ${destination}/ | tail -10
    """

    echo "✅ Deployment completed successfully!"
}
