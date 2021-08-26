pipeline {
	agent any
	stages {
		stage('Build') {
			steps {
				withGradle {
					sh 'gradle :desktop:jar :server:jar'
				}
				archiveArtifacts artifacts: 'desktop/build/distributions/*.jar', fingerprint: true
				archiveArtifacts artifacts: 'server/build/distributions/*.jar', fingerprint: true
			}
		}
	}
}