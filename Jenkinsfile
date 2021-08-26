pipeline {
	agent any
	node {
		stage('Build') {
			steps {
				withGradle {
					sh 'gradle build'
				}
			}
		}
	}
}