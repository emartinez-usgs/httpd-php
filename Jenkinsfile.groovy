#!/usr/bin/env grooy

node {
  def FROM_IMAGE = null
  def IMAGE_BASE = null
  def IMAGE_NAME = null
  def IMAGE_VERSION = null
  def NODE_VERSION = null
  def SCM_VARS = null

  IMAGE_BASE = "${GITLAB_INNERSOURCE_REGISTRY}/devops/images"
  FROM_IMAGE = "${IMAGE_BASE}/usgs/centos"
  IMAGE_NAME = "usgs/httpd-php"

  try {
    stage('Initialize') {
      cleanWs()

      SCM_VARS = checkout scm

      if (params.GIT_BRANCH != '') {
        sh "git checkout --detach ${params.GIT_BRANCH}"

        SCM_VARS.GIT_BRANCH = params.GIT_BRANCH
        SCM_VARS.GIT_COMMIT = sh(
          returnStdout: true,
          script: "git rev-parse HEAD"
        )
      }

      // Determine image tag to use
      if (SCM_VARS.GIT_BRANCH != 'origin/master') {
        IMAGE_VERSION = SCM_VARS.GIT_BRANCH.split('/').last().replace(' ', '_')
      } else {
        IMAGE_VERSION = 'latest'
      }
    }

    stage('Build') {
      ansiColor('xterm') {
        // Tag for internal registry
        sh """
          docker build \
            --build-arg FROM_IMAGE=${FROM_IMAGE} \
            -t ${IMAGE_BASE}/${IMAGE_NAME}:${IMAGE_VERSION} .
        """

        // Tag for default public Docker Hub
        sh """
          docker tag \
            ${IMAGE_BASE}/${IMAGE_NAME}:${IMAGE_VERSION} \
            ${IMAGE_NAME}:${IMAGE_VERSION}
        """
      }
    }

    stage('Scan') {
      echo 'TODO :: Implement security scanning.'
    }

    stage('Publish') {
      docker.withRegistry(
        "https://${GITLAB_INNERSOURCE_REGISTRY}",
        'innersource-hazdev-cicd'
      ) {
        ansiColor('xterm') {
          sh "docker push ${IMAGE_BASE}/${IMAGE_NAME}:${IMAGE_VERSION}"
        }
      }

      docker.withRegistry('', 'usgs-docker-hub-credentials') {
        ansiColor('xterm') {
          sh "docker push ${IMAGE_NAME}:${IMAGE_VERSION}"
        }
      }
    }
  } catch (err) {
    try {
      mail([
        to: 'gs-haz_dev_team_group@usgs.gov',
        from: 'noreply@jenkins',
        subject: "Jenkins Pipeline Failed: ${env.BUILD_TAG}",
        body: "Details: ${err}"
      ])
    } catch (inner) {
      echo "An error occured while sending email. '${inner}'"
    }

    currentBuild.result = 'FAILURE'
    throw err
  }
}
