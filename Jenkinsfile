// CI pipeline: Checkout -> Build & Test -> Package -> Docker Build -> Push
pipeline {
    agent any

    triggers {
        githubPush()            // GitHub webhook -> build on every push
        pollSCM('H/5 * * * *')  // fallback when GitHub can't reach Jenkins (e.g. Jenkins on a home network)
    }

    options {
        skipDefaultCheckout()   // checkout happens in its own stage so it shows in the stage view
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    environment {
        IMAGE = 'abhishek6122008/taskmanager'   // Docker Hub repository
        TAG   = "${env.BUILD_NUMBER}"           // every build gets a unique, traceable image tag
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build & Test') {
            steps {
                sh 'chmod +x mvnw && ./mvnw -B clean test'
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'   // test results graph in Jenkins
                }
            }
        }

        stage('Package') {
            steps {
                sh './mvnw -B package -DskipTests'
                archiveArtifacts artifacts: 'target/app.jar', fingerprint: true
            }
        }

        stage('Docker Build') {
            steps {
                sh 'docker build -t $IMAGE:$TAG -t $IMAGE:latest .'
            }
        }

        stage('Push') {
            steps {
                // Docker Hub login comes from the Jenkins credential "dockerhub", never from this file
                withCredentials([usernamePassword(credentialsId: 'dockerhub',
                                                  usernameVariable: 'DOCKERHUB_USER',
                                                  passwordVariable: 'DOCKERHUB_TOKEN')]) {
                    sh 'echo "$DOCKERHUB_TOKEN" | docker login -u "$DOCKERHUB_USER" --password-stdin'
                    sh 'docker push $IMAGE:$TAG && docker push $IMAGE:latest'
                }
            }
            post {
                always {
                    sh 'docker logout'
                }
            }
        }
    }

    post {
        success {
            echo "Pushed $IMAGE:$TAG"
        }
        always {
            // Drop this build's local images so the Jenkins host doesn't fill up
            sh 'docker rmi $IMAGE:$TAG $IMAGE:latest || true'
        }
    }
}
