pipeline {
	agent any
	stages {
		stage('Build') {
			steps {
				withGradle {
					sh 'gradle build'
				}
				archiveArtifacts artifacts: 'build/distributions/*', fingerprint: true
			}
		}
	}
}