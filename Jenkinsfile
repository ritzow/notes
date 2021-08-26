pipeline {
	agent any
	stages {
		stage('Build') {
			steps {
				withGradle {
					sh 'gradle build'
				}
				archiveArtifacts artifacts: 'desktop/build/distributions/*', fingerprint: true
				archiveArtifacts artifacts: 'server/build/distributions/*', fingerprint: true
			}
		}
	}
}